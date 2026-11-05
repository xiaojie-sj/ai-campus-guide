package com.hbeu.campusguide.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hbeu.campusguide.common.BusinessException;
import com.hbeu.campusguide.common.ErrorCode;
import com.hbeu.campusguide.config.AdminPrincipal;
import com.hbeu.campusguide.config.JwtService;
import com.hbeu.campusguide.domain.AdminUser;
import com.hbeu.campusguide.dto.LoginRequest;
import com.hbeu.campusguide.dto.LoginResponse;
import com.hbeu.campusguide.mapper.AdminUserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthService {
    private final AdminUserMapper adminUserMapper;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminAuthService(AdminUserMapper adminUserMapper, JwtService jwtService, AuditService auditService) {
        this.adminUserMapper = adminUserMapper;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    public LoginResponse login(LoginRequest request) {
        AdminUser user = adminUserMapper.selectOne(
                Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getUsername, request.username()));
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.record(null, "LOGIN_FAILED", "ADMIN", request.username(), "用户名或密码错误");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        AdminPrincipal principal = new AdminPrincipal(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
        auditService.record(user.getId(), "LOGIN", "ADMIN", String.valueOf(user.getId()), "管理员登录成功");
        return new LoginResponse(
                jwtService.issue(principal),
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole()
        );
    }

    public BCryptPasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }
}
