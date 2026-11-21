
package com.example.ai_pet_agent.controller;

import com.example.ai_pet_agent.dto.SessionHistoryResponse;
import com.example.ai_pet_agent.service.SessionService;
import com.example.ai_pet_agent.util.SessionIdValidator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    // 查询指定会话的历史记录
    @GetMapping("/{sessionId}/history")
    public ResponseEntity<SessionHistoryResponse> getHistory(
            @PathVariable("sessionId") String sessionId) {

        if (!SessionIdValidator.isValid(sessionId)) {
            return ResponseEntity.badRequest().build();
        }

        SessionHistoryResponse response =
                sessionService.getHistory(sessionId);

        return ResponseEntity.ok(response);
    }

    // 清空指定会话的历史记录
    @DeleteMapping("/{sessionId}/history")
    public ResponseEntity<Void> clearHistory(
            @PathVariable("sessionId") String sessionId) {

        if (!SessionIdValidator.isValid(sessionId)) {
            return ResponseEntity.badRequest().build();
        }

        sessionService.clearHistory(sessionId);

        return ResponseEntity.noContent().build();
    }
}
