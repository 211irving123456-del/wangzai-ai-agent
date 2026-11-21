
package com.example.ai_pet_agent.controller;

import com.example.ai_pet_agent.llm.LlmServiceException;

import java.util.Map;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LlmServiceException.class)
    public ResponseEntity<Map<String, String>> handleLlmException(
            LlmServiceException ex) {

        // 记录错误类型，不记录 API Key 或完整请求体
        String causeType = ex.getCause() == null
                ? "none"
                : ex.getCause().getClass().getSimpleName();

        log.warn(
                "LLM调用失败：requestId={}, code={}, httpStatus={}, cause={}",
                 MDC.get("requestId"),
                 ex.getCode(),
                 ex.getHttpStatus().value(),
                 causeType
        );

        Map<String, String> body = Map.of(
                "status", "error",
                "code", ex.getCode(),
                "message", ex.getMessage()
        );

        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(body);
    }
}
