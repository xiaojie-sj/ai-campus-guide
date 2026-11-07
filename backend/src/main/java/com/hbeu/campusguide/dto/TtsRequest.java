package com.hbeu.campusguide.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TtsRequest(
        @NotBlank @Size(max = 2000) String text,
        @DecimalMin("0.5") @DecimalMax("2.0") Double speed
) {
}
