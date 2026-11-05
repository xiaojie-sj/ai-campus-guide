package com.hbeu.campusguide.dto;

public record SceneCommand(
        String type,
        Long pointId,
        String cameraKey,
        RoutePlan route,
        String action
) {
    public static SceneCommand none() {
        return new SceneCommand("NONE", null, null, null, null);
    }
}
