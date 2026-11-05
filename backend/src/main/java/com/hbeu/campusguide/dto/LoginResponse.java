package com.hbeu.campusguide.dto;

public record LoginResponse(
        String token,
        long userId,
        String username,
        String displayName,
        String role
) {
}
