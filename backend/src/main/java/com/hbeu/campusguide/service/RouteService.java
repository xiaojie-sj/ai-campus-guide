package com.hbeu.campusguide.service;

import com.hbeu.campusguide.algorithm.PathPlanner;
import com.hbeu.campusguide.common.BusinessException;
import com.hbeu.campusguide.common.ErrorCode;
import com.hbeu.campusguide.domain.CampusPoint;
import com.hbeu.campusguide.domain.RoadNode;
import com.hbeu.campusguide.dto.NavigationStep;
import com.hbeu.campusguide.dto.RoutePlan;
import com.hbeu.campusguide.dto.SourceRef;
import com.hbeu.campusguide.config.DatabaseCapabilities;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a point-to-point navigation request into a road-following route.
 */
@Service
public class RouteService {
    private final CatalogService catalogService;
    private final PathPlanner pathPlanner;
    private final ConfigService configService;
    private final JdbcTemplate jdbcTemplate;
    private final boolean pgRoutingEnabled;

    public RouteService(
            CatalogService catalogService,
            PathPlanner pathPlanner,
            ConfigService configService,
            JdbcTemplate jdbcTemplate,
            DatabaseCapabilities databaseCapabilities
    ) {
        this.catalogService = catalogService;
        this.pathPlanner = pathPlanner;
        this.configService = configService;
        this.jdbcTemplate = jdbcTemplate;
        this.pgRoutingEnabled = databaseCapabilities.pgRouting();
    }

    public RoutePlan plan(Long startPointId, Long endPointId) {
        CampusPoint end = catalogService.requirePublishedPoint(endPointId);
        CampusPoint start = startPointId == null
                ? catalogService.publishedPoints().stream()
                .filter(point -> "GATE".equals(point.getCategory()))
                .min(Comparator.comparing(CampusPoint::getSortOrder))
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_EVIDENCE, "尚未配置默认起点"))
                : catalogService.requirePublishedPoint(startPointId);
        if (start.getRoadNodeId() == null || end.getRoadNodeId() == null) {
            throw new BusinessException(ErrorCode.NO_EVIDENCE, "点位尚未绑定道路节点");
        }

        List<RoadNode> nodes = catalogService.enabledNodes();
        PlannedPath result = pgRoutingEnabled
                ? planWithPgRouting(start.getRoadNodeId(), end.getRoadNodeId(), nodes)
                : planInMemory(start.getRoadNodeId(), end.getRoadNodeId(), nodes);
        if (!result.found()) {
            throw new BusinessException(ErrorCode.NO_EVIDENCE, "当前未配置可行步行路线");
        }

        Map<Long, RoadNode> nodeMap = new HashMap<>();
        nodes.forEach(node -> nodeMap.put(node.getId(), node));
        List<double[]> path = new ArrayList<>();
        path.add(new double[]{start.getX(), start.getZ()});
        for (Long nodeId : result.nodes()) {
            RoadNode node = nodeMap.get(nodeId);
            path.add(new double[]{node.getX(), node.getZ()});
        }
        path.add(new double[]{end.getX(), end.getZ()});

        List<NavigationStep> steps = new ArrayList<>();
        for (int i = 1; i < result.nodes().size(); i++) {
            RoadNode from = nodeMap.get(result.nodes().get(i - 1));
            RoadNode to = nodeMap.get(result.nodes().get(i));
            double distance = Math.hypot(from.getX() - to.getX(), from.getZ() - to.getZ());
            steps.add(new NavigationStep(
                    i,
                    from.getId(),
                    to.getId(),
                    "沿校园道路向" + to.getName() + "方向步行约" + Math.round(distance) + "米",
                    distance
            ));
        }
        if (steps.isEmpty()) {
            steps.add(new NavigationStep(1, start.getRoadNodeId(), end.getRoadNodeId(),
                    "起点与终点位于同一道路节点，请沿现场标识前往", 0));
        }
        double speed = Math.max(0.5, configService.getDouble("scene.roamSpeed", 2.4));
        int estimatedSeconds = (int) Math.ceil(result.distance() / speed);
        return new RoutePlan(
                start.getId(), start.getName(), end.getId(), end.getName(),
                round(result.distance()), estimatedSeconds, path, steps,
                List.of(source(start), source(end))
        );
    }

    private PlannedPath planInMemory(long startNodeId, long endNodeId, List<RoadNode> nodes) {
        PathPlanner.PathResult result =
                pathPlanner.plan(startNodeId, endNodeId, nodes, catalogService.enabledEdges());
        return new PlannedPath(result.nodes(), result.distance());
    }

    private PlannedPath planWithPgRouting(long startNodeId, long endNodeId, List<RoadNode> nodes) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT node
                FROM pgr_dijkstra(
                    'SELECT id, source, target, cost, reverse_cost FROM campus_road_geometry',
                    ?, ?, directed := false
                )
                ORDER BY seq
                """, startNodeId, endNodeId);
        if (rows.isEmpty()) return PlannedPath.empty();

        Map<Long, RoadNode> nodeMap = new HashMap<>();
        nodes.forEach(node -> nodeMap.put(node.getId(), node));
        List<Long> path = rows.stream()
                .map(row -> ((Number) row.get("node")).longValue())
                .toList();
        if (!path.contains(startNodeId) || path.get(path.size() - 1) != endNodeId) {
            return PlannedPath.empty();
        }

        double distance = 0;
        for (int index = 1; index < path.size(); index++) {
            RoadNode from = nodeMap.get(path.get(index - 1));
            RoadNode to = nodeMap.get(path.get(index));
            if (from == null || to == null) return PlannedPath.empty();
            distance += Math.hypot(from.getX() - to.getX(), from.getZ() - to.getZ());
        }
        return new PlannedPath(path, distance);
    }

    private SourceRef source(CampusPoint point) {
        return new SourceRef(
                point.getSourceTitle() == null ? point.getName() : point.getSourceTitle(),
                point.getSourceUrl(),
                point.getDescription(),
                null
        );
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record PlannedPath(List<Long> nodes, double distance) {
        static PlannedPath empty() {
            return new PlannedPath(List.of(), Double.POSITIVE_INFINITY);
        }

        boolean found() {
            return !nodes.isEmpty();
        }
    }
}
