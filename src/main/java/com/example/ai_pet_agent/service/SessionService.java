
package com.example.ai_pet_agent.service;

import com.example.ai_pet_agent.dto.SessionHistoryResponse;
import com.example.ai_pet_agent.memory.ConversationMemory;

import org.springframework.stereotype.Service;

@Service
public class SessionService {

    private final ConversationMemory memory;

    public SessionService(ConversationMemory memory) {
        this.memory = memory;
    }

    // 查看指定会话的历史记录
    public SessionHistoryResponse getHistory(
            String sessionId) {

        var history = memory.getHistory(sessionId);

        return new SessionHistoryResponse(
                sessionId,
                history.size(),
                history
        );
    }

    // 清空指定会话的记忆
    public void clearHistory(String sessionId) {

        memory.clear(sessionId);
    }
}
