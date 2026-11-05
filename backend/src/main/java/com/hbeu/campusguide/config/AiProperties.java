package com.hbeu.campusguide.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All AI providers use OpenAI-compatible endpoints.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String chatModel,
        String transcriptionModel,
        String speechModel,
        String speechVoice,
        int timeoutSeconds
) {
    public boolean configured() {
        return enabled && baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }
}
