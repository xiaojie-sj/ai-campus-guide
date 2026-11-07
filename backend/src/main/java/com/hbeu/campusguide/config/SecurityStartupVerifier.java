package com.hbeu.campusguide.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hbeu.campusguide.domain.AdminUser;
import com.hbeu.campusguide.mapper.AdminUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Emits loud, actionable warnings when a non-demo deployment still carries
 * development defaults. Demo mode stays frictionless for local use.
 */
@Component
public class SecurityStartupVerifier implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SecurityStartupVerifier.class);
    private static final List<String> DEMO_USERNAMES = List.of("admin", "editor", "reviewer", "analyst");

    private final SecurityProperties securityProperties;
    private final AdminUserMapper adminUserMapper;
    private final String datasourcePassword;
    private final boolean docsEnabled;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SecurityStartupVerifier(
            SecurityProperties securityProperties,
            AdminUserMapper adminUserMapper,
            @Value("${spring.datasource.password:}") String datasourcePassword,
            @Value("${springdoc.api-docs.enabled:true}") boolean docsEnabled
    ) {
        this.securityProperties = securityProperties;
        this.adminUserMapper = adminUserMapper;
        this.datasourcePassword = datasourcePassword;
        this.docsEnabled = docsEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (securityProperties.demoMode()) {
            return;
        }
        if ("123456".equals(datasourcePassword)) {
            log.error("SECURITY: default PostgreSQL password is still in use; set POSTGRES_PASSWORD.");
        }
        if (docsEnabled) {
            log.error("SECURITY: API documentation is enabled in non-demo mode; set KNIFE4J_ENABLED=false.");
        }
        adminUserMapper.selectList(Wrappers.<AdminUser>lambdaQuery()
                        .in(AdminUser::getUsername, DEMO_USERNAMES))
                .stream()
                .filter(user -> user.getPasswordHash() != null
                        && passwordEncoder.matches("Demo@123", user.getPasswordHash()))
                .forEach(user -> log.error(
                        "SECURITY: demo account '{}' still uses the published demo password; rotate it before launch.",
                        user.getUsername()));
    }
}
