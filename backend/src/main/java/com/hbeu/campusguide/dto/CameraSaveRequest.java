package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CameraSaveRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{1,80}$") String key,
        @NotBlank @Size(max = 120) String name,
        Long pointId,
        @NotNull Double x,
        @NotNull Double y,
        @NotNull Double z,
        @NotNull Double targetX,
        @NotNull Double targetY,
        @NotNull Double targetZ,
        Boolean publicVisible,
        @Size(max = 40) String status,
        Integer sortOrder
) {
}
