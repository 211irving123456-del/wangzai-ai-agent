
package com.example.ai_pet_agent.controller;

import com.example.ai_pet_agent.dto.ChatRequest;
import com.example.ai_pet_agent.dto.ChatResponse;
import com.example.ai_pet_agent.service.ChatService;
import com.example.ai_pet_agent.util.SessionIdValidator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/api/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request) {

        // 1. 检查消息内容
        if (request == null
                || request.message() == null
                || request.message().isBlank()) {

            return ResponseEntity.badRequest().body(
                    new ChatResponse("消息不能为空", "error")
            );
        }

        // 2. 获取会话编号
        String sessionId = request.sessionId();

        // 3. 不提供 sessionId：沿用旧版无记忆聊天
        if (sessionId == null) {

            ChatResponse response =
                    chatService.chat(request.message());

            return ResponseEntity.ok(response);
        }

        // 4. 提供了 sessionId：必须满足统一校验规则
        if (!SessionIdValidator.isValid(sessionId)) {

            return ResponseEntity.badRequest().body(
                    new ChatResponse(
                            "sessionId 格式不正确，"
                                    + "请使用1至64位字母、数字、下划线或连接符",
                            "error"
                    )
            );
        }

        // 5. 合法 sessionId：执行多轮聊天
        ChatResponse response = chatService.chat(
                sessionId,
                request.message()
        );

        return ResponseEntity.ok(response);
    }
}
