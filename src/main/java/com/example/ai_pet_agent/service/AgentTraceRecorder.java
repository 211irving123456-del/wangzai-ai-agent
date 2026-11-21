package com.example.ai_pet_agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 执行轨迹记录器。
 *
 * 1. JSONL 持久化
 * 2. 线程安全追加
 * 3. 工具参数白名单
 * 4. 写入失败不影响 Agent 正常执行
 * 5. 默认关闭，通过环境变量启用
 */
public final class AgentTraceRecorder {

    private static final Logger log =
            LoggerFactory.getLogger(AgentTraceRecorder.class);

    private static final JsonMapper mapper =
            JsonMapper.builder().build();

    private static final Object FILE_LOCK = new Object();

    private AgentTraceRecorder() {}

    public static void start(String traceId) {
        emit(base(traceId, "start", 0));
    }

    public static void model(
            String traceId, int round, long durationMs) {

        Map<String, Object> event =
                base(traceId, "llm_decision", round);

        event.put("duration_ms", Math.max(0, durationMs));

        emit(event);
    }

    public static void tool(
            String traceId,
            int round,
            String callId,
            String toolName,
            String arguments,
            String toolResult,
            long durationMs) {

        if (!enabled()) {
            return;
        }

        Map<String, Object> event =
                base(traceId, "tool", round);

        event.put("tool_call_id", shortText(callId));
        event.put("tool_name", shortText(toolName));
        event.put("duration_ms", Math.max(0, durationMs));

        // 只记录允许展示的输入字段
        Map<String, Object> safeArgs =
                new LinkedHashMap<>();

        try {
            JsonNode args = mapper.readTree(arguments);

            if ("getCurrentWeather".equals(toolName)) {
                addText(safeArgs, args, "city");
            } else if ("showExpression".equals(toolName)) {
                addText(safeArgs, args, "action");
            }
        } catch (Exception ignored) {
            // 参数不可解析时，不记录原始字符串
        }

        event.put("arguments", safeArgs);

        // 只记录允许展示的结果字段
        Map<String, Object> safeResult =
                new LinkedHashMap<>();

        try {
            JsonNode result = mapper.readTree(toolResult);

            if (result.isObject()) {
                if (result.path("success").isBoolean()) {
                    safeResult.put(
                            "success",
                            result.path("success").asBoolean()
                    );
                }

                addText(safeResult, result, "status");
                addText(safeResult, result, "action");
                addText(safeResult, result, "condition");
                addText(safeResult, result, "expression");
                addText(safeResult, result, "error_code");

                if (result.path("command_id").isNumber()) {
                    safeResult.put(
                            "command_id",
                            result.path("command_id").asLong()
                    );
                }
            } else {
                safeResult.put("parsed", false);
            }

        } catch (Exception e) {
            safeResult.put("parsed", false);
        }

        event.put("result", safeResult);
        emit(event);
    }

    private static void addText(
            Map<String, Object> target,
            JsonNode source,
            String key) {

        JsonNode value = source.path(key);

        if (value.isTextual()) {
            target.put(key, shortText(value.asText()));
        }
    }

    private static String shortText(String value) {
        if (value == null) {
            return "";
        }

        return value.length() > 100
                ? value.substring(0, 100)
                : value;
    }

    private static Map<String, Object> base(
            String traceId,
            String type,
            int round) {

        Map<String, Object> event =
                new LinkedHashMap<>();

        event.put("trace_id", traceId);
        event.put("timestamp", Instant.now().toString());
        event.put("event", type);
        event.put("round", round);

        return event;
    }

    private static boolean enabled() {

        String value =
                System.getProperty("wangzai.trace.enabled");

        if (value == null) {
            value = System.getenv("WANGZAI_TRACE_ENABLED");
        }

        return Boolean.parseBoolean(value);
    }

    private static void emit(Map<String, Object> event) {

        if (!enabled()) {
            return;
        }

        try {
            String filename =
                    System.getProperty("wangzai.trace.file");

            if (filename == null || filename.isBlank()) {
                filename = System.getenv("WANGZAI_TRACE_FILE");
            }

            if (filename == null || filename.isBlank()) {
                filename = "data/agent-traces.jsonl";
            }

            Path path = Path.of(filename);

            synchronized (FILE_LOCK) {
                Files.createDirectories(
                        path.toAbsolutePath().getParent()
                );

                Files.writeString(
                        path,
                        mapper.writeValueAsString(event) + "\n",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            }

        } catch (Exception e) {
            log.warn(
                    "Agent Trace 写入失败：{}",
                    e.toString()
            );
        }
    }
}
