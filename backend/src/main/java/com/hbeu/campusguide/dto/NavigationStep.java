package com.hbeu.campusguide.dto;

public record NavigationStep(
        int index,
        Long fromNodeId,
        Long toNodeId,
        String instruction,
        double distance
) {
}
