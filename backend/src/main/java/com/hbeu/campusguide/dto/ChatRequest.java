package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @Size(max = 64) String sessionId,
        @NotBlank @Size(max = 500) String message,
        Long currentPointId,
        @Pattern(regexp = "TEXT|VOICE") String inputType
) {
}
