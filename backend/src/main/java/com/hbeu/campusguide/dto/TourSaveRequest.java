package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

public record TourSaveRequest(
        @NotBlank String key,
        @NotBlank String name,
        String summary,
        String audience,
        Integer estimatedMinutes,
        String status,
        String verificationStatus,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Integer sortOrder,
        @NotEmpty List<TourStopInput> stops
) {
}
