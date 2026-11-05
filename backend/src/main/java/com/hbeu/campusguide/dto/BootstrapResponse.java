package com.hbeu.campusguide.dto;

import java.util.List;

public record BootstrapResponse(
        List<PointView> points,
        List<CampusBuildingView> buildings,
        List<RoadNodeView> roadNodes,
        List<RoadEdgeView> roadEdges,
        List<CameraView> cameras,
        List<TourView> tours,
        SystemPublicConfig config,
        String dataVersion
) {
}
