package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotNull;

public record RoadEdgeSaveRequest(
        @NotNull Long fromNodeId,
        @NotNull Long toNodeId,
        Double distance,
        Boolean walkable,
        Boolean enabled
) {
}
