package com.hbeu.campusguide.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbeu.campusguide.domain.CampusPoint;
import com.hbeu.campusguide.domain.ChatLog;
import com.hbeu.campusguide.dto.StatsResponse;
import com.hbeu.campusguide.mapper.CampusPointMapper;
import com.hbeu.campusguide.mapper.ChatLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsService {
    private final ChatLogMapper chatLogMapper;
    private final CampusPointMapper pointMapper;

    public StatsService(ChatLogMapper chatLogMapper, CampusPointMapper pointMapper) {
        this.chatLogMapper = chatLogMapper;
        this.pointMapper = pointMapper;
    }

    public StatsResponse summary(LocalDate from, LocalDate to) {
        LocalDate startDate = from == null ? LocalDate.now().minusDays(29) : from;
        LocalDate endDate = to == null ? LocalDate.now() : to;
        List<ChatLog> logs = chatLogMapper.selectList(new QueryWrapper<ChatLog>()
                .ge("created_at", startDate.atStartOfDay())
                .lt("created_at", endDate.plusDays(1).atStartOfDay()));

        Map<String, Long> intentCounts = new LinkedHashMap<>();
        logs.stream().collect(Collectors.groupingBy(
                log -> log.getIntent() == null ? "UNKNOWN" : log.getIntent(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> intentCounts.put(entry.getKey(), entry.getValue()));

        Map<Long, String> pointNames = pointMapper.selectList(null).stream()
                .collect(Collectors.toMap(CampusPoint::getId, CampusPoint::getName));
        List<StatsResponse.NamedCount> popular = logs.stream()
                .filter(log -> log.getPointId() != null && pointNames.containsKey(log.getPointId()))
                .collect(Collectors.groupingBy(log -> pointNames.get(log.getPointId()), Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new StatsResponse.NamedCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(StatsResponse.NamedCount::count).reversed())
                .limit(10)
                .toList();

        List<StatsResponse.NamedCount> questions = logs.stream()
                .filter(log -> "NO_EVIDENCE".equals(log.getAnswerStatus())
                        || "OUT_OF_SCOPE".equals(log.getAnswerStatus()))
                .collect(Collectors.groupingBy(ChatLog::getMessage, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new StatsResponse.NamedCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(StatsResponse.NamedCount::count).reversed())
                .limit(10)
                .toList();

        long unanswerable = logs.stream()
                .filter(log -> "NO_EVIDENCE".equals(log.getAnswerStatus())
                        || "OUT_OF_SCOPE".equals(log.getAnswerStatus()))
                .count();
        double averageLatency = logs.stream()
                .filter(log -> log.getLatencyMs() != null)
                .mapToLong(ChatLog::getLatencyMs)
                .average()
                .orElse(0);
        return new StatsResponse(logs.size(), unanswerable, Math.round(averageLatency * 10.0) / 10.0,
                intentCounts, popular, questions);
    }
}
