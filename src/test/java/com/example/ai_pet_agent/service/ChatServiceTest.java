
package com.example.ai_pet_agent.service;

import com.example.ai_pet_agent.dto.ChatResponse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class ChatServiceTest {

    @Test
    void testChatSuccess() {

        // 1. 创建模拟的 AgentService
        AgentService agentService = mock(AgentService.class);

        // 2. 指定模拟对象的返回结果
        when(agentService.chat("你好"))
                .thenReturn("你好，我是旺仔！");

        // 3. 创建被测试的 ChatService
        ChatService chatService = new ChatService(agentService);

        // 4. 执行业务方法
        ChatResponse response = chatService.chat("你好");

        // 5. 验证返回结果
        assertEquals("你好，我是旺仔！", response.reply());
        assertEquals("success", response.status());

        // 6. 验证 AgentService 确实被调用
        verify(agentService).chat("你好");
    }
}
