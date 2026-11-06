package com.hbeu.campusguide.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbeu.campusguide.common.BusinessException;
import com.hbeu.campusguide.common.ErrorCode;
import com.hbeu.campusguide.domain.CameraPreset;
import com.hbeu.campusguide.domain.CampusPoint;
import com.hbeu.campusguide.domain.RoadEdge;
import com.hbeu.campusguide.domain.RoadNode;
import com.hbeu.campusguide.domain.TourRoute;
import com.hbeu.campusguide.domain.TourStop;
import com.hbeu.campusguide.dto.BootstrapResponse;
import com.hbeu.campusguide.dto.CameraView;
import com.hbeu.campusguide.dto.CampusBuildingView;
import com.hbeu.campusguide.dto.PointView;
import com.hbeu.campusguide.dto.RoadEdgeView;
import com.hbeu.campusguide.dto.RoadNodeView;
import com.hbeu.campusguide.dto.TourStopView;
import com.hbeu.campusguide.dto.TourView;
import com.hbeu.campusguide.mapper.CameraPresetMapper;
import com.hbeu.campusguide.mapper.CampusPointMapper;
import com.hbeu.campusguide.mapper.RoadEdgeMapper;
import com.hbeu.campusguide.mapper.RoadNodeMapper;
import com.hbeu.campusguide.mapper.TourRouteMapper;
import com.hbeu.campusguide.mapper.TourStopMapper;
import com.hbeu.campusguide.config.DatabaseCapabilities;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read model shared by the public bootstrap endpoint, chat actions, and the
 * admin preview. Only published data crosses the public boundary.
 */
@Service
public class CatalogService {
    private final CampusPointMapper pointMapper;
    private final RoadNodeMapper nodeMapper;
    private final RoadEdgeMapper edgeMapper;
    private final CameraPresetMapper cameraMapper;
    private final TourRouteMapper routeMapper;
    private final TourStopMapper stopMapper;
    private final ConfigService configService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final boolean spatialEnabled;

    public CatalogService(
            CampusPointMapper pointMapper,
            RoadNodeMapper nodeMapper,
            RoadEdgeMapper edgeMapper,
            CameraPresetMapper cameraMapper,
            TourRouteMapper routeMapper,
            TourStopMapper stopMapper,
            ConfigService configService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            DatabaseCapabilities databaseCapabilities
    ) {
        this.pointMapper = pointMapper;
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
        this.cameraMapper = cameraMapper;
        this.routeMapper = routeMapper;
        this.stopMapper = stopMapper;
        this.configService = configService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.spatialEnabled = databaseCapabilities.postgis();
    }

    public BootstrapResponse bootstrap() {
        List<CampusPoint> pointEntities = publishedPoints();
        Map<Long, CampusPoint> pointMap = new HashMap<>();
        pointEntities.forEach(point -> pointMap.put(point.getId(), point));
        return new BootstrapResponse(
                pointEntities.stream().map(this::toPointView).toList(),
                campusBuildings(),
                enabledNodes().stream().map(node -> new RoadNodeView(
                        node.getId(), node.getName(), node.getX(), node.getZ())).toList(),
                enabledEdges().stream().map(edge -> new RoadEdgeView(
                        edge.getId(), edge.getFromNodeId(), edge.getToNodeId(),
                        edge.getDistance() == null ? 0 : edge.getDistance())).toList(),
                publicCameras().stream().map(this::toCameraView).toList(),
                publicTours(pointMap),
                configService.publicConfig(),
                LocalDate.now().toString()
        );
    }

    public List<CampusBuildingView> campusBuildings() {
        if (!spatialEnabled) return List.of();
        return jdbcTemplate.query("""
                SELECT id, point_id, name, category, height, color_hex, footprint_json
                FROM campus_building
                WHERE status = 'PUBLISHED'
                ORDER BY sort_order, id
                """, (rs, rowNum) -> new CampusBuildingView(
                rs.getLong("id"),
                rs.getObject("point_id", Long.class),
                rs.getString("name"),
                rs.getString("category"),
                rs.getDouble("height"),
                rs.getString("color_hex"),
                parseFootprint(rs.getString("footprint_json"))
        ));
    }

    private double[][] parseFootprint(String value) {
        try {
            return objectMapper.readValue(value, double[][].class);
        } catch (Exception exception) {
            return new double[0][];
        }
    }

    public List<CampusPoint> publishedPoints() {
        return pointMapper.selectList(Wrappers.<CampusPoint>lambdaQuery()
                .eq(CampusPoint::getStatus, "PUBLISHED")
                .orderByAsc(CampusPoint::getSortOrder)
                .orderByAsc(CampusPoint::getId));
    }

    public List<CampusPoint> allPoints() {
        return pointMapper.selectList(Wrappers.<CampusPoint>lambdaQuery()
                .orderByAsc(CampusPoint::getSortOrder)
                .orderByAsc(CampusPoint::getId));
    }

