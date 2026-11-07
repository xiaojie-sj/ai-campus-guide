package com.hbeu.campusguide.controller;

import com.hbeu.campusguide.common.ApiResponse;
import com.hbeu.campusguide.config.AdminContext;
import com.hbeu.campusguide.config.AdminPrincipal;
import com.hbeu.campusguide.config.JwtProperties;
import com.hbeu.campusguide.config.JwtService;
import com.hbeu.campusguide.dto.AdminSessionView;
import com.hbeu.campusguide.dto.LoginRequest;
import com.hbeu.campusguide.dto.LoginResponse;
import com.hbeu.campusguide.service.AdminAuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Tag(name = "01. 认证", description = "管理端登录、会话校验与退出")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AdminAuthService authService;
    private final JwtService jwtService;

    public AuthController(AdminAuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        LoginResponse response = authService.login(request, clientIp(servletRequest));
        servletResponse.addHeader(HttpHeaders.SET_COOKIE,
                sessionCookie(response.token(), jwtService.ttl()).toString());
        return ApiResponse.ok(response);
    }

    @GetMapping("/me")
    public ApiResponse<AdminSessionView> me() {
        AdminPrincipal principal = AdminContext.current();
        return ApiResponse.ok(new AdminSessionView(
                principal.userId(), principal.username(), principal.displayName(), principal.role()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        try {
            authService.logout(AdminContext.current());
        } finally {
            // Always clear the browser cookie, even if audit/DB cleanup fails.
            response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
        }
        return ApiResponse.ok("已退出登录", null);
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        JwtProperties properties = jwtService.properties();
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}

