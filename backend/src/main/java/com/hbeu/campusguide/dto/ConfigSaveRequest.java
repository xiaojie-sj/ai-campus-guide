package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfigSaveRequest(
        @NotBlank @Size(max = 1000) String value
) {
}
