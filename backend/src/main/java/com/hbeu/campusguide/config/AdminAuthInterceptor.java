package com.hbeu.campusguide.config;

import com.hbeu.campusguide.common.BusinessException;
import com.hbeu.campusguide.common.ErrorCode;
import com.hbeu.campusguide.domain.AdminUser;
import com.hbeu.campusguide.mapper.AdminUserMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Enforces authentication, database-side account state, role authorization and
 * cookie CSRF checks for admin endpoints. Endpoints without an explicit
 * {@link RequireRole} are denied by default.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {
    private final JwtService jwtService;
    private final AdminUserMapper adminUserMapper;
    private final AppProperties appProperties;

    public AdminAuthInterceptor(
            JwtService jwtService,
            AdminUserMapper adminUserMapper,
            AppProperties appProperties
    ) {
        this.jwtService = jwtService;
        this.adminUserMapper = adminUserMapper;
        this.appProperties = appProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        TokenSource tokenSource = resolveToken(request);
        if (tokenSource == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        AdminPrincipal claims = jwtService.parse(tokenSource.token());

        AdminUser user = adminUserMapper.selectById(claims.userId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        long currentVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
        if (claims.tokenVersion() != currentVersion) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // Role and profile come from the database, not from a token that may
        // have been issued before a role change or account suspension.
        AdminPrincipal principal = new AdminPrincipal(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), currentVersion);
        request.setAttribute(AdminContext.ATTRIBUTE, principal);

        if (tokenSource.fromCookie() && isUnsafeMethod(request.getMethod()) && !isTrustedOrigin(request)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请求来源未通过校验");
        }

        if (request.getRequestURI().startsWith("/api/admin/")) {
            RequireRole required = null;
            if (handler instanceof HandlerMethod method) {
                required = method.getMethodAnnotation(RequireRole.class);
                if (required == null) {
                    required = method.getBeanType().getAnnotation(RequireRole.class);
                }
            }
            if (required == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            if (Arrays.stream(required.value()).noneMatch(principal.role()::equals)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
        return true;
    }

    private TokenSource resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String value = authorization.substring(7).trim();
            if (!value.isEmpty()) {
                return new TokenSource(value, false);
            }
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String cookieName = jwtService.properties().cookieName();
            return Arrays.stream(cookies)
                    .filter(cookie -> cookieName.equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .map(value -> new TokenSource(value, true))
                    .orElse(null);
        }
        return null;
    }

    private boolean isUnsafeMethod(String method) {
        return !Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(method.toUpperCase(Locale.ROOT));
    }

    private boolean isTrustedOrigin(HttpServletRequest request) {
        if ("cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"))) {
            return false;
        }
        String origin = originOf(request.getHeader("Origin"));
        if (origin == null) {
            origin = originOf(request.getHeader("Referer"));
        }
        if (origin == null) {
            // Non-browser clients do not send Origin/Referer. They cannot ride
            // an ambient browser cookie, so this is not a CSRF vector.
            return true;
        }
        var origins = appProperties.cors() == null || appProperties.cors().allowedOrigins() == null
                ? java.util.List.<String>of()
                : appProperties.cors().allowedOrigins();
        Set<String> allowed = origins.stream()
                .map(this::normalizeOrigin)
                .collect(java.util.stream.Collectors.toSet());
        return allowed.contains(origin);
    }

    private String originOf(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return normalizeOrigin(uri.getScheme() + "://" + uri.getAuthority());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalizeOrigin(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private record TokenSource(String token, boolean fromCookie) {
    }
}

