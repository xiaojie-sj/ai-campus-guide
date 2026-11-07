package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record FaqSaveRequest(
        @NotBlank @Size(max = 500) String question,
        @Size(max = 1000) String aliases,
        @NotBlank @Size(max = 10000) String answer,
        @Size(max = 100) String category,
        @Size(max = 500) String sourceTitle,
        @Size(max = 1000) String sourceUrl,
        Integer priority,
        @Size(max = 40) String status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}

