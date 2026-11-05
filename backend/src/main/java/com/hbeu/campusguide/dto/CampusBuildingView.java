package com.hbeu.campusguide.dto;

public record CampusBuildingView(
        Long id,
        Long pointId,
        String name,
        String category,
        double height,
        String colorHex,
        double[][] footprint
) {
}
