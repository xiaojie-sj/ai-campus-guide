package com.hbeu.campusguide.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Non-secret application limits and storage paths.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Storage storage,
        Cors cors,
        Speech speech,
        Rag rag
) {
    public record Storage(String uploadDir, long maxFileBytes) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Speech(int maxSeconds) {
    }

    public record Rag(int chunkSize, int overlap, double minScore, int topK) {
    }
}
