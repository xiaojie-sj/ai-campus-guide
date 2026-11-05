package com.hbeu.campusguide.config;

/**
 * Authenticated admin identity placed on the request after JWT validation.
 */
public record AdminPrincipal(long userId, String username, String displayName, String role) {
}
