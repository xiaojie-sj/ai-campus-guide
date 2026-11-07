package com.hbeu.campusguide.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hbeu.campusguide.domain.AuditLog;
import com.hbeu.campusguide.domain.CampusPoint;
import com.hbeu.campusguide.domain.ChatLog;
import com.hbeu.campusguide.domain.KnowledgeDocument;
import com.hbeu.campusguide.domain.RoadEdge;
import com.hbeu.campusguide.domain.RoadNode;
import com.hbeu.campusguide.dto.DashboardResponse;
import com.hbeu.campusguide.dto.DashboardResponse.Assets;
import com.hbeu.campusguide.dto.DashboardResponse.DailyPoint;
import com.hbeu.campusguide.dto.DashboardResponse.NamedCount;
import com.hbeu.campusguide.dto.DashboardResponse.PointMarker;
import com.hbeu.campusguide.dto.DashboardResponse.RecentAudit;
import com.hbeu.campusguide.dto.DashboardResponse.RoadEdgeMarker;
import com.hbeu.campusguide.dto.DashboardResponse.RoadNodeMarker;
import com.hbeu.campusguide.dto.DashboardResponse.TableStat;
import com.hbeu.campusguide.mapper.AdminUserMapper;
import com.hbeu.campusguide.mapper.AuditLogMapper;
import com.hbeu.campusguide.mapper.CameraPresetMapper;
import com.hbeu.campusguide.mapper.CampusMediaMapper;
import com.hbeu.campusguide.mapper.CampusPointMapper;
import com.hbeu.campusguide.mapper.ChatLogMapper;
import com.hbeu.campusguide.mapper.FaqItemMapper;
import com.hbeu.campusguide.mapper.KnowledgeChunkMapper;
import com.hbeu.campusguide.mapper.KnowledgeDocumentMapper;
import com.hbeu.campusguide.mapper.RoadEdgeMapper;
import com.hbeu.campusguide.mapper.RoadNodeMapper;
import com.hbeu.campusguide.mapper.SystemConfigMapper;
import com.hbeu.campusguide.mapper.TourRouteMapper;
import com.hbeu.campusguide.mapper.TourStopMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Read-only aggregation across every operational table used by the admin
 * console. The data wall reflects the live database, not a static mock.
 *
 * <p>Raw questions and audit details are only returned to roles that may
 * access /chat-logs and /audit-logs. Other roles still receive aggregate
 * metrics and the spatial asset snapshot.</p>
 */
@Service
public class DashboardService {
    private static final DateTimeFormatter TREND_FORMAT = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ChatLogMapper chatLogMapper;
    private final CampusPointMapper pointMapper;
    private final RoadNodeMapper roadNodeMapper;
    private final RoadEdgeMapper roadEdgeMapper;
    private final CameraPresetMapper cameraMapper;
    private final TourRouteMapper tourMapper;
    private final TourStopMapper tourStopMapper;
    private final FaqItemMapper faqMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final CampusMediaMapper mediaMapper;
    private final AdminUserMapper adminUserMapper;
    private final SystemConfigMapper configMapper;
    private final AuditLogMapper auditLogMapper;

    public DashboardService(
            ChatLogMapper chatLogMapper,
            CampusPointMapper pointMapper,
            RoadNodeMapper roadNodeMapper,
            RoadEdgeMapper roadEdgeMapper,
            CameraPresetMapper cameraMapper,
            TourRouteMapper tourMapper,
            TourStopMapper tourStopMapper,
            FaqItemMapper faqMapper,
            KnowledgeDocumentMapper documentMapper,
            KnowledgeChunkMapper chunkMapper,
            CampusMediaMapper mediaMapper,
            AdminUserMapper adminUserMapper,
            SystemConfigMapper configMapper,
            AuditLogMapper auditLogMapper
    ) {
        this.chatLogMapper = chatLogMapper;
        this.pointMapper = pointMapper;
        this.roadNodeMapper = roadNodeMapper;
        this.roadEdgeMapper = roadEdgeMapper;
        this.cameraMapper = cameraMapper;
        this.tourMapper = tourMapper;
        this.tourStopMapper = tourStopMapper;
        this.faqMapper = faqMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.mediaMapper = mediaMapper;
        this.adminUserMapper = adminUserMapper;
        this.configMapper = configMapper;
        this.auditLogMapper = auditLogMapper;
    }

