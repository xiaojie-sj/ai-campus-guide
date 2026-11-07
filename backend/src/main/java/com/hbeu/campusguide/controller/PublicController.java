package com.hbeu.campusguide.controller;

import com.hbeu.campusguide.ai.AiGateway;
import com.hbeu.campusguide.common.ApiResponse;
import com.hbeu.campusguide.common.BusinessException;
import com.hbeu.campusguide.common.ErrorCode;
import com.hbeu.campusguide.config.AppProperties;
import com.hbeu.campusguide.dto.BootstrapResponse;
import com.hbeu.campusguide.dto.ChatRequest;
import com.hbeu.campusguide.dto.ChatResponse;
import com.hbeu.campusguide.dto.FeedbackRequest;
import com.hbeu.campusguide.dto.PointView;
import com.hbeu.campusguide.dto.TourView;
import com.hbeu.campusguide.dto.TtsRequest;
import com.hbeu.campusguide.service.CatalogService;
import com.hbeu.campusguide.service.ChatService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Public, unauthenticated campus guide API.
 */
@Tag(name = "02. 公开导览", description = "无需登录的校园数据、数字人问答、语音和反馈接口")
@RestController
@RequestMapping("/api/public")
public class PublicController {
    private final CatalogService catalogService;
    private final ChatService chatService;
    private final AiGateway aiGateway;
    private final AppProperties properties;

    public PublicController(
            CatalogService catalogService,
            ChatService chatService,
            AiGateway aiGateway,
            AppProperties properties
    ) {
        this.catalogService = catalogService;
        this.chatService = chatService;
        this.aiGateway = aiGateway;
        this.properties = properties;
    }

    @GetMapping("/bootstrap")
    public ApiResponse<BootstrapResponse> bootstrap() {
        return ApiResponse.ok(catalogService.bootstrap());
    }

    @GetMapping("/points")
    public ApiResponse<List<PointView>> points() {
        return ApiResponse.ok(catalogService.publishedPoints().stream()
                .map(catalogService::toPointView).toList());
    }

    @GetMapping("/tours")
    public ApiResponse<List<TourView>> tours() {
        java.util.Map<Long, com.hbeu.campusguide.domain.CampusPoint> points = new java.util.HashMap<>();
        catalogService.publishedPoints().forEach(point -> points.put(point.getId(), point));
        return ApiResponse.ok(catalogService.publicTours(points));
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.ok(chatService.chat(request));
    }

    @PostMapping("/feedback")
    public ApiResponse<Void> feedback(@Valid @RequestBody FeedbackRequest request) {
        chatService.feedback(request.chatLogId(), request.sessionId(), request.feedback());
        return ApiResponse.ok("感谢反馈", null);
    }

    @PostMapping(value = "/asr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> transcribe(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "durationSeconds", defaultValue = "0") int durationSeconds
    ) {
        if (file.isEmpty() || durationSeconds < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "录音文件或时长不合法");
        }
        if (durationSeconds > properties.speech().maxSeconds()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "录音时长超过限制");
        }
        if (file.getSize() > 8 * 1024 * 1024L) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "录音文件过大");
        }
        try {
            return ApiResponse.ok(Map.of(
                    "text", aiGateway.transcribe(file.getBytes(), safeAudioFilename(file.getOriginalFilename())),
                    "provider", "remote"
            ));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "录音处理失败");
        }
    }

    private String safeAudioFilename(String original) {
        if (original == null || !original.matches("[A-Za-z0-9._-]{1,80}")) {
            return "speech.webm";
        }
        return original;
    }
    @PostMapping(value = "/tts", produces = "audio/mpeg")
    public ResponseEntity<byte[]> synthesize(@Valid @RequestBody TtsRequest request) {
        byte[] audio = aiGateway.synthesize(request.text(), request.speed() == null ? 1.0 : request.speed());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .body(audio);
    }
}


