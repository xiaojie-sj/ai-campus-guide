package com.hbeu.campusguide.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(
        Long logId,
        String sessionId,
        String intent,
        double confidence,
        String answer,
        String answerStatus,
        List<SourceRef> sources,
        List<MediaRef> media,
        SceneCommand command
) {
}
