
package com.example.ai_pet_agent.tool;

import com.example.ai_pet_agent.device.DeviceStatusClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.List;

public final class GetDeviceStatusTool {

    private static final Logger log =
            LoggerFactory.getLogger(GetDeviceStatusTool.class);

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    /**
     * 工具 Schema：告诉 DeepSeek 如何使用该工具
     */
    public static Map<String, Object> schema() {

        return Map.of(
            "type", "function",

            "function", Map.of(
                "name", "getDeviceStatus",

                "description",
                "查询真实旺仔ESP32设备的当前表情、" +
                "在线状态、运行时间和剩余内存。" +
                "用户询问设备情况时应调用此工具。",

                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(),
                    "required", List.of(),
                    "additionalProperties", false
                )
            )
        );
    }

    /**
     * 执行工具：查询真实 ESP32 状态
     */
    public String execute(String arguments) {

        // 第一步：检查工具参数
        try {

            JsonNode args = mapper.readTree(arguments);

            if (!args.isObject() || args.size() != 0) {

                return errorResult(
                    "getDeviceStatus不需要参数，请传入{}"
                );
            }

        } catch (Exception e) {

            return errorResult(
                "getDeviceStatus参数不是有效的JSON"
            );
        }

        // 第二步：查询 ESP32
        try {

            log.info(
                "正在查询设备状态：requestId={}",
                MDC.get("requestId")
            );

            String result =
                    new DeviceStatusClient().getDeviceStatus();

            JsonNode status = mapper.readTree(result);

            if (!status.isObject()) {

                return errorResult(
                    "设备状态响应不是JSON对象"
                );
            }

            // 保留之前的设备错误响应
            if (status.path("success").isBoolean()
                    && !status.path("success").asBoolean()) {

                return result;
            }

            // 检查设备状态字段
            if (!status.path("online").isBoolean()
                    || !status.path("expression").isTextual()
                    || !status.path("uptime_seconds").isNumber()
                    || !status.path("free_heap").isNumber()) {

                return errorResult(
                    "设备状态响应缺少必要字段"
                );
            }

            return result;

        } catch (Exception e) {

            log.warn(
                "设备状态查询失败：{}",
                e.toString()
            );

            return errorResult(
                "设备状态查询失败：" +
                e.getClass().getSimpleName()
            );
        }
    }

    /**
     * 统一错误结果
     */
    private String errorResult(String message) {

        return mapper.writeValueAsString(
            Map.of(
                "success", false,
                "error", message
            )
        );
    }
}
