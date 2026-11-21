
package com.example.ai_pet_agent.service;

import com.example.ai_pet_agent.device.Esp32Client;
import com.example.ai_pet_agent.llm.LlmService;
import com.example.ai_pet_agent.memory.ConversationMemory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class AgentMemoryTest {

    private LlmService llmService;
    private Esp32Client esp32Client;
    private ConversationMemory memory;
    private AgentService agentService;

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    @BeforeEach
    void setUp() {

        llmService = mock(LlmService.class);
        esp32Client = mock(Esp32Client.class);
        memory = new ConversationMemory();

        agentService = new AgentService(
                llmService,
                esp32Client,
                memory
        );
    }

    // 测试一：同一个会话能携带之前的聊天记录
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void testSameSessionHasHistory() throws Exception {

        when(llmService.complete(anyMap()))
                .thenReturn(
                        modelReply("你好，小明！"),
                        modelReply("你叫小明。")
                );

        agentService.chat("session-A", "我叫小明");

        String reply = agentService.chat(
                "session-A",
                "你还记得我叫什么吗？"
        );

        assertEquals("你叫小明。", reply);

        ArgumentCaptor<Map> captor =
                ArgumentCaptor.forClass(Map.class);

        verify(llmService, times(2))
                .complete(captor.capture());

        Map secondRequest =
                captor.getAllValues().get(1);

        List<?> messages =
                (List<?>) secondRequest.get("messages");

        // system + 历史user + 历史assistant + 当前user
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

        assertEquals(
                2,
                memory.getHistory("session-A").size()
        );

        verifyNoInteractions(esp32Client);
    }

    // 测试二：不同会话不能读取彼此的历史
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void testDifferentSessionsAreIsolated()
            throws Exception {

        when(llmService.complete(anyMap()))
                .thenReturn(
                        modelReply("你好，甲！"),
                        modelReply("你好，乙！")
                );

        agentService.chat("session-A", "我是甲");

        agentService.chat("session-B", "我是乙");

        ArgumentCaptor<Map> captor =
                ArgumentCaptor.forClass(Map.class);

        verify(llmService, times(2))
                .complete(captor.capture());

        Map secondRequest =
                captor.getAllValues().get(1);

        List<?> messages =
                (List<?>) secondRequest.get("messages");

        // 新会话只有 system 和当前 user
        assertEquals(2, messages.size());

        assertMessage(
                messages.get(1),
                "user",
                "我是乙"
        );

        assertEquals(
                1,
                memory.getHistory("session-A").size()
        );

        assertEquals(
                1,
                memory.getHistory("session-B").size()
        );

        verifyNoInteractions(esp32Client);
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
}
