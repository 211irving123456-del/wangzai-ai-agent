
package com.example.ai_pet_agent.dto;

import com.example.ai_pet_agent.memory.ConversationMemory;

import java.util.List;

public record SessionHistoryResponse(
        String sessionId,
        int count,
        List<ConversationMemory.Turn> history
) {
}
