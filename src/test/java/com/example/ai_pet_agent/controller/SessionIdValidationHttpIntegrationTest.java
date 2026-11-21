
package com.example.ai_pet_agent.controller;

import com.example.ai_pet_agent.llm.LlmService;
import com.example.ai_pet_agent.memory.ConversationMemory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class SessionIdValidationHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ConversationMemory memory;

    @MockitoBean
    private LlmService llmService;

    private final HttpClient client =
            HttpClient.newHttpClient();

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    // 测试一：GET 拒绝非法会话编号
    @Test
    void testRejectInvalidGetSessionId() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(url("/api/sessions/bad.id/history"))
                .timeout(Duration.ofSeconds(10))
                .header(
                         "Authorization",
                         "Bearer wangzai-test-token"
                )
                .GET()
                .build();

        var response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(400, response.statusCode());
    }

    // 测试二：DELETE 不得删除非法编号对应的数据
    @Test
    void testRejectInvalidDeleteSessionId() throws Exception {

        String sessionId = "bad.id";

        memory.saveTurn(
                sessionId,
                "测试消息",
                "测试回复"
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url("/api/sessions/bad.id/history"))
                    .timeout(Duration.ofSeconds(10))
                    .header(
                         "Authorization",
                         "Bearer wangzai-test-token"
                )
                    .DELETE()
                    .build();

            var response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(400, response.statusCode());

            // 被拒绝的删除请求不能修改已有数据
            assertEquals(
                    1,
                    memory.getHistory(sessionId).size()
            );

        } finally {
            memory.clear(sessionId);
        }
    }

    // 测试三：POST 拒绝空白 sessionId
    @Test
    void testRejectBlankChatSessionId() throws Exception {

        HttpResponse<String> response = postChat(" ");

        assertEquals(400, response.statusCode());
        verifyNoInteractions(llmService);
    }

    // 测试四：POST 拒绝超长 sessionId
    @Test
    void testRejectLongChatSessionId() throws Exception {

        String sessionId = "a".repeat(65);

        HttpResponse<String> response = postChat(sessionId);

        assertEquals(400, response.statusCode());
        verifyNoInteractions(llmService);
    }

    private HttpResponse<String> postChat(String sessionId)
            throws Exception {

        String json = mapper.writeValueAsString(
                Map.of(
                        "sessionId", sessionId,
                        "message", "旺仔你好"
                )
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(url("/api/chat"))
                .header("Content-Type", "application/json")
                .header(
                         "Authorization",
                         "Bearer wangzai-test-token"
                )
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private URI url(String path) {

        return URI.create(
                "http://127.0.0.1:" + port + path
        );
    }
}
