package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PointSaveRequest(
        @NotBlank String name,
        String aliases,
        @NotBlank String category,
        @NotBlank String description,
        @NotNull Double x,
        @NotNull Double z,
        Double buildingHeight,
        String colorHex,
        String modelKey,
        Long roadNodeId,
        String status,
        String verificationStatus,
        String sourceTitle,
        String sourceUrl,
        Integer sortOrder
) {
}
