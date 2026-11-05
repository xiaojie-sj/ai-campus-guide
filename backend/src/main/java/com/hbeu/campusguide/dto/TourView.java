package com.hbeu.campusguide.dto;

import java.util.List;

public record TourView(
        Long id,
        String key,
        String name,
        String summary,
        String audience,
        int estimatedMinutes,
        String verificationStatus,
        List<TourStopView> stops
) {
}
