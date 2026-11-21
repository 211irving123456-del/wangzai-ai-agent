
package com.example.ai_pet_agent.controller;

import com.example.ai_pet_agent.llm.LlmService;
import com.example.ai_pet_agent.llm.LlmServiceException;
import java.util.UUID;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ChatErrorHttpIntegrationTest {

    // Spring Boot 自动分配测试端口
    @LocalServerPort
    private int port;

    // 替换真实的 LlmService，避免调用 DeepSeek
    @MockitoBean
    private LlmService llmService;

    private final HttpClient client =
            HttpClient.newHttpClient();

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    // 测试一：模型超时 → HTTP 504
    @Test
    void testLlmTimeoutHttpResponse() throws Exception {

        when(llmService.complete(anyMap()))
                .thenThrow(new LlmServiceException(
                        "LLM_TIMEOUT",
                        HttpStatus.GATEWAY_TIMEOUT,
                        "模型响应超时，请稍后再试"
                ));

        HttpResponse<String> response = postChat();
String requestId = response.headers()
        .firstValue("X-Request-ID")
        .orElse(null);

assertNotNull(requestId);

assertDoesNotThrow(
        () -> UUID.fromString(requestId)
);
        assertEquals(504, response.statusCode());

        JsonNode body = mapper.readTree(response.body());

        assertEquals(
                "error",
                body.path("status").asText()
        );

        assertEquals(
                "LLM_TIMEOUT",
                body.path("code").asText()
        );

        assertEquals(
                "模型响应超时，请稍后再试",
                body.path("message").asText()
        );
    }

    // 测试二：模型认证失败 → HTTP 502
    @Test
    void testLlmAuthErrorHttpResponse() throws Exception {

        when(llmService.complete(anyMap()))
                .thenThrow(new LlmServiceException(
                        "LLM_AUTH_ERROR",
                        HttpStatus.BAD_GATEWAY,
                        "模型服务认证失败，请联系管理员检查配置"
                ));

        HttpResponse<String> response = postChat();
        String requestId = response.headers()
        .firstValue("X-Request-ID")
        .orElse(null);

assertNotNull(requestId);

assertDoesNotThrow(
        () -> UUID.fromString(requestId)
);

        assertEquals(502, response.statusCode());

        JsonNode body = mapper.readTree(response.body());

        assertEquals(
                "error",
                body.path("status").asText()
        );

        assertEquals(
                "LLM_AUTH_ERROR",
                body.path("code").asText()
        );
    }

    // 发送真正的 HTTP 请求给测试服务器
    private HttpResponse<String> postChat() throws Exception {

        String requestJson = mapper.writeValueAsString(
                Map.of("message", "旺仔，你好！")
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://127.0.0.1:" + port + "/api/chat"
                ))
                .header(
                        "Content-Type",
                        "application/json"
                )
                .header(
                         "Authorization",
                         "Bearer wangzai-test-token"
                )
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(
                        requestJson,
                        StandardCharsets.UTF_8
                ))
                .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8
                )
        );
    }
}
