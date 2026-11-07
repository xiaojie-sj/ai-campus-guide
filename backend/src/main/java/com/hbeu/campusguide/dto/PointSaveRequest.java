package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PointSaveRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String aliases,
        @NotBlank @Size(max = 40) String category,
        @NotBlank @Size(max = 5000) String description,
        @NotNull @DecimalMin("-10000") @DecimalMax("10000") Double x,
        @NotNull @DecimalMin("-10000") @DecimalMax("10000") Double z,
        @DecimalMin("0") @DecimalMax("2000") Double buildingHeight,
        @Pattern(regexp = "^#[0-9a-fA-F]{3,8}$") String colorHex,
        @Pattern(regexp = "^[A-Za-z0-9._-]{0,80}$") String modelKey,
        Long roadNodeId,
        @Size(max = 40) String status,
        @Size(max = 40) String verificationStatus,
        @Size(max = 300) String sourceTitle,
        @Size(max = 500) String sourceUrl,
        Integer sortOrder
) {
}
