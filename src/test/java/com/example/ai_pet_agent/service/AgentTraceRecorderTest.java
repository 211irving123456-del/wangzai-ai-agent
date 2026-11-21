package com.example.ai_pet_agent.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentTraceRecorderTest {

    @Test
    void shouldRecordTraceWithoutLeakingSecrets(
            @TempDir Path tempDir) throws Exception {

        Path file = tempDir.resolve("trace.jsonl");

        String oldEnabled =
                System.getProperty("wangzai.trace.enabled");

        String oldFile =
                System.getProperty("wangzai.trace.file");

        try {
            System.setProperty(
                    "wangzai.trace.enabled", "true"
            );
            System.setProperty(
                    "wangzai.trace.file", file.toString()
            );

            AgentTraceRecorder.start("trace-test-1");

            AgentTraceRecorder.model(
                    "trace-test-1", 1, 120
            );

            AgentTraceRecorder.tool(
                    "trace-test-1",
                    2,
                    "call-1",
                    "showExpression",
                    "{\"action\":\"sad\",\"token\":\"SECRET_XYZ\"}",
                    "{\"success\":true,\"status\":\"completed\","
                    + "\"token\":\"SECRET_XYZ\"}",
                    32
            );

            List<String> lines = Files.readAllLines(file);

            assertEquals(3, lines.size());

            JsonMapper mapper =
                    JsonMapper.builder().build();

            for (String line : lines) {
                assertTrue(
                        mapper.readTree(line).isObject()
                );
            }

            JsonNode tool = mapper.readTree(lines.get(2));

            assertEquals(
                    "tool",
                    tool.path("event").asText()
            );

            assertEquals(
                    "sad",
                    tool.path("arguments")
                            .path("action").asText()
            );

            assertEquals(
                    "completed",
                    tool.path("result")
                            .path("status").asText()
            );

            assertFalse(
                    Files.readString(file)
                            .contains("SECRET_XYZ")
            );

        } finally {
            restore("wangzai.trace.enabled", oldEnabled);
            restore("wangzai.trace.file", oldFile);
        }
    }

    private static void restore(String key, String oldValue) {
        if (oldValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, oldValue);
        }
    }
}
