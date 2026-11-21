
package com.example.ai_pet_agent.controller;

import com.example.ai_pet_agent.llm.LlmService;
import com.example.ai_pet_agent.memory.ConversationMemory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ChatMemoryHttpIntegrationTest {

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

    @BeforeEach
    void setUp() {
        memory.clear("http-memory-A");
        memory.clear("http-memory-B");
    }

    // 测试一：同一 sessionId 能携带历史消息
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void testSameSessionMemory() throws Exception {

        when(llmService.complete(anyMap()))
                .thenReturn(
                        modelReply("你好，小明！"),
                        modelReply("你叫小明。")
                );

        HttpResponse<String> first = postChat(
                "http-memory-A",
                "我叫小明"
        );

        HttpResponse<String> second = postChat(
                "http-memory-A",
                "你还记得我叫什么吗？"
        );

        assertEquals(200, first.statusCode());
        assertEquals(200, second.statusCode());

        JsonNode body = mapper.readTree(second.body());

        assertEquals(
                "你叫小明。",
                body.path("reply").asText()
        );

        ArgumentCaptor<Map> captor =
                ArgumentCaptor.forClass(Map.class);

        verify(llmService, times(2))
                .complete(captor.capture());

        Map secondRequest =
                captor.getAllValues().get(1);

        List<?> messages =
                (List<?>) secondRequest.get("messages");

        // system + 上轮user + 上轮assistant + 当前user
        assertEquals(4, messages.size());

        assertMessage(
                messages.get(1),
                "user",
                "我叫小明"
        );

        assertMessage(
                messages.get(2),
                "assistant",
                "你好，小明！"
        );

        assertMessage(
                messages.get(3),
                "user",
                "你还记得我叫什么吗？"
        );
    }

    // 测试二：不同 sessionId 不共享聊天历史
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void testDifferentSessionIsolation() throws Exception {

        when(llmService.complete(anyMap()))
                .thenReturn(
                        modelReply("你好，小明！"),
                        modelReply("我还不知道你的名字。")
                );

        HttpResponse<String> first = postChat(
                "http-memory-A",
                "我叫小明"
        );

        HttpResponse<String> second = postChat(
                "http-memory-B",
                "你知道我叫什么吗？"
        );

        assertEquals(200, first.statusCode());
        assertEquals(200, second.statusCode());

        ArgumentCaptor<Map> captor =
                ArgumentCaptor.forClass(Map.class);

        verify(llmService, times(2))
                .complete(captor.capture());

        Map secondRequest =
                captor.getAllValues().get(1);

        List<?> messages =
                (List<?>) secondRequest.get("messages");

        // 新会话只能有 system 和当前 user
        assertEquals(2, messages.size());

        assertMessage(
                messages.get(1),
                "user",
                "你知道我叫什么吗？"
        );

        assertEquals(
                1,
                memory.getHistory("http-memory-A").size()
        );

        assertEquals(
                1,
                memory.getHistory("http-memory-B").size()
        );
    }

    private void assertMessage(
            Object message,
            String role,
            String content) {

        Map<?, ?> map = (Map<?, ?>) message;

        assertEquals(role, map.get("role"));
        assertEquals(content, map.get("content"));
    }

    private JsonNode modelReply(String content)
            throws Exception {

        return mapper.readTree(
                mapper.writeValueAsString(
                        Map.of(
                                "choices",
                                List.of(
                                        Map.of(
                                                "message",
                                                Map.of(
                                                        "role", "assistant",
                                                        "content", content
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private HttpResponse<String> postChat(
            String sessionId,
            String message) throws Exception {

        String json = mapper.writeValueAsString(
                Map.of(
                        "sessionId", sessionId,
                        "message", message
                )
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://127.0.0.1:" + port + "/api/chat"
                ))
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
}
