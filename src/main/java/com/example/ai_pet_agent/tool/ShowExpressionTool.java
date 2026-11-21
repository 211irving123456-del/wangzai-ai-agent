package com.example.ai_pet_agent.tool;

import com.example.ai_pet_agent.device.CommandConfirmationClient;
import com.example.ai_pet_agent.device.DeviceStatusClient;
import com.example.ai_pet_agent.device.Esp32Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Implements the actual showExpression tool. AgentService no longer needs
 * to know how ESP32 commands or confirmation are performed.
 *
 * This class deliberately preserves the old tool-result fields and the
 * optional command_id / boot_id confirmation path.
 */
public final class ShowExpressionTool {
    private static final Logger log = LoggerFactory.getLogger(ShowExpressionTool.class);
    private static final Set<String> ALLOWED_ACTIONS = Set.of("happy", "sad", "sleep");

    private final Esp32Client esp32Client;
    private final JsonMapper mapper = JsonMapper.builder().build();


/**
 * Describe this tool to DeepSeek.
 */
public static Map<String, Object> schema() {

    return Map.of(
        "type", "function",

        "function", Map.of(
            "name", "showExpression",

            "description",
            "控制AI桌宠显示表情。" +
            "happy表示开心，sad表示难过，" +
            "sleep表示睡觉。",

            "parameters", Map.of(
                "type", "object",

                "properties", Map.of(
                    "action", Map.of(
                        "type", "string",
                        "enum", List.of(
                            "happy",
                            "sad",
                            "sleep"
                        )
                    )
                ),

                "required", List.of("action"),
                "additionalProperties", false
            )
        )
    );
}

    public ShowExpressionTool(Esp32Client esp32Client) {
        this.esp32Client = Objects.requireNonNull(esp32Client);
    }

    public String execute(String arguments) {
        String action;
        try {
            JsonNode args = mapper.readTree(arguments);
            if (!args.isObject() || args.size() != 1 || !args.path("action").isTextual()) {
                return errorResult("工具参数结构不正确");
            }
            action = args.path("action").asText();
        } catch (Exception e) {
            return errorResult("工具参数不是有效的JSON");
        }

        if (!ALLOWED_ACTIONS.contains(action)) {
            return errorResult("不支持的表情：" + action);
        }

        try {
            log.info("正在执行表情工具：action={}, requestId={}", action, MDC.get("requestId"));
            var result = esp32Client.showExpression(action);

            // Keep HTTP error details, including httpStatus and responseBody,
            // to preserve the existing contract and regression tests.
            if (!result.httpOk()) {
                String reason;
                if (result.httpStatus() == 401 || result.httpStatus() == 403) {
                    reason = "ESP32设备认证失败，请检查设备Token";
                } else if (result.httpStatus() == 503) {
                    reason = "ESP32设备繁忙，当前无法接收指令";
                } else {
                    reason = "ESP32设备请求失败";
                }
                log.warn("ESP32 HTTP error: status={}, action={}", result.httpStatus(), action);
                return mapper.writeValueAsString(Map.of(
                        "success", false,
                        "action", action,
                        "httpStatus", result.httpStatus(),
                        "responseBody", result.responseBody() == null ? "" : result.responseBody(),
                        "error", reason
                ));
            }

            // New Lesson 29/30 path: confirm the *specific* command using its
            // command_id and boot_id; no automatic replay on uncertain errors.
            if ("true".equalsIgnoreCase(System.getenv("ESP32_CONFIRM_BY_ID"))) {
                String confirmation = new CommandConfirmationClient().confirm(
                        action, result.httpOk(), result.httpStatus(), result.responseBody());
                log.info("设备指令确认：action={}, result={}", action, confirmation);
                return confirmation;
            }

            // Preserve the older mocked-device behavior unless opt-in is set.
            JsonNode deviceJson = mapper.readTree(result.responseBody());
            JsonNode successNode = deviceJson.path("success");
            boolean success = successNode.isBoolean()
                    ? successNode.asBoolean()
                    : "success".equals(deviceJson.path("status").asText());

            if (success && "true".equalsIgnoreCase(System.getenv("ESP32_VERIFY_AFTER_COMMAND"))) {
                return verifyExpressionState(action, result.httpStatus());
            }

            return mapper.writeValueAsString(Map.of(
                    "success", success,
                    "action", action,
                    "httpStatus", result.httpStatus(),
                    "responseBody", result.responseBody() == null ? "" : result.responseBody()
            ));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return errorResult("设备请求被中断：InterruptedException");
        } catch (Exception e) {
            String errorType = e.getClass().getSimpleName();
            log.warn("ESP32 communication failed: type={}, requestId={}",
                    errorType, MDC.get("requestId"));
            if (e instanceof HttpTimeoutException) {
                return errorResult("设备执行失败：HttpTimeoutException；ESP32通信超时，无法确认指令是否执行。");
            }
            if (e instanceof ConnectException) {
                return errorResult("设备执行失败：ConnectException；ESP32设备离线或网络不可达。");
            }
            if (e instanceof IOException) {
                return errorResult("设备执行失败：IOException；ESP32网络通信异常，无法确认执行结果。");
            }
            return errorResult("设备执行失败：" + errorType);
        }
    }

    private String verifyExpressionState(String action, int httpStatus) throws InterruptedException {
        String lastObserved = "unavailable";
        DeviceStatusClient statusClient;
        try {
            statusClient = new DeviceStatusClient();
        } catch (RuntimeException e) {
            log.warn("状态客户端初始化失败: {}", e.getClass().getSimpleName());
            return mapper.writeValueAsString(Map.of(
                    "success", false, "status", "unconfirmed", "action", action,
                    "httpStatus", httpStatus, "error", "无法初始化状态查询客户端"));
        }

        for (int attempt = 1; attempt <= 8; attempt++) {
            try {
                JsonNode status = mapper.readTree(statusClient.getDeviceStatus());
                if (status.path("online").asBoolean(false) && status.path("expression").isTextual()) {
                    lastObserved = status.path("expression").asText();
                    if (action.equals(lastObserved)) {
                        log.info("表情状态匹配：action={}, attempt={}, requestId={}",
                                action, attempt, MDC.get("requestId"));
                        return mapper.writeValueAsString(Map.of(
                                "success", true,
                                "status", "state_observed",
                                "action", action,
                                "observedExpression", lastObserved,
                                "httpStatus", httpStatus,
                                "note", "设备记录的当前表情与请求一致，尚非逐指令执行回执"
                        ));
                    }
                }
            } catch (Exception e) {
                log.warn("设备状态查询暂时失败：attempt={}, error={}",
                        attempt, e.getClass().getSimpleName());
            }
            if (attempt < 8) {
                Thread.sleep(200);
            }
        }

        return mapper.writeValueAsString(Map.of(
                "success", false,
                "status", "unconfirmed",
                "action", action,
                "lastObservedExpression", lastObserved,
                "httpStatus", httpStatus,
                "error", "设备已接受指令，但未能确认当前记录的表情与请求一致"
        ));
    }

    private String errorResult(String message) {
        return mapper.writeValueAsString(Map.of("success", false, "error", message));
    }
}
