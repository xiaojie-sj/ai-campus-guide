package com.hbeu.campusguide.dto;

import java.util.List;

public record PointView(
        Long id,
        String name,
        List<String> aliases,
        String category,
        String description,
        double x,
        double z,
        double buildingHeight,
        String colorHex,
        String modelKey,
        Long roadNodeId,
        String status,
        String verificationStatus,
        String sourceTitle,
        String sourceUrl,
        int sortOrder
) {
}
