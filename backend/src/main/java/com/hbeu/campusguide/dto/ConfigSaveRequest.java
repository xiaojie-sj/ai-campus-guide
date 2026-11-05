package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfigSaveRequest(
        @NotBlank String value
) {
}
