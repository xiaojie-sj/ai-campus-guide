package com.hbeu.campusguide.config;

import com.hbeu.campusguide.common.BusinessException;
import com.hbeu.campusguide.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * Enforces authentication and optional role checks for admin endpoints.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {
    private final JwtService jwtService;

    public AdminAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        AdminPrincipal principal = jwtService.parse(authorization.substring(7));
        request.setAttribute(AdminContext.ATTRIBUTE, principal);

        if (handler instanceof HandlerMethod method) {
            RequireRole required = method.getMethodAnnotation(RequireRole.class);
            if (required == null) {
                required = method.getBeanType().getAnnotation(RequireRole.class);
            }
            if (required != null && Arrays.stream(required.value()).noneMatch(principal.role()::equals)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
        return true;
    }
}
