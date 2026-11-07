package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record TourSaveRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{1,80}$") String key,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 2000) String summary,
        @Size(max = 160) String audience,
        Integer estimatedMinutes,
        @Size(max = 40) String status,
        @Size(max = 40) String verificationStatus,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Integer sortOrder,
        @NotEmpty @Size(max = 100) List<TourStopInput> stops
) {
}
