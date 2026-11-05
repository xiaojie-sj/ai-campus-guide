package com.hbeu.campusguide.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hbeu.campusguide.common.ApiResponse;
import com.hbeu.campusguide.config.OpenApiConfig;
import com.hbeu.campusguide.config.RequireRole;
import com.hbeu.campusguide.domain.AuditLog;
import com.hbeu.campusguide.domain.ChatLog;
import com.hbeu.campusguide.dto.StatsResponse;
import com.hbeu.campusguide.mapper.AuditLogMapper;
import com.hbeu.campusguide.mapper.ChatLogMapper;
import com.hbeu.campusguide.service.StatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only analytics and audit endpoints.
 */
@Tag(name = "04. 数据分析与审计", description = "问答日志、运营统计和审计日志")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/admin")
@RequireRole({"DATA_ANALYST", "SYSTEM_ADMIN"})
public class AdminAnalyticsController {
    private final ChatLogMapper chatLogMapper;
    private final AuditLogMapper auditLogMapper;
    private final StatsService statsService;

    public AdminAnalyticsController(
            ChatLogMapper chatLogMapper,
            AuditLogMapper auditLogMapper,
            StatsService statsService
    ) {
        this.chatLogMapper = chatLogMapper;
        this.auditLogMapper = auditLogMapper;
        this.statsService = statsService;
    }

    @GetMapping("/chat-logs")
    public ApiResponse<Page<ChatLog>> chatLogs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "30") long size,
            @RequestParam(required = false) String intent,
            @RequestParam(required = false) String answerStatus
    ) {
        QueryWrapper<ChatLog> query = new QueryWrapper<ChatLog>().orderByDesc("created_at");
        if (intent != null && !intent.isBlank()) {
            query.eq("intent", intent);
        }
        if (answerStatus != null && !answerStatus.isBlank()) {
            query.eq("answer_status", answerStatus);
        }
        return ApiResponse.ok(chatLogMapper.selectPage(
                new Page<>(Math.max(1, page), Math.min(100, Math.max(1, size))), query));
    }

    @GetMapping("/stats")
    public ApiResponse<StatsResponse> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.ok(statsService.summary(from, to));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AuditLog>> auditLogs(@RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(auditLogMapper.selectList(
                new QueryWrapper<AuditLog>().orderByDesc("created_at")
                        .last("LIMIT " + Math.min(500, Math.max(1, limit)))));
    }
}
