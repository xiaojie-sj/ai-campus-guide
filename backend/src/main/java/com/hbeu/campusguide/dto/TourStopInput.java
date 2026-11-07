package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TourStopInput(
        @NotNull Long pointId,
        @Size(max = 5000) String narration,
        @Size(max = 40) String transitionType
) {
}
