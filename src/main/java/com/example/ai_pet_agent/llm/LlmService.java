
package com.example.ai_pet_agent.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import java.net.http.HttpTimeoutException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class LlmService {

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final HttpClient client;
    private final JsonMapper mapper;

    public LlmService() {
        this.apiKey = System.getenv("LLM_API_KEY");
        this.apiUrl = System.getenv("LLM_API_URL");
        this.model = System.getenv("LLM_MODEL");

        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.mapper = JsonMapper.builder().build();
    }

    // 统一的 DeepSeek 请求方法
    public JsonNode complete(Map<String, Object> requestFields) {

        if (apiKey == null || apiKey.isBlank()
                || apiUrl == null || apiUrl.isBlank()
                || model == null || model.isBlank()) {

            throw new LlmServiceException(
    "LLM_CONFIG_ERROR",
    HttpStatus.INTERNAL_SERVER_ERROR,
    "模型服务配置缺失，请联系管理员"
);
        }

        try {
            Map<String, Object> payload =
                    new LinkedHashMap<>(requestFields);

            payload.put("model", model);
            payload.putIfAbsent("stream", false);

            String requestJson =
                    mapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            requestJson,
                            StandardCharsets.UTF_8
                    ))
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );


int statusCode = response.statusCode();

if (statusCode == 401 || statusCode == 403) {
    throw new LlmServiceException(
            "LLM_AUTH_ERROR",
            HttpStatus.BAD_GATEWAY,
            "模型服务认证失败，请联系管理员检查配置"
    );
}

if (statusCode == 429) {
    throw new LlmServiceException(
            "LLM_RATE_LIMIT",
            HttpStatus.SERVICE_UNAVAILABLE,
            "模型服务请求繁忙，请稍后重试"
    );
}

if (statusCode < 200 || statusCode >= 300) {
    throw new LlmServiceException(
            "LLM_UPSTREAM_ERROR",
            HttpStatus.BAD_GATEWAY,
            "模型服务暂时不可用，请稍后重试"
    );
}


            return mapper.readTree(response.body());


} catch (HttpTimeoutException e) {

    throw new LlmServiceException(
            "LLM_TIMEOUT",
            HttpStatus.GATEWAY_TIMEOUT,
            "模型响应超时，请稍后再试",
            e
    );

} catch (InterruptedException e) {

    Thread.currentThread().interrupt();

    throw new LlmServiceException(
            "LLM_INTERRUPTED",
            HttpStatus.SERVICE_UNAVAILABLE,
            "模型请求已中断",
            e
    );

} catch (IOException e) {

    throw new LlmServiceException(
            "LLM_NETWORK_ERROR",
            HttpStatus.BAD_GATEWAY,
            "模型网络连接失败，请稍后再试",
            e
    );
}
    }


    // 保留普通聊天能力
    public String chat(String message) {

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("消息不能为空");
        }

        JsonNode root = complete(Map.of(
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", message
                        )
                ),
                "stream", false
        ));

        String reply = root
                .path("choices")
                .path(0)
                .path("message")
                .path("content")
                .asText();

        if (reply.isBlank()) {
            throw new IllegalStateException(
                    "DeepSeek 没有返回有效回复"
            );
        }

        return reply;
    }
}
