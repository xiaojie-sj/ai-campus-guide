package com.hbeu.campusguide.common;

import org.slf4j.MDC;

import java.time.Instant;

/**
 * Unified API envelope. HTTP status still carries transport semantics while
 * code carries a stable business error identifier.
 */
public record ApiResponse<T>(int code, String message, T data, String traceId, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data, currentTraceId(), Instant.now());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(0, message, data, currentTraceId(), Instant.now());
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.code(), errorCode.message(), null, currentTraceId(), Instant.now());
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, null, currentTraceId(), Instant.now());
    }

    private static String currentTraceId() {
        String value = MDC.get("traceId");
        return value == null ? "" : value;
    }
}
