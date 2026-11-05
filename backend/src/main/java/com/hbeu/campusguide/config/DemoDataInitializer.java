package com.hbeu.campusguide.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hbeu.campusguide.domain.AdminUser;
import com.hbeu.campusguide.mapper.AdminUserMapper;
import com.hbeu.campusguide.service.AdminAuthService;
import com.hbeu.campusguide.service.KnowledgeIngestionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Creates local demo accounts and the bundled public-fact knowledge set. Account
 * passwords are hashed at runtime and must be changed before any public launch.
 */
@Component
public class DemoDataInitializer implements ApplicationRunner {
    private final AdminUserMapper adminUserMapper;
    private final AdminAuthService authService;
    private final KnowledgeIngestionService knowledgeService;

    public DemoDataInitializer(
            AdminUserMapper adminUserMapper,
            AdminAuthService authService,
            KnowledgeIngestionService knowledgeService
    ) {
        this.adminUserMapper = adminUserMapper;
        this.authService = authService;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        seedUser("admin", "系统管理员", "SYSTEM_ADMIN");
        seedUser("editor", "内容管理员", "CONTENT_ADMIN");
        seedUser("reviewer", "内容审核员", "REVIEWER");
        seedUser("analyst", "数据分析员", "DATA_ANALYST");
        knowledgeService.seedDemoKnowledge();
    }

    private void seedUser(String username, String displayName, String role) {
        long count = adminUserMapper.selectCount(
                Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getUsername, username));
        if (count > 0) {
            return;
        }
        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(authService.passwordEncoder().encode("Demo@123"));
        user.setRole(role);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        adminUserMapper.insert(user);
    }
}
