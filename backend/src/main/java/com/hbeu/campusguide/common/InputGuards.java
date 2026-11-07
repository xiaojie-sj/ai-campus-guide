package com.hbeu.campusguide.common;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared validation for values that are later rendered as links or used as
 * storage keys. Never trust an administrator-supplied URL scheme.
 */
public final class InputGuards {
    private static final Pattern HTTP_URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR_HEX = Pattern.compile("^#[0-9a-fA-F]{3,8}$");
    private static final Pattern SAFE_KEY = Pattern.compile("^[A-Za-z0-9._-]{1,80}$");
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,39}$");

    private InputGuards() {
    }

    public static String httpUrl(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength || !HTTP_URL.matcher(trimmed).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + "必须是 http/https 地址");
        }
        return trimmed;
    }

    public static String text(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + "长度超过限制");
        }
        return trimmed;
    }

    public static String requiredText(String value, int maxLength, String field) {
        String trimmed = text(value, maxLength, field);
        if (trimmed == null || trimmed.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + "不能为空");
        }
        return trimmed;
    }

    public static String colorHex(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        if (!COLOR_HEX.matcher(trimmed).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "颜色格式不正确");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    public static String safeKey(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength || !SAFE_KEY.matcher(trimmed).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + "只能包含字母、数字、点、下划线和短横线");
        }
        return trimmed;
    }

    public static String code(String value, String fallback, String field) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + "格式不正确");
        }
        return normalized;
    }
    public static String oneOf(String value, Set<String> allowed, String fallback, String field) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + "取值不正确");
        }
        return normalized;
    }
}

