package com.hbeu.campusguide.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token settings are externalized so the demo secret is never reused in production.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long ttlMinutes) {
}
