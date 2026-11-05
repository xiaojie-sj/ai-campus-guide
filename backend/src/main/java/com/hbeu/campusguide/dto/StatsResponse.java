package com.hbeu.campusguide.dto;

import java.util.List;
import java.util.Map;

public record StatsResponse(
        long totalConversations,
        long unanswerableCount,
        double averageLatencyMs,
        Map<String, Long> intentCounts,
        List<NamedCount> popularPoints,
        List<NamedCount> frequentQuestions
) {
    public record NamedCount(String name, long count) {
    }
}
