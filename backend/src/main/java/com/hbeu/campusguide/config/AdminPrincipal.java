package com.hbeu.campusguide.config;

/**
 * Authenticated admin identity placed on the request after JWT validation and
 * database revalidation. Role is always taken from the database, never trusted
 * from a client-controlled token alone.
 */
public record AdminPrincipal(
        long userId,
        String username,
        String displayName,
        String role,
        long tokenVersion
) {
}