    public DashboardResponse snapshot(boolean includeSensitiveDetails) {
        LocalDate today = LocalDate.now();
        LocalDate trendStart = today.minusDays(13);
        LocalDateTime windowStart = today.minusDays(29).atStartOfDay();
        LocalDateTime windowEnd = today.plusDays(1).atStartOfDay();

        List<ChatLog> logs = chatLogMapper.selectList(new QueryWrapper<ChatLog>()
                .ge("created_at", windowStart)
                .lt("created_at", windowEnd));

        long totalConversations = count(chatLogMapper);
        long unanswerable = chatLogMapper.selectCount(new QueryWrapper<ChatLog>()
                .in("answer_status", "NO_EVIDENCE", "OUT_OF_SCOPE"));
        List<ChatLog> recent = chatLogMapper.selectList(new QueryWrapper<ChatLog>()
                .ge("created_at", today.minusDays(1).atStartOfDay()));
        long activeSessions = recent.stream()
                .map(ChatLog::getSessionId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long todayConversations = recent.stream()
                .filter(log -> log.getCreatedAt() != null && log.getCreatedAt().toLocalDate().equals(today))
                .count();

        List<CampusPoint> pointEntities = pointMapper.selectList(null);
        Map<Long, String> pointNames = pointEntities.stream()
                .collect(Collectors.toMap(CampusPoint::getId, CampusPoint::getName, (a, b) -> a));

        Map<String, Long> intents = countBy(logs.stream().map(ChatLog::getIntent).toList(), "UNKNOWN");
        Map<String, Long> answerStatus = countBy(logs.stream().map(ChatLog::getAnswerStatus).toList(), "UNKNOWN");
        Map<String, Long> feedback = countBy(logs.stream()
                .map(log -> log.getFeedback() == null ? "NONE" : log.getFeedback()).toList(), "NONE");

        List<NamedCount> popularPoints = logs.stream()
                .filter(log -> log.getPointId() != null && pointNames.containsKey(log.getPointId()))
                .collect(Collectors.groupingBy(log -> pointNames.get(log.getPointId()), Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new NamedCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(NamedCount::count).reversed())
                .limit(8)
                .toList();

        List<NamedCount> frequentQuestions = logs.stream()
                .filter(log -> "NO_EVIDENCE".equals(log.getAnswerStatus())
                        || "OUT_OF_SCOPE".equals(log.getAnswerStatus()))
                .collect(Collectors.groupingBy(log -> truncate(log.getMessage(), 72), Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new NamedCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(NamedCount::count).reversed())
                .limit(8)
                .toList();

        Map<LocalDate, List<ChatLog>> byDay = logs.stream()
                .filter(log -> log.getCreatedAt() != null)
                .collect(Collectors.groupingBy(log -> log.getCreatedAt().toLocalDate()));
        List<DailyPoint> dailyTrend = IntStream.rangeClosed(0, 13)
                .mapToObj(offset -> trendStart.plusDays(offset))
                .map(day -> {
                    List<ChatLog> dayLogs = byDay.getOrDefault(day, List.of());
                    long misses = dayLogs.stream()
                            .filter(log -> "NO_EVIDENCE".equals(log.getAnswerStatus())
                                    || "OUT_OF_SCOPE".equals(log.getAnswerStatus()))
                            .count();
                    return new DailyPoint(day.format(TREND_FORMAT), dayLogs.size(), misses);
                })
                .toList();

        Map<Integer, Long> byHour = logs.stream()
                .filter(log -> log.getCreatedAt() != null)
                .collect(Collectors.groupingBy(log -> log.getCreatedAt().getHour(), Collectors.counting()));
        List<NamedCount> hourly = IntStream.range(0, 24)
                .mapToObj(hour -> new NamedCount(String.format("%02d", hour), byHour.getOrDefault(hour, 0L)))
                .toList();

        List<Long> latencies = logs.stream()
                .map(ChatLog::getLatencyMs)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        double averageLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long p95 = latencies.isEmpty()
                ? 0
                : latencies.get(Math.min(latencies.size() - 1, (int) Math.ceil(latencies.size() * 0.95) - 1));

        Map<String, Long> pointCategories = countBy(
                pointEntities.stream().map(CampusPoint::getCategory).toList(), "OTHER");
        List<KnowledgeDocument> documents = documentMapper.selectList(null);
        Map<String, Long> knowledgeStatus = countBy(documents.stream().map(KnowledgeDocument::getStatus).toList(), "UNKNOWN");
        Map<String, Long> parseStatus = countBy(documents.stream().map(KnowledgeDocument::getParseStatus).toList(), "UNKNOWN");

        List<RoadNode> roadNodeEntities = roadNodeMapper.selectList(null);
        List<RoadEdge> roadEdgeEntities = roadEdgeMapper.selectList(null);
        List<PointMarker> pointMarkers = pointEntities.stream()
                .map(point -> new PointMarker(
                        value(point.getId()),
                        point.getName(),
                        point.getCategory(),
                        value(point.getX()),
                        value(point.getZ()),
                        value(point.getBuildingHeight()),
                        point.getStatus(),
                        point.getColorHex()))
                .toList();
        List<RoadNodeMarker> roadNodes = roadNodeEntities.stream()
                .filter(node -> !Boolean.FALSE.equals(node.getEnabled()))
                .map(node -> new RoadNodeMarker(value(node.getId()), value(node.getX()), value(node.getZ())))
                .toList();
        List<RoadEdgeMarker> roadEdges = roadEdgeEntities.stream()
                .filter(edge -> !Boolean.FALSE.equals(edge.getEnabled()))
                .map(edge -> new RoadEdgeMarker(value(edge.getFromNodeId()), value(edge.getToNodeId())))
                .toList();

        long points = pointEntities.size();
        long roadNodeCount = roadNodes.size();
        long roadEdgeCount = roadEdges.size();
        long cameras = count(cameraMapper);
        long tours = count(tourMapper);
        long tourStops = count(tourStopMapper);
        long faqs = count(faqMapper);
        long docCount = documents.size();
        long chunks = count(chunkMapper);
        long media = count(mediaMapper);
        long admins = count(adminUserMapper);
        long configs = count(configMapper);
        long audits = count(auditLogMapper);

        List<TableStat> tableStats = List.of(
                new TableStat("campus_point", "校园点位", points),
                new TableStat("road_node", "路网节点", roadNodeCount),
                new TableStat("road_edge", "路网边", roadEdgeCount),
                new TableStat("camera_preset", "预置机位", cameras),
                new TableStat("tour_route", "精品路线", tours),
                new TableStat("tour_stop", "路线站点", tourStops),
                new TableStat("knowledge_document", "知识文档", docCount),
                new TableStat("knowledge_chunk", "知识切片", chunks),
                new TableStat("faq_item", "高频问答", faqs),
                new TableStat("campus_media", "校园媒体", media),
                new TableStat("chat_log", "对话日志", totalConversations),
                new TableStat("admin_user", "管理员", admins),
                new TableStat("system_config", "系统参数", configs),
                new TableStat("audit_log", "审计日志", audits)
        );

        List<RecentAudit> recentAudits = auditLogMapper.selectPage(
                        new Page<>(1, 6, false),
                        new QueryWrapper<AuditLog>().orderByDesc("created_at"))
                .getRecords()
                .stream()
                .map(log -> new RecentAudit(
                        log.getAction(),
                        log.getTargetType(),
                        truncate(log.getDetail(), 90),
                        log.getCreatedAt() == null ? "" : log.getCreatedAt().format(TIME_FORMAT)))
                .toList();

        Assets assets = new Assets(points, roadNodeCount, roadEdgeCount, cameras, tours, tourStops,
                faqs, docCount, chunks, media, admins, configs, audits);

        return new DashboardResponse(
                totalConversations,
                todayConversations,
                unanswerable,
                activeSessions,
                Math.round(averageLatency * 10.0) / 10.0,
                p95,
                intents,
                answerStatus,
                feedback,
                popularPoints,
                includeSensitiveDetails ? frequentQuestions : List.of(),
                dailyTrend,
                hourly,
                pointCategories,
                knowledgeStatus,
                parseStatus,
                tableStats,
                includeSensitiveDetails ? recentAudits : List.of(),
                pointMarkers,
                roadNodes,
                roadEdges,
                assets
        );
    }

    private static long value(Long number) {
        return number == null ? 0 : number;
    }

    private static double value(Double number) {
        return number == null ? 0 : number;
    }

    private static long count(BaseMapper<?> mapper) {
        Long value = mapper.selectCount(null);
        return value == null ? 0 : value;
    }

    private static Map<String, Long> countBy(Collection<String> values, String fallback) {
        Map<String, Long> counts = values.stream()
                .map(value -> value == null || value.isBlank() ? fallback : value)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<Map.Entry<String, Long>> ordered = new ArrayList<>(counts.entrySet());
        ordered.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        Map<String, Long> result = new LinkedHashMap<>();
        ordered.forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }
}
