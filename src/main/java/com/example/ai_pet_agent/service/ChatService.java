
package com.example.ai_pet_agent.service;

import com.example.ai_pet_agent.dto.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final AgentService agentService;

    public ChatService(AgentService agentService) {
        this.agentService = agentService;
    }

    // 旧接口：普通聊天，保持兼容
    public ChatResponse chat(String message) {

        String reply = agentService.chat(message);

        return new ChatResponse(reply, "success");
    }

    // 新接口：支持 sessionId 的多轮聊天
    public ChatResponse chat(
            String sessionId,
            String message) {

        String reply = agentService.chat(
                sessionId,
                message
        );

        return new ChatResponse(reply, "success");
    }
}
