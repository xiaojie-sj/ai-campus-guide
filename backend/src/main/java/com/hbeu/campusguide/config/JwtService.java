package com.hbeu.campusguide.config;

import com.hbeu.campusguide.common.BusinessException;
import com.hbeu.campusguide.common.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * Signs and validates short-lived admin access tokens. Tokens are bound to a
 * database-side token version so logout can invalidate every token for a user.
 */
@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String DEMO_SECRET_PREFIX = "demo-only-";
    private static final Set<String> ALLOWED_ROLES = Set.of(
            "SYSTEM_ADMIN", "CONTENT_ADMIN", "REVIEWER", "DATA_ANALYST");

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties, SecurityProperties securityProperties) {
        this.properties = properties;
        if (properties.secret() == null || properties.secret().length() < 32) {
            throw new IllegalStateException("app.jwt.secret must contain at least 32 characters");
        }
        if (!securityProperties.demoMode() && properties.secret().startsWith(DEMO_SECRET_PREFIX)) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET must be replaced with a strong random value when APP_DEMO_MODE=false");
        }
        if (!securityProperties.demoMode() && !properties.cookieSecure()) {
            log.warn("APP_COOKIE_SECURE=false in non-demo mode; enable HTTPS and set APP_COOKIE_SECURE=true");
        }
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(AdminPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(ttl());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .audience().add(properties.audience()).and()
                .subject(Long.toString(principal.userId()))
                .claim("username", principal.username())
                .claim("displayName", principal.displayName())
                .claim("role", principal.role())
                .claim("tv", principal.tokenVersion())
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public AdminPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .requireAudience(properties.audience())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            long userId = Long.parseLong(claims.getSubject());
            String username = claims.get("username", String.class);
            String displayName = claims.get("displayName", String.class);
            String role = claims.get("role", String.class);
            Object tokenVersionClaim = claims.get("tv");
            long tokenVersion = tokenVersionClaim instanceof Number number ? number.longValue() : 0L;
            if (username == null || username.isBlank()
                    || role == null || !ALLOWED_ROLES.contains(role)
                    || claims.getExpiration() == null) {
                throw new IllegalArgumentException("token claims are incomplete");
            }
            return new AdminPrincipal(userId, username, displayName == null ? username : displayName,
                    role, tokenVersion);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    public Duration ttl() {
        return Duration.ofMinutes(Math.max(1, properties.ttlMinutes()));
    }

    public JwtProperties properties() {
        return properties;
    }
}
