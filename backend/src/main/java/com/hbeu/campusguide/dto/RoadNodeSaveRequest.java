package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RoadNodeSaveRequest(
        @NotBlank String name,
        @NotNull Double x,
        @NotNull Double z,
        Boolean enabled
) {
}
