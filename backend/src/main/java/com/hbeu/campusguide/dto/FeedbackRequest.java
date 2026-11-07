package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(
        @Positive long chatLogId,
        @NotBlank @Size(max = 64) String sessionId,
        @NotBlank @Pattern(regexp = "HELPFUL|UNHELPFUL|OUTDATED") String feedback
) {
}
