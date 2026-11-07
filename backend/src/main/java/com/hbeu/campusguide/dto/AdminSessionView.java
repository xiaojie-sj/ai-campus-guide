package com.hbeu.campusguide.dto;

/**
 * Non-secret identity returned to the browser. The auth token itself lives in
 * an HttpOnly cookie and is never persisted by frontend JavaScript.
 */
public record AdminSessionView(
        long userId,
        String username,
        String displayName,
        String role
) {
}
