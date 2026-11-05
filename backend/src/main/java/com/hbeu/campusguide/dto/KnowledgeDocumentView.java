package com.hbeu.campusguide.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record KnowledgeDocumentView(
        Long id,
        String title,
        String originalFilename,
        String sourceTitle,
        String sourceUrl,
        String effectiveYear,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String category,
        String status,
        String parseStatus,
        int chunkCount,
        String indexVersion,
        String reviewer,
        String failureReason,
        LocalDateTime updatedAt
) {
}