    public CampusPoint requirePublishedPoint(Long pointId) {
        CampusPoint point = pointMapper.selectById(pointId);
        if (point == null || !"PUBLISHED".equals(point.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "点位不存在或尚未发布");
        }
        return point;
    }

    public Optional<CampusPoint> findPublishedPoint(Long pointId) {
        if (pointId == null) {
            return Optional.empty();
        }
        CampusPoint point = pointMapper.selectById(pointId);
        return point != null && "PUBLISHED".equals(point.getStatus()) ? Optional.of(point) : Optional.empty();
    }

    public List<RoadNode> enabledNodes() {
        return nodeMapper.selectList(Wrappers.<RoadNode>lambdaQuery()
                .eq(RoadNode::getEnabled, true)
                .orderByAsc(RoadNode::getId));
    }

    public List<RoadEdge> enabledEdges() {
        return edgeMapper.selectList(Wrappers.<RoadEdge>lambdaQuery()
                .eq(RoadEdge::getEnabled, true)
                .eq(RoadEdge::getWalkable, true)
                .orderByAsc(RoadEdge::getId));
    }

    public List<CameraPreset> publicCameras() {
        return cameraMapper.selectList(Wrappers.<CameraPreset>lambdaQuery()
                .eq(CameraPreset::getStatus, "PUBLISHED")
                .eq(CameraPreset::getPublicVisible, true)
                .orderByAsc(CameraPreset::getSortOrder)
                .orderByAsc(CameraPreset::getId));
    }

    public List<TourView> publicTours(Map<Long, CampusPoint> points) {
        LocalDate today = LocalDate.now();
        List<TourRoute> routes = routeMapper.selectList(Wrappers.<TourRoute>lambdaQuery()
                .eq(TourRoute::getStatus, "PUBLISHED")
                .orderByAsc(TourRoute::getSortOrder)
                .orderByAsc(TourRoute::getId));
        return routes.stream()
                .filter(route -> route.getEffectiveFrom() == null || !route.getEffectiveFrom().isAfter(today))
                .filter(route -> route.getEffectiveTo() == null || !route.getEffectiveTo().isBefore(today))
                .map(route -> toTourView(route, points))
                .toList();
    }

    public TourView findTour(String key) {
        Map<Long, CampusPoint> points = new HashMap<>();
        publishedPoints().forEach(point -> points.put(point.getId(), point));
        return routeMapper.selectList(Wrappers.<TourRoute>lambdaQuery()
                        .eq(TourRoute::getRouteKey, key)
                        .eq(TourRoute::getStatus, "PUBLISHED"))
                .stream().findFirst().map(route -> toTourView(route, points)).orElse(null);
    }

    public PointView toPointView(CampusPoint point) {
        List<String> aliases = point.getAliases() == null || point.getAliases().isBlank()
                ? List.of()
                : Arrays.stream(point.getAliases().split("[,，、]")).map(String::trim)
                .filter(value -> !value.isBlank()).toList();
        return new PointView(
                point.getId(),
                point.getName(),
                aliases,
                point.getCategory(),
                point.getDescription(),
                point.getX(),
                point.getZ(),
                point.getBuildingHeight() == null ? 4 : point.getBuildingHeight(),
                point.getColorHex(),
                point.getModelKey(),
                point.getRoadNodeId(),
                point.getStatus(),
                point.getVerificationStatus(),
                point.getSourceTitle(),
                point.getSourceUrl(),
                point.getSortOrder() == null ? 0 : point.getSortOrder()
        );
    }

    private TourView toTourView(TourRoute route, Map<Long, CampusPoint> points) {
        List<TourStop> stops = stopMapper.selectList(Wrappers.<TourStop>lambdaQuery()
                .eq(TourStop::getRouteId, route.getId())
                .orderByAsc(TourStop::getSortOrder));
        List<TourStopView> stopViews = new ArrayList<>();
        for (TourStop stop : stops) {
            CampusPoint point = points.get(stop.getPointId());
            if (point == null) {
                continue;
            }
            stopViews.add(new TourStopView(
                    stop.getId(), point.getId(), point.getName(), point.getX(), point.getZ(),
                    stop.getSortOrder(), stop.getNarration(), stop.getTransitionType()));
        }
        return new TourView(
                route.getId(), route.getRouteKey(), route.getName(), route.getSummary(),
                route.getAudience(), route.getEstimatedMinutes() == null ? 10 : route.getEstimatedMinutes(),
                route.getVerificationStatus(), stopViews);
    }

    private CameraView toCameraView(CameraPreset camera) {
        return new CameraView(
                camera.getId(), camera.getPresetKey(), camera.getName(), camera.getPointId(),
                camera.getX(), camera.getY(), camera.getZ(),
                camera.getTargetX(), camera.getTargetY(), camera.getTargetZ(),
                camera.getSortOrder() == null ? 0 : camera.getSortOrder());
    }
}
