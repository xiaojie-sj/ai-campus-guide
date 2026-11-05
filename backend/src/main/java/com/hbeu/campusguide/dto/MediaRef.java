package com.hbeu.campusguide.dto;

public record MediaRef(
        Long id,
        String title,
        String url,
        String altText,
        String sourceTitle,
        String sourceUrl
) {
}
