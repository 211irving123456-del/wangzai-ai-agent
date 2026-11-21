
package com.example.ai_pet_agent.dto;

public record ChatRequest(
        String message,
        String sessionId
) {
    // 兼容之前使用 new ChatRequest(message) 的代码
    public ChatRequest(String message) {
        this(message, null);
    }
}
