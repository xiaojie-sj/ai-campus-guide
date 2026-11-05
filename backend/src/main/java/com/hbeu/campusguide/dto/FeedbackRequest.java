package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotBlank;

public record FeedbackRequest(
        long chatLogId,
        @NotBlank String feedback
) {
}
