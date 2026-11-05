package com.hbeu.campusguide.dto;

public record SystemPublicConfig(
        double avatarSpeechRate,
        double avatarVolume,
        double roamSpeed,
        double cameraFlightSeconds,
        long highlightMs,
        boolean autoSpeak,
        String mode,
        String notice
) {
}
