package com.hbeu.campusguide.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token settings are externalized so the demo secret is never reused in production.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long ttlMinutes,
        String issuer,
        String audience,
        String cookieName,
        boolean cookieSecure,
        String cookieSameSite
) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "campus-guide";
        }
        if (audience == null || audience.isBlank()) {
            audience = "campus-guide-admin";
        }
        if (cookieName == null || cookieName.isBlank()) {
            cookieName = "cg_admin_session";
        }
        if (cookieSameSite == null || cookieSameSite.isBlank()) {
            cookieSameSite = "Strict";
        }
    }
}
