package com.hbeu.campusguide.algorithm;

public record IntentResult(
        IntentType type,
        double confidence,
        Long pointId,
        Long startPointId,
        Long endPointId,
        String sceneAction,
        String tourKey,
        String normalizedText
) {
    public static IntentResult unknown(String normalizedText) {
        return new IntentResult(IntentType.UNKNOWN, 0.0, null, null, null, null, null, normalizedText);
    }
}
