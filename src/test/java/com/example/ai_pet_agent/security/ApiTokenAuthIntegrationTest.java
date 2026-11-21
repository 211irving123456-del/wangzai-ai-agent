
package com.example.ai_pet_agent.security;

import com.example.ai_pet_agent.memory.ConversationMemory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

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
class ApiTokenAuthIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ConversationMemory memory;

    private final HttpClient client =
            HttpClient.newHttpClient();

    private URI url(String path) {
        return URI.create(
                "http://127.0.0.1:" + port + path
        );
    }

    private HttpResponse<String> send(
            HttpRequest request) throws Exception {

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    // 测试一：没有 Token，不能查询历史
    @Test
    void testRejectMissingToken() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(url("/api/sessions/test-001/history"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = send(request);

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("UNAUTHORIZED"));
    }

    // 测试二：错误 Token，不能删除记录
    @Test
    void testRejectWrongTokenAndPreserveHistory()
            throws Exception {

        String sessionId = "auth-" + UUID.randomUUID();

        memory.saveTurn(
                sessionId,
                "我叫小明",
                "你好，小明！"
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url(
                            "/api/sessions/" + sessionId + "/history"
                    ))
                    .header(
                            "Authorization",
                            "Bearer wrong-token"
                    )
                    .timeout(Duration.ofSeconds(10))
                    .DELETE()
                    .build();

            HttpResponse<String> response = send(request);

            assertEquals(401, response.statusCode());

            // 认证失败不能删除记录
            assertEquals(
                    1,
                    memory.getHistory(sessionId).size()
            );

        } finally {
            memory.clear(sessionId);
        }
    }

    // 测试三：正确 Token，可以查询历史
    @Test
    void testAllowCorrectToken() throws Exception {

        String sessionId = "auth-" + UUID.randomUUID();

        memory.saveTurn(
                sessionId,
                "我喜欢芒果",
                "记住啦！"
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url(
                            "/api/sessions/" + sessionId + "/history"
                    ))
                    .header(
                            "Authorization",
                            "Bearer wangzai-test-token"
                    )
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = send(request);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("我喜欢芒果"));

        } finally {
            memory.clear(sessionId);
        }
    }

    // 测试四：聊天接口也必须要求 Token
    @Test
    void testRejectChatWithoutToken() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(url("/api/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"sessionId\":\"test-001\","
                        + "\"message\":\"旺仔你好\"}"
                ))
                .build();

        HttpResponse<String> response = send(request);

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("UNAUTHORIZED"));
    }
}
