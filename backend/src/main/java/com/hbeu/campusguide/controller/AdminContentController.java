package com.hbeu.campusguide.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hbeu.campusguide.common.ApiResponse;
import com.hbeu.campusguide.common.BusinessException;
import com.hbeu.campusguide.common.ErrorCode;
import com.hbeu.campusguide.config.AdminContext;
import com.hbeu.campusguide.config.RequireRole;
import com.hbeu.campusguide.domain.CameraPreset;
import com.hbeu.campusguide.domain.CampusPoint;
import com.hbeu.campusguide.domain.FaqItem;
import com.hbeu.campusguide.domain.RoadEdge;
import com.hbeu.campusguide.domain.RoadNode;
import com.hbeu.campusguide.domain.TourRoute;
import com.hbeu.campusguide.domain.TourStop;
import com.hbeu.campusguide.dto.CameraSaveRequest;
import com.hbeu.campusguide.dto.ConfigSaveRequest;
import com.hbeu.campusguide.dto.FaqSaveRequest;
import com.hbeu.campusguide.dto.KnowledgeDocumentView;
import com.hbeu.campusguide.dto.PointSaveRequest;
import com.hbeu.campusguide.dto.RoadEdgeSaveRequest;
import com.hbeu.campusguide.dto.RoadNodeSaveRequest;
import com.hbeu.campusguide.dto.TourSaveRequest;
import com.hbeu.campusguide.dto.TourStopInput;
import com.hbeu.campusguide.mapper.CameraPresetMapper;
import com.hbeu.campusguide.mapper.CampusPointMapper;
import com.hbeu.campusguide.mapper.FaqItemMapper;
import com.hbeu.campusguide.mapper.RoadEdgeMapper;
import com.hbeu.campusguide.mapper.RoadNodeMapper;
import com.hbeu.campusguide.mapper.TourRouteMapper;
import com.hbeu.campusguide.mapper.TourStopMapper;
import com.hbeu.campusguide.service.AuditService;
import com.hbeu.campusguide.service.CatalogService;
import com.hbeu.campusguide.service.ConfigService;
import com.hbeu.campusguide.service.KnowledgeIngestionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Content operations available to the independent admin application.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminContentController {
    private final CampusPointMapper pointMapper;
    private final RoadNodeMapper nodeMapper;
    private final RoadEdgeMapper edgeMapper;
    private final CameraPresetMapper cameraMapper;
    private final TourRouteMapper routeMapper;
    private final TourStopMapper stopMapper;
    private final FaqItemMapper faqMapper;
    private final CatalogService catalogService;
    private final ConfigService configService;
    private final KnowledgeIngestionService knowledgeService;
    private final AuditService auditService;

    public AdminContentController(
            CampusPointMapper pointMapper,
            RoadNodeMapper nodeMapper,
            RoadEdgeMapper edgeMapper,
            CameraPresetMapper cameraMapper,
            TourRouteMapper routeMapper,
            TourStopMapper stopMapper,
            FaqItemMapper faqMapper,
            CatalogService catalogService,
            ConfigService configService,
            KnowledgeIngestionService knowledgeService,
            AuditService auditService
    ) {
        this.pointMapper = pointMapper;
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
        this.cameraMapper = cameraMapper;
        this.routeMapper = routeMapper;
        this.stopMapper = stopMapper;
        this.faqMapper = faqMapper;
        this.catalogService = catalogService;
        this.configService = configService;
        this.knowledgeService = knowledgeService;
        this.auditService = auditService;
    }

    @GetMapping("/points")
    @RequireRole({"CONTENT_ADMIN", "REVIEWER", "SYSTEM_ADMIN"})
    public ApiResponse<List<CampusPoint>> points() {
        return ApiResponse.ok(catalogService.allPoints());
    }

    @PostMapping("/points")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    public ApiResponse<CampusPoint> createPoint(@Valid @RequestBody PointSaveRequest request) {
        CampusPoint point = new CampusPoint();
        apply(point, request);
        point.setUpdatedAt(LocalDateTime.now());
        pointMapper.insert(point);
        audit("POINT_CREATE", point.getId(), point.getName());
        return ApiResponse.ok(point);
    }

    @PutMapping("/points/{id}")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    public ApiResponse<CampusPoint> updatePoint(
            @PathVariable long id,
            @Valid @RequestBody PointSaveRequest request
    ) {
        CampusPoint point = pointMapper.selectById(id);
        if (point == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "点位不存在");
        }
        apply(point, request);
        point.setUpdatedAt(LocalDateTime.now());
        pointMapper.updateById(point);
        audit("POINT_UPDATE", id, point.getName());
        return ApiResponse.ok(point);
    }

    @DeleteMapping("/points/{id}")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    public ApiResponse<Void> deletePoint(@PathVariable long id) {
        long references = stopMapper.selectCount(Wrappers.<TourStop>lambdaQuery().eq(TourStop::getPointId, id));
        if (references > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "点位仍被精品路线引用，请先解除引用或下线");
        }
        CampusPoint point = pointMapper.selectById(id);
        pointMapper.deleteById(id);
        audit("POINT_DELETE", id, point == null ? "" : point.getName());
        return ApiResponse.ok("点位已删除", null);
    }

    @GetMapping("/roads")
    @RequireRole({"CONTENT_ADMIN", "REVIEWER", "SYSTEM_ADMIN"})
    public ApiResponse<Object> roads() {
        return ApiResponse.ok(new Object[]{nodeMapper.selectList(null), edgeMapper.selectList(null)});
    }

    @PostMapping("/roads/nodes")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    public ApiResponse<RoadNode> createNode(@Valid @RequestBody RoadNodeSaveRequest request) {
        RoadNode node = new RoadNode();
        node.setName(request.name());
        node.setX(request.x());
        node.setZ(request.z());
        node.setEnabled(request.enabled() == null || request.enabled());
        nodeMapper.insert(node);
        audit("ROAD_NODE_CREATE", node.getId(), node.getName());
        return ApiResponse.ok(node);
    }

    @DeleteMapping("/roads/nodes/{id}")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    public ApiResponse<Void> deleteNode(@PathVariable long id) {
        long edges = edgeMapper.selectCount(Wrappers.<RoadEdge>lambdaQuery()
                .eq(RoadEdge::getFromNodeId, id).or().eq(RoadEdge::getToNodeId, id));
        if (edges > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "道路节点仍被边引用");
        }
        nodeMapper.deleteById(id);
        audit("ROAD_NODE_DELETE", id, "");
        return ApiResponse.ok("道路节点已删除", null);
    }

    @PostMapping("/roads/edges")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    public ApiResponse<RoadEdge> createEdge(@Valid @RequestBody RoadEdgeSaveRequest request) {
        if (request.fromNodeId().equals(request.toNodeId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "道路边不能连接同一个节点");
        }
        RoadNode from = nodeMapper.selectById(request.fromNodeId());
        RoadNode to = nodeMapper.selectById(request.toNodeId());
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "道路节点不存在");
        }
        RoadEdge edge = new RoadEdge();
        edge.setFromNodeId(request.fromNodeId());
        edge.setToNodeId(request.toNodeId());
        edge.setDistance(request.distance() == null
                ? Math.hypot(from.getX() - to.getX(), from.getZ() - to.getZ()) : request.distance());
        edge.setWalkable(request.walkable() == null || request.walkable());
        edge.setEnabled(request.enabled() == null || request.enabled());
        edgeMapper.insert(edge);
        audit("ROAD_EDGE_CREATE", edge.getId(), from.getName() + " -> " + to.getName());
        return ApiResponse.ok(edge);
    }

    @DeleteMapping("/roads/edges/{id}")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    public ApiResponse<Void> deleteEdge(@PathVariable long id) {
        edgeMapper.deleteById(id);
        audit("ROAD_EDGE_DELETE", id, "");
        return ApiResponse.ok("道路边已删除", null);
    }

    @GetMapping("/cameras")
    @RequireRole({"CONTENT_ADMIN", "REVIEWER", "SYSTEM_ADMIN"})
    public ApiResponse<List<CameraPreset>> cameras() {
        return ApiResponse.ok(cameraMapper.selectList(
                Wrappers.<CameraPreset>lambdaQuery().orderByAsc(CameraPreset::getSortOrder)));
    }

    @PostMapping("/cameras")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    public ApiResponse<CameraPreset> createCamera(@Valid @RequestBody CameraSaveRequest request) {
        CameraPreset camera = new CameraPreset();
        apply(camera, request);
        cameraMapper.insert(camera);
        audit("CAMERA_CREATE", camera.getId(), camera.getName());
        return ApiResponse.ok(camera);
    }

    @PutMapping("/cameras/{id}")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    public ApiResponse<CameraPreset> updateCamera(
            @PathVariable long id,
            @Valid @RequestBody CameraSaveRequest request
    ) {
        CameraPreset camera = cameraMapper.selectById(id);
        if (camera == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "机位不存在");
        }
        apply(camera, request);
        cameraMapper.updateById(camera);
        audit("CAMERA_UPDATE", id, camera.getName());
        return ApiResponse.ok(camera);
    }

    @GetMapping("/tours")
    @RequireRole({"CONTENT_ADMIN", "REVIEWER", "SYSTEM_ADMIN"})
    public ApiResponse<List<TourRoute>> tours() {
        return ApiResponse.ok(routeMapper.selectList(
                Wrappers.<TourRoute>lambdaQuery().orderByAsc(TourRoute::getSortOrder)));
    }

    @GetMapping("/tours/{id}/stops")
    @RequireRole({"CONTENT_ADMIN", "REVIEWER", "SYSTEM_ADMIN"})
    public ApiResponse<List<TourStop>> stops(@PathVariable long id) {
        return ApiResponse.ok(stopMapper.selectList(Wrappers.<TourStop>lambdaQuery()
                .eq(TourStop::getRouteId, id).orderByAsc(TourStop::getSortOrder)));
    }

    @PostMapping("/tours")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    @Transactional
    public ApiResponse<TourRoute> createTour(@Valid @RequestBody TourSaveRequest request) {
        TourRoute route = new TourRoute();
        apply(route, request);
        routeMapper.insert(route);
        replaceStops(route.getId(), request.stops());
        audit("TOUR_CREATE", route.getId(), route.getName());
        return ApiResponse.ok(route);
    }

    @PutMapping("/tours/{id}")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    @Transactional
    public ApiResponse<TourRoute> updateTour(
            @PathVariable long id,
            @Valid @RequestBody TourSaveRequest request
    ) {
        TourRoute route = routeMapper.selectById(id);
        if (route == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "精品路线不存在");
        }
        apply(route, request);
        routeMapper.updateById(route);
        replaceStops(id, request.stops());
        audit("TOUR_UPDATE", id, route.getName());
        return ApiResponse.ok(route);
    }

    @GetMapping("/faqs")
    @RequireRole({"CONTENT_ADMIN", "REVIEWER", "SYSTEM_ADMIN"})
    public ApiResponse<List<FaqItem>> faqs() {
        return ApiResponse.ok(faqMapper.selectList(Wrappers.<FaqItem>lambdaQuery()
                .orderByDesc(FaqItem::getPriority).orderByDesc(FaqItem::getUpdatedAt)));
    }

    @PostMapping("/faqs")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    public ApiResponse<FaqItem> createFaq(@Valid @RequestBody FaqSaveRequest request) {
        FaqItem item = new FaqItem();
        apply(item, request);
        faqMapper.insert(item);
        audit("FAQ_CREATE", item.getId(), item.getQuestion());
        return ApiResponse.ok(item);
    }

    @PutMapping("/faqs/{id}")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    public ApiResponse<FaqItem> updateFaq(@PathVariable long id, @Valid @RequestBody FaqSaveRequest request) {
        FaqItem item = faqMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "FAQ 不存在");
        }
        apply(item, request);
        faqMapper.updateById(item);
        audit("FAQ_UPDATE", id, item.getQuestion());
        return ApiResponse.ok(item);
    }

    @DeleteMapping("/faqs/{id}")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    public ApiResponse<Void> deleteFaq(@PathVariable long id) {
        faqMapper.deleteById(id);
        audit("FAQ_DELETE", id, "");
        return ApiResponse.ok("FAQ 已删除", null);
    }

    @GetMapping("/knowledge")
    @RequireRole({"CONTENT_ADMIN", "REVIEWER", "SYSTEM_ADMIN"})
    public ApiResponse<List<KnowledgeDocumentView>> knowledge() {
        return ApiResponse.ok(knowledgeService.list());
    }

    @PostMapping(value = "/knowledge", consumes = "multipart/form-data")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    public ApiResponse<KnowledgeDocumentView> uploadKnowledge(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "sourceTitle", required = false) String sourceTitle,
            @RequestParam(value = "sourceUrl", required = false) String sourceUrl,
            @RequestParam(value = "effectiveYear", required = false) String effectiveYear,
            @RequestParam(value = "effectiveFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveFrom,
            @RequestParam(value = "effectiveTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveTo,
            @RequestParam(value = "category", required = false) String category
    ) {
        return ApiResponse.ok(knowledgeService.upload(
                file, title, sourceTitle, sourceUrl, effectiveYear,
                effectiveFrom, effectiveTo, category));
    }

    @PutMapping("/knowledge/{id}/publish")
    @RequireRole({"REVIEWER", "SYSTEM_ADMIN"})
    public ApiResponse<KnowledgeDocumentView> publishKnowledge(
            @PathVariable long id,
            @RequestParam(defaultValue = "true") boolean publish
    ) {
        return ApiResponse.ok(knowledgeService.publish(id, publish));
    }

    @PostMapping("/knowledge/{id}/reindex")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    public ApiResponse<KnowledgeDocumentView> reindexKnowledge(@PathVariable long id) {
        return ApiResponse.ok(knowledgeService.reindex(id));
    }

    @DeleteMapping("/knowledge/{id}")
    @RequireRole({"CONTENT_ADMIN", "SYSTEM_ADMIN"})
    public ApiResponse<Void> deleteKnowledge(@PathVariable long id) {
        knowledgeService.delete(id);
        return ApiResponse.ok("文档已归档", null);
    }

    @GetMapping("/configs")
    @RequireRole({"SYSTEM_ADMIN"})
    public ApiResponse<Object> configs() {
        return ApiResponse.ok(configService.list());
    }

    @PutMapping("/configs/{key}")
    @RequireRole({"SYSTEM_ADMIN"})
    public ApiResponse<Object> updateConfig(
            @PathVariable String key,
            @Valid @RequestBody ConfigSaveRequest request
    ) {
        return ApiResponse.ok(configService.update(key, request.value()));
    }

    private void apply(CampusPoint point, PointSaveRequest request) {
        point.setName(request.name());
        point.setAliases(request.aliases());
        point.setCategory(request.category());
        point.setDescription(request.description());
        point.setX(request.x());
        point.setZ(request.z());
        point.setBuildingHeight(request.buildingHeight() == null ? 4 : request.buildingHeight());
        point.setColorHex(request.colorHex() == null ? "#7d9a8b" : request.colorHex());
        point.setModelKey(request.modelKey());
        point.setRoadNodeId(request.roadNodeId());
        point.setStatus(request.status() == null ? "DRAFT" : request.status());
        point.setVerificationStatus(request.verificationStatus() == null ? "PENDING" : request.verificationStatus());
        point.setSourceTitle(request.sourceTitle());
        point.setSourceUrl(request.sourceUrl());
        point.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private void apply(CameraPreset camera, CameraSaveRequest request) {
        camera.setPresetKey(request.key());
        camera.setName(request.name());
        camera.setPointId(request.pointId());
        camera.setX(request.x());
        camera.setY(request.y());
        camera.setZ(request.z());
        camera.setTargetX(request.targetX());
        camera.setTargetY(request.targetY());
        camera.setTargetZ(request.targetZ());
        camera.setPublicVisible(request.publicVisible() == null || request.publicVisible());
        camera.setStatus(request.status() == null ? "PUBLISHED" : request.status());
        camera.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private void apply(TourRoute route, TourSaveRequest request) {
        route.setRouteKey(request.key());
        route.setName(request.name());
        route.setSummary(request.summary());
        route.setAudience(request.audience());
        route.setEstimatedMinutes(request.estimatedMinutes() == null ? 10 : request.estimatedMinutes());
        route.setStatus(request.status() == null ? "DRAFT" : request.status());
        route.setVerificationStatus(request.verificationStatus() == null ? "PENDING" : request.verificationStatus());
        route.setEffectiveFrom(request.effectiveFrom());
        route.setEffectiveTo(request.effectiveTo());
        route.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private void replaceStops(long routeId, List<TourStopInput> inputs) {
        stopMapper.delete(Wrappers.<TourStop>lambdaQuery().eq(TourStop::getRouteId, routeId));
        for (int i = 0; i < inputs.size(); i++) {
            TourStopInput input = inputs.get(i);
            if (pointMapper.selectById(input.pointId()) == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "路线包含不存在的点位");
            }
            TourStop stop = new TourStop();
            stop.setRouteId(routeId);
            stop.setPointId(input.pointId());
            stop.setSortOrder(i + 1);
            stop.setNarration(input.narration());
            stop.setTransitionType(input.transitionType() == null ? "FLY" : input.transitionType());
            stopMapper.insert(stop);
        }
    }

    private void apply(FaqItem item, FaqSaveRequest request) {
        item.setQuestion(request.question());
        item.setAliases(request.aliases());
        item.setAnswer(request.answer());
        item.setCategory(request.category());
        item.setSourceTitle(request.sourceTitle());
        item.setSourceUrl(request.sourceUrl());
        item.setPriority(request.priority() == null ? 10 : request.priority());
        item.setStatus(request.status() == null ? "DRAFT" : request.status());
        item.setEffectiveFrom(request.effectiveFrom());
        item.setEffectiveTo(request.effectiveTo());
        item.setUpdatedAt(LocalDateTime.now());
    }

    private void audit(String action, long id, String detail) {
        auditService.record(AdminContext.current().userId(), action, "CONTENT", String.valueOf(id), detail);
    }
}
