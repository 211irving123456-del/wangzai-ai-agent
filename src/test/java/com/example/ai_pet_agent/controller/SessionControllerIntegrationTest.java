
package com.example.ai_pet_agent.controller;

import com.example.ai_pet_agent.memory.ConversationMemory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class SessionControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ConversationMemory memory;

    private final HttpClient client =
            HttpClient.newHttpClient();

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    // 测试一：通过 GET 查看真实保存的会话历史
    @Test
    void testGetHistory() throws Exception {

        String sessionId =
                "lesson21-" + UUID.randomUUID();

        try {
            memory.saveTurn(
                    sessionId,
                    "我叫小明",
                    "你好，小明！"
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "http://127.0.0.1:" + port
                            + "/api/sessions/" + sessionId
                            + "/history"
                    ))
                    .header(
                         "Authorization",
                         "Bearer wangzai-test-token"
                )
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());

            JsonNode body = mapper.readTree(response.body());

            assertEquals(
                    sessionId,
                    body.path("sessionId").asText()
            );

            assertEquals(
                    1,
                    body.path("count").asInt()
            );

            assertEquals(
                    "我叫小明",
                    body.path("history")
                            .path(0)
                            .path("userMessage")
                            .asText()
            );

        } finally {
            memory.clear(sessionId);
        }
    }

    // 测试二：通过 DELETE 清空会话历史
    @Test
    void testDeleteHistory() throws Exception {

        String sessionId =
                "lesson21-" + UUID.randomUUID();

        try {
            memory.saveTurn(
                    sessionId,
                    "我喜欢芒果",
                    "记住啦！"
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "http://127.0.0.1:" + port
                            + "/api/sessions/" + sessionId
                            + "/history"
                    ))
                    .header(
                         "Authorization",
                         "Bearer wangzai-test-token"
                )
                    .timeout(Duration.ofSeconds(10))
                    .DELETE()
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(204, response.statusCode());

            assertTrue(
                    memory.getHistory(sessionId).isEmpty()
            );

        } finally {
            memory.clear(sessionId);
        }
    }
}
