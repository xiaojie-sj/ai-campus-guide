package com.hbeu.campusguide.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbeu.campusguide.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limiting for public cost-bearing endpoints. This is an
 * in-process guard for a single instance; use an edge gateway or Redis when
 * running multiple replicas.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PublicRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_SECONDS = 60;
    private static final long MAX_JSON_BODY_BYTES = 64 * 1024;
    private static final long MAX_AUDIO_BODY_BYTES = 8 * 1024 * 1024;

    private record Window(long startedAt, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public PublicRateLimitFilter(SecurityProperties securityProperties, ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !("POST".equalsIgnoreCase(request.getMethod()) && path.startsWith("/api/public/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        long contentLength = request.getContentLengthLong();
        if ("/api/public/asr".equals(path) && contentLength > MAX_AUDIO_BODY_BYTES) {
            reject(response, ErrorCode.BAD_REQUEST, "录音文件过大");
            return;
        }
        if (!"/api/public/asr".equals(path) && contentLength > MAX_JSON_BODY_BYTES) {
            reject(response, ErrorCode.BAD_REQUEST, "请求体过大");
            return;
        }

        int limit = isAiEndpoint(path)
                ? securityProperties.aiRequestsPerMinute()
                : securityProperties.publicRequestsPerMinute();
        String key = clientIp(request) + "|" + bucket(path);
        long now = Instant.now().getEpochSecond();
        Window window = windows.compute(key, (ignored, previous) -> {
            if (previous == null || now - previous.startedAt() >= WINDOW_SECONDS) {
                return new Window(now, 1);
            }
            return new Window(previous.startedAt(), previous.count() + 1);
        });
        if (window.count() > limit) {
            response.setHeader("Retry-After", Long.toString(Math.max(1,
                    WINDOW_SECONDS - (now - window.startedAt()))));
            reject(response, ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
            return;
        }
        if (windows.size() > 50_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= WINDOW_SECONDS);
        }
        chain.doFilter(request, response);
    }

    private boolean isAiEndpoint(String path) {
        return "/api/public/asr".equals(path) || "/api/public/tts".equals(path);
    }

    private String bucket(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private void reject(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode, message));
    }
}
