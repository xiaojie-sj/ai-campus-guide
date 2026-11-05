package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CameraSaveRequest(
        @NotBlank String key,
        @NotBlank String name,
        Long pointId,
        @NotNull Double x,
        @NotNull Double y,
        @NotNull Double z,
        @NotNull Double targetX,
        @NotNull Double targetY,
        @NotNull Double targetZ,
        Boolean publicVisible,
        String status,
        Integer sortOrder
) {
}
