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
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;

@Service
public class AdminAuthService {
    private final AdminUserMapper adminUserMapper;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final LoginAttemptService loginAttemptService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String dummyPasswordHash;

    public AdminAuthService(
            AdminUserMapper adminUserMapper,
            JwtService jwtService,
            AuditService auditService,
            LoginAttemptService loginAttemptService
    ) {
        this.adminUserMapper = adminUserMapper;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.loginAttemptService = loginAttemptService;
        // Always perform a BCrypt comparison so unknown users do not get a
        // measurably faster response than wrong-password users.
        this.dummyPasswordHash = passwordEncoder.encode("not-a-real-account-password");
    }

    public LoginResponse login(LoginRequest request, String clientIp) {
        String username = request.username().trim();
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            // BCrypt only consumes the first 72 bytes; reject longer inputs
            // instead of silently accepting a truncated password.
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码长度超过安全上限");
        }
        loginAttemptService.checkAllowed(username, clientIp);

        AdminUser user = adminUserMapper.selectOne(
                Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getUsername, username));
        String hash = user == null || user.getPasswordHash() == null
                ? dummyPasswordHash
                : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || !passwordMatches) {
            loginAttemptService.failure(username, clientIp);
            auditService.record(null, "LOGIN_FAILED", "ADMIN", username,
                    "登录失败，来源IP=" + clientIp);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        loginAttemptService.success(username, clientIp);
        long tokenVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
        AdminPrincipal principal = new AdminPrincipal(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), tokenVersion);
        auditService.record(user.getId(), "LOGIN", "ADMIN", String.valueOf(user.getId()),
                "管理员登录成功，来源IP=" + clientIp);
        return new LoginResponse(
                jwtService.issue(principal),
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole()
        );
    }

    @Transactional
    public void logout(AdminPrincipal principal) {
        AdminUser user = adminUserMapper.selectById(principal.userId());
        if (user != null) {
            long nextVersion = (user.getTokenVersion() == null ? 0L : user.getTokenVersion()) + 1;
            user.setTokenVersion(nextVersion);
            adminUserMapper.updateById(user);
        }
        auditService.record(principal.userId(), "LOGOUT", "ADMIN",
                String.valueOf(principal.userId()), "管理员退出登录，已撤销该账号的存量令牌");
    }

    public BCryptPasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }
}

