
package com.example.ai_pet_agent.controller;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString();

        // 返回给客户端，方便用户报告故障
        response.setHeader("X-Request-ID", requestId);

        // 保存到当前请求线程的日志上下文
        MDC.put("requestId", requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 必须清理，防止线程复用时混入其他请求
            MDC.remove("requestId");
        }
    }
}
