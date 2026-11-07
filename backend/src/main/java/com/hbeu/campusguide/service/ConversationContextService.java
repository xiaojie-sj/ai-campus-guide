package com.hbeu.campusguide.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps short-lived conversational references. A production multi-instance
 * deployment can replace this with Redis without changing ChatService's API.
 */
@Service
public class ConversationContextService {
    private static final Duration TTL = Duration.ofMinutes(20);
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public Long referencePoint(String sessionId) {
        State state = states.get(sessionId);
        if (state == null) {
            return null;
        }
        if (state.updatedAt().plus(TTL).isBefore(Instant.now())) {
            states.remove(sessionId);
            return null;
        }
        return state.referencePointId();
    }

    public void rememberPoint(String sessionId, Long pointId) {
        if (sessionId == null || pointId == null) {
            return;
        }
        if (states.size() > 50_000) {
            purgeExpired();
        }
        states.put(sessionId, new State(pointId, Instant.now()));
    }

    private void purgeExpired() {
        Instant cutoff = Instant.now().minus(TTL);
        states.entrySet().removeIf(entry -> entry.getValue().updatedAt().isBefore(cutoff));
    }

    public void clear(String sessionId) {
        states.remove(sessionId);
    }

    record State(Long referencePointId, Instant updatedAt) {
    }
}

