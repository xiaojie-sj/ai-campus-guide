package com.hbeu.campusguide.dto;

import java.util.List;
import java.util.Map;

/**
 * Aggregated operational snapshot for the admin data wall. Every field is
 * derived from the live database; no demo values are synthesized here.
 */
public record DashboardResponse(
        long totalConversations,
        long todayConversations,
        long unanswerableCount,
        long activeSessions,
        double averageLatencyMs,
        long p95LatencyMs,
        Map<String, Long> intentCounts,
        Map<String, Long> answerStatusCounts,
        Map<String, Long> feedbackCounts,
        List<NamedCount> popularPoints,
        List<NamedCount> frequentQuestions,
        List<DailyPoint> dailyTrend,
        List<NamedCount> hourlyActivity,
        Map<String, Long> pointCategoryCounts,
        Map<String, Long> knowledgeStatusCounts,
        Map<String, Long> parseStatusCounts,
        List<TableStat> tableStats,
        List<RecentAudit> recentAudits,
        List<PointMarker> pointMarkers,
        List<RoadNodeMarker> roadNodes,
        List<RoadEdgeMarker> roadEdges,
        Assets assets
) {
    public record NamedCount(String name, long count) {
    }

    public record DailyPoint(String date, long total, long unanswerable) {
    }

    public record TableStat(String table, String label, long rows) {
    }

    public record RecentAudit(String action, String targetType, String detail, String createdAt) {
    }

    /** Minimal geometry needed by the Three.js campus situation view. */
    public record PointMarker(
            long id,
            String name,
            String category,
            double x,
            double z,
            double height,
            String status,
            String colorHex
    ) {
    }

    public record RoadNodeMarker(long id, double x, double z) {
    }

    public record RoadEdgeMarker(long fromNodeId, long toNodeId) {
    }

    public record Assets(
            long points,
            long roadNodes,
            long roadEdges,
            long cameras,
            long tours,
            long tourStops,
            long faqs,
            long documents,
            long chunks,
            long media,
            long admins,
            long configs,
            long audits
    ) {
    }
}
