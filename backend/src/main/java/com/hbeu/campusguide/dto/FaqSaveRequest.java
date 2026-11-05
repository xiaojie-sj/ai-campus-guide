package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record FaqSaveRequest(
        @NotBlank String question,
        String aliases,
        @NotBlank String answer,
        String category,
        String sourceTitle,
        String sourceUrl,
        Integer priority,
        String status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
