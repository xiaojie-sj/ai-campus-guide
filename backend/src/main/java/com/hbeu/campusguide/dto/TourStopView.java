package com.hbeu.campusguide.dto;

public record TourStopView(
        Long id,
        Long pointId,
        String pointName,
        double x,
        double z,
        int sortOrder,
        String narration,
        String transitionType
) {
}
