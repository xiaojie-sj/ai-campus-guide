package com.hbeu.campusguide.config;

import com.hbeu.campusguide.common.BusinessException;
import com.hbeu.campusguide.common.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Signs and validates short-lived admin access tokens.
 */
@Service
public class JwtService {
    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        if (properties.secret() == null || properties.secret().length() < 32) {
            throw new IllegalStateException("app.jwt.secret must contain at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(AdminPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(properties.ttlMinutes()));
        return Jwts.builder()
                .subject(Long.toString(principal.userId()))
                .claim("username", principal.username())
                .claim("displayName", principal.displayName())
                .claim("role", principal.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public AdminPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return new AdminPrincipal(
                    Long.parseLong(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("displayName", String.class),
                    claims.get("role", String.class)
            );
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
