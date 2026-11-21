
package com.example.ai_pet_agent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ApiTokenAuthFilter extends OncePerRequestFilter {

    private final String expectedToken;

    public ApiTokenAuthFilter(
            @Value("${wangzai.auth.token:}") String token) {

        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "缺少 WANGZAI_AUTH_TOKEN，拒绝启动服务"
            );
        }

        this.expectedToken = token;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String path = request.getServletPath();

        boolean protectedPath =
                "/api/chat".equals(path)
                || path.startsWith("/api/sessions/");

        // 不需要认证的接口继续执行
        if (!protectedPath) {
            chain.doFilter(request, response);
            return;
        }

        String authorization =
                request.getHeader("Authorization");

        String providedToken = null;

        if (authorization != null
                && authorization.startsWith("Bearer ")) {

            providedToken = authorization.substring(7);
        }

        if (!isValidToken(providedToken)) {

            response.setStatus(
                    HttpServletResponse.SC_UNAUTHORIZED
            );

            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");

            response.getWriter().write(
                    "{\"status\":\"error\","
                    + "\"code\":\"UNAUTHORIZED\","
                    + "\"message\":\"缺少或无效的访问令牌\"}"
            );

            return;
        }

        // 认证成功，进入后续 Spring Boot 业务流程
        chain.doFilter(request, response);
    }

    private boolean isValidToken(String providedToken) {

        if (providedToken == null
                || providedToken.isBlank()) {
            return false;
        }

        byte[] expected = expectedToken.getBytes(
                StandardCharsets.UTF_8
        );

        byte[] provided = providedToken.getBytes(
                StandardCharsets.UTF_8
        );

        return MessageDigest.isEqual(expected, provided);
    }
}
