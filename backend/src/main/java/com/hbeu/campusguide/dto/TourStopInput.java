package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotNull;

public record TourStopInput(
        @NotNull Long pointId,
        String narration,
        String transitionType
) {
}
