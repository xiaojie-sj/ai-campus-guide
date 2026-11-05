package com.hbeu.campusguide.dto;

import java.util.List;

public record RoutePlan(
        Long startPointId,
        String startName,
        Long endPointId,
        String endName,
        double distance,
        int estimatedSeconds,
        List<double[]> path,
        List<NavigationStep> steps,
        List<SourceRef> sources
) {
}
