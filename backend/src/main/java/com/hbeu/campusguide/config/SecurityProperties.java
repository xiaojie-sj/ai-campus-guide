package com.hbeu.campusguide.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coarse security limits and deployment mode. Demo mode keeps local demo
 * accounts available; production operators must set APP_DEMO_MODE=false.
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        boolean demoMode,
        int loginMaxAttempts,
        int loginWindowMinutes,
        int loginLockMinutes,
        int publicRequestsPerMinute,
        int aiRequestsPerMinute
) {
    public SecurityProperties {
        if (loginMaxAttempts <= 0) {
            loginMaxAttempts = 5;
        }
        if (loginWindowMinutes <= 0) {
            loginWindowMinutes = 15;
        }
        if (loginLockMinutes <= 0) {
            loginLockMinutes = 15;
        }
        if (publicRequestsPerMinute <= 0) {
            publicRequestsPerMinute = 60;
        }
        if (aiRequestsPerMinute <= 0) {
            aiRequestsPerMinute = 10;
        }
    }
}
