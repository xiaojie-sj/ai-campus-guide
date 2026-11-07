package com.hbeu.campusguide.service;

import com.hbeu.campusguide.common.BusinessException;
import com.hbeu.campusguide.common.ErrorCode;
import com.hbeu.campusguide.config.SecurityProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-process brute-force guard for the admin login endpoint. Keys are
 * scoped by account and client address so one bad account cannot exhaust the
 * whole service. For multi-instance deployments back this with Redis.
 */
@Service
public class LoginAttemptService {
    private record Attempt(int failures, Instant windowStart, Instant lockedUntil) {
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final SecurityProperties properties;

    public LoginAttemptService(SecurityProperties properties) {
        this.properties = properties;
    }

    public void checkAllowed(String username, String clientIp) {
        checkKey(userKey(username, clientIp), properties.loginMaxAttempts());
        checkKey(ipKey(clientIp), ipThreshold());
    }

    public void failure(String username, String clientIp) {
        recordFailure(userKey(username, clientIp), properties.loginMaxAttempts());
        // A shared NAT address should need more failures before the whole
        // network is locked out, while still stopping password spraying.
        recordFailure(ipKey(clientIp), ipThreshold());
        if (attempts.size() > 10_000) {
            purgeExpired();
        }
    }

    public void success(String username, String clientIp) {
        attempts.remove(userKey(username, clientIp));
    }

    private int ipThreshold() {
        return Math.max(10, properties.loginMaxAttempts() * 4);
    }

    private void checkKey(String key, int maxAttempts) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return;
        }
        Instant now = Instant.now();
        if (attempt.lockedUntil() != null && attempt.lockedUntil().isAfter(now)) {
            long seconds = Math.max(1, Duration.between(now, attempt.lockedUntil()).toSeconds());
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "登录尝试过多，请 " + ((seconds + 59) / 60) + " 分钟后再试");
        }
        if (attempt.lockedUntil() != null && !attempt.lockedUntil().isAfter(now)) {
            attempts.remove(key);
        }
    }

    private void recordFailure(String key, int maxAttempts) {
        Instant now = Instant.now();
        attempts.compute(key, (ignored, previous) -> {
            Instant windowStart = previous == null
                    ? now
                    : previous.windowStart().plus(Duration.ofMinutes(properties.loginWindowMinutes()));
            if (previous == null || now.isAfter(windowStart)) {
                return new Attempt(1, now, null);
            }
            int failures = previous.failures() + 1;
            Instant lockedUntil = previous.lockedUntil();
            if (lockedUntil != null && lockedUntil.isAfter(now)) {
                return previous;
            }
            if (failures >= maxAttempts) {
                lockedUntil = now.plus(Duration.ofMinutes(properties.loginLockMinutes()));
            }
            return new Attempt(failures, previous.windowStart(), lockedUntil);
        });
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        attempts.entrySet().removeIf(entry -> {
            Attempt attempt = entry.getValue();
            if (attempt.lockedUntil() != null) {
                return attempt.lockedUntil().isBefore(now);
            }
            return attempt.windowStart()
                    .plus(Duration.ofMinutes(properties.loginWindowMinutes()))
                    .isBefore(now);
        });
    }

    private String userKey(String username, String clientIp) {
        return "u:" + username + "|" + clientIp;
    }

    private String ipKey(String clientIp) {
        return "ip:" + clientIp;
    }

}


