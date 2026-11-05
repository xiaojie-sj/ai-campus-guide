package com.hbeu.campusguide.controller;

import com.hbeu.campusguide.common.ApiResponse;
import com.hbeu.campusguide.dto.LoginRequest;
import com.hbeu.campusguide.dto.LoginResponse;
import com.hbeu.campusguide.service.AdminAuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01. 认证", description = "管理端登录并获取 JWT")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AdminAuthService authService;

    public AuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }
}
