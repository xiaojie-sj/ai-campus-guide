package com.hbeu.campusguide.dto;

public record CameraView(
        Long id,
        String key,
        String name,
        Long pointId,
        double x,
        double y,
        double z,
        double targetX,
        double targetY,
        double targetZ,
        int sortOrder
) {
}
