package com.example.ai_pet_agent.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class AgentTraceController {

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    @GetMapping("/api/agent/traces/latest")
    public ResponseEntity<?> latest(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization) {

        // 独立鉴权，防止新接口绕过已有过滤器
        if (!authorized(authorization)) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "unauthorized"));
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

            if (!Files.isRegularFile(path)) {
                return ResponseEntity.ok(Map.of(
                        "traceId", "",
                        "events", List.of()
                ));
            }

            // 只在内存中保留最后 500 个有效事件
            ArrayDeque<JsonNode> recent = new ArrayDeque<>();

            try (BufferedReader reader = Files.newBufferedReader(
                    path, StandardCharsets.UTF_8)) {

                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.length() > 16384) {
                        continue;
                    }

                    try {
                        JsonNode event = mapper.readTree(line);

                        if (!event.isObject()
                                || !event.path("trace_id").isTextual()
                                || !event.path("event").isTextual()) {
                            continue;
                        }

                        recent.addLast(event);

                        if (recent.size() > 500) {
                            recent.removeFirst();
                        }

                    } catch (Exception ignored) {
                        // 忽略不完整或损坏的单行 JSON
                    }
                }
            }

            if (recent.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "traceId", "",
                        "events", List.of()
                ));
            }

            String latestId = recent.getLast()
                    .path("trace_id").asText();

            List<JsonNode> events = new ArrayList<>();

            for (JsonNode event : recent) {
                if (latestId.equals(
                        event.path("trace_id").asText())) {
                    events.add(event);
                }
            }

            return ResponseEntity.ok(Map.of(
                    "traceId", latestId,
                    "events", events,
                    "count", events.size()
            ));

        } catch (Exception e) {
            // 不将文件路径或内部异常暴露给调用方
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "trace_read_failed"
                    ));
        }
    }

    private boolean authorized(String authorization) {

        String expected =
                System.getenv("WANGZAI_AUTH_TOKEN");

        if (expected == null || expected.isBlank()
                || authorization == null
                || !authorization.startsWith("Bearer ")) {
            return false;
        }

        String actual = authorization.substring(7).trim();

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
