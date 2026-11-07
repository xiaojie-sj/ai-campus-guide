package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoadNodeSaveRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull Double x,
        @NotNull Double z,
        Boolean enabled
) {
}
