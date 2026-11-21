
package com.example.ai_pet_agent.controller;

import com.example.ai_pet_agent.llm.LlmServiceException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    // 测试一：模型超时应该返回 HTTP 504
    @Test
    void testTimeoutResponse() {

        LlmServiceException exception =
                new LlmServiceException(
                        "LLM_TIMEOUT",
                        HttpStatus.GATEWAY_TIMEOUT,
                        "模型响应超时，请稍后再试"
                );

        ResponseEntity<Map<String, String>> response =
                handler.handleLlmException(exception);

        assertEquals(
                HttpStatus.GATEWAY_TIMEOUT,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "error",
                response.getBody().get("status")
        );

        assertEquals(
                "LLM_TIMEOUT",
                response.getBody().get("code")
        );
    }

    // 测试二：上游认证失败应该返回 HTTP 502
    @Test
    void testAuthenticationResponse() {

        LlmServiceException exception =
                new LlmServiceException(
                        "LLM_AUTH_ERROR",
                        HttpStatus.BAD_GATEWAY,
                        "模型服务认证失败，请联系管理员检查配置"
                );

        ResponseEntity<Map<String, String>> response =
                handler.handleLlmException(exception);

        assertEquals(
                HttpStatus.BAD_GATEWAY,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "LLM_AUTH_ERROR",
                response.getBody().get("code")
        );
    }
}