package com.hbeu.campusguide.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbeu.campusguide.algorithm.IntentRecognizer;
import com.hbeu.campusguide.algorithm.IntentResult;
import com.hbeu.campusguide.algorithm.IntentType;
import com.hbeu.campusguide.common.BusinessException;
import com.hbeu.campusguide.domain.CampusPoint;
import com.hbeu.campusguide.domain.ChatLog;
import com.hbeu.campusguide.dto.ChatRequest;
import com.hbeu.campusguide.dto.ChatResponse;
import com.hbeu.campusguide.dto.MediaRef;
import com.hbeu.campusguide.dto.RoutePlan;
import com.hbeu.campusguide.dto.SceneCommand;
import com.hbeu.campusguide.dto.SourceRef;
import com.hbeu.campusguide.mapper.ChatLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates intent recognition, trust-bounded answer generation and scene
 * actions. This is the single public conversation entry point.
 */
@Service
public class ChatService {
    private final IntentRecognizer intentRecognizer;
    private final CatalogService catalogService;
    private final RouteService routeService;
    private final RagService ragService;
    private final CampusMediaService mediaService;
    private final ConversationContextService contextService;
    private final ChatLogMapper chatLogMapper;
    private final ObjectMapper objectMapper;

    public ChatService(
            IntentRecognizer intentRecognizer,
            CatalogService catalogService,
            RouteService routeService,
            RagService ragService,
            CampusMediaService mediaService,
            ConversationContextService contextService,
            ChatLogMapper chatLogMapper,
            ObjectMapper objectMapper
    ) {
        this.intentRecognizer = intentRecognizer;
        this.catalogService = catalogService;
        this.routeService = routeService;
        this.ragService = ragService;
        this.mediaService = mediaService;
        this.contextService = contextService;
        this.chatLogMapper = chatLogMapper;
        this.objectMapper = objectMapper;
    }

    public ChatResponse chat(ChatRequest request) {
        long started = System.nanoTime();
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.sessionId().trim();
        Long contextPoint = contextService.referencePoint(sessionId);
        IntentResult intent = intentRecognizer.recognize(
                request.message(), catalogService.publishedPoints(), contextPoint);

        Draft draft = switch (intent.type()) {
            case BUILDING_INTRO -> buildingDraft(sessionId, intent);
            case ROUTE -> routeDraft(sessionId, request, intent);
            case INFO -> infoDraft(intent);
            case SCENE_CONTROL -> sceneDraft(sessionId, intent);
            case CLARIFY -> clarifyDraft(intent);
            case UNKNOWN -> unknownDraft(intent);
        };

        ChatLog log = new ChatLog();
        log.setSessionId(sessionId);
        log.setMessage(request.message());
        log.setInputType(request.inputType() == null ? "TEXT" : request.inputType());
        log.setIntent(intent.type().name());
        log.setConfidence(intent.confidence());
        log.setPointId(intent.pointId() != null ? intent.pointId() : intent.endPointId());
        log.setAnswerStatus(draft.status());
        log.setAnswer(draft.answer());
        log.setSourcesJson(writeJson(draft.sources()));
        log.setLatencyMs((System.nanoTime() - started) / 1_000_000);
        log.setCreatedAt(LocalDateTime.now());
        chatLogMapper.insert(log);

        return new ChatResponse(
                log.getId(),
                sessionId,
                intent.type().name(),
                intent.confidence(),
                draft.answer(),
                draft.status(),
                draft.sources(),
                draft.media(),
                draft.command()
        );
    }

    public void feedback(long logId, String sessionId, String feedback) {
        ChatLog log = chatLogMapper.selectById(logId);
        // The session id is the capability for a public conversation. Matching
        // it prevents one visitor from modifying another visitor's feedback.
        if (log == null || sessionId == null || !log.getSessionId().equals(sessionId.trim())) {
            throw new BusinessException(com.hbeu.campusguide.common.ErrorCode.NOT_FOUND, "对话记录不存在");
        }
        if (!List.of("HELPFUL", "UNHELPFUL", "OUTDATED").contains(feedback)) {
            throw new BusinessException(com.hbeu.campusguide.common.ErrorCode.BAD_REQUEST, "反馈类型不支持");
        }
        log.setFeedback(feedback);
        chatLogMapper.updateById(log);
    }

    private Draft buildingDraft(String sessionId, IntentResult intent) {
        CampusPoint point = catalogService.requirePublishedPoint(intent.pointId());
        contextService.rememberPoint(sessionId, point.getId());
        String answer = point.getDescription() == null || point.getDescription().isBlank()
                ? "该点位尚未编辑讲解内容。"
                : point.getDescription();
        return new Draft(
                answer,
                "POINT_PROFILE",
                List.of(pointSource(point)),
                mediaService.publishedForPoint(point.getId()),
                new SceneCommand("FOCUS_POINT", point.getId(), null, null, "INTRO")
        );
    }

    private Draft routeDraft(String sessionId, ChatRequest request, IntentResult intent) {
        Long startId = intent.startPointId() != null ? intent.startPointId() : request.currentPointId();
        RoutePlan route = routeService.plan(startId, intent.endPointId());
        contextService.rememberPoint(sessionId, route.endPointId());
        String stepNarration = route.steps().stream()
                .map(step -> step.instruction() + "。")
                .collect(java.util.stream.Collectors.joining(""));
        String answer = "从" + route.startName() + "前往" + route.endName()
                + "，预计步行" + Math.round(route.distance()) + "米，约"
                + Math.max(1, Math.round(route.estimatedSeconds() / 60.0)) + "分钟。"
                + "请沿画面中的发光路线行走。" + stepNarration;
        return new Draft(
                answer,
                "ROUTE_READY",
                route.sources(),
                mediaService.publishedForPoint(route.endPointId()),
                new SceneCommand("SHOW_ROUTE", route.endPointId(), null, route, "START_NAVIGATION")
        );
    }

    private Draft infoDraft(IntentResult intent) {
        RagService.RagAnswer rag = ragService.answer(intent.normalizedText());
        return new Draft(
                rag.answer(),
                rag.status(),
                rag.sources(),
                List.of(),
                SceneCommand.none()
        );
    }

    private Draft sceneDraft(String sessionId, IntentResult intent) {
        String action = intent.sceneAction() == null ? "OPEN_CAMERAS" : intent.sceneAction();
        if ("CLEAR_ROUTE".equals(action)) {
            contextService.clear(sessionId);
        } else if (intent.pointId() != null) {
            contextService.rememberPoint(sessionId, intent.pointId());
        }
        String answer = switch (action) {
            case "RESET_VIEW" -> "已恢复默认校园视角。";
            case "CLEAR_ROUTE" -> "已清除导航路线和当前路线上下文。";
            case "PAUSE_TOUR" -> "已暂停精品路线。";
            case "RESUME_TOUR" -> "已继续精品路线。";
            case "FOCUS_POINT" -> "正在切换到目标视角。";
            case "START_TOUR" -> "正在打开并准备播放精品路线。";
            default -> "请从机位列表选择一个观察视角。";
        };
        return new Draft(
                answer,
                "SCENE_ACTION",
                List.of(),
                mediaService.publishedForPoint(intent.pointId()),
                new SceneCommand(action, intent.pointId(), null, null, intent.tourKey())
        );
    }

    private Draft clarifyDraft(IntentResult intent) {
        String answer = "ASK_DESTINATION".equals(intent.sceneAction())
                ? "请告诉我要前往的具体点位，例如“带我去图书馆”。"
                : "我还不能确定你指的是哪个位置，请说出点位名称或从地图中选择。";
        return new Draft(answer, "CLARIFY", List.of(), List.of(), SceneCommand.none());
    }

    private Draft unknownDraft(IntentResult intent) {
        return new Draft(
                "我目前只能处理校园建筑介绍、步行导航、已审核校园信息查询和场景控制。"
                        + "你也可以直接说出一个校园点位名称。",
                "OUT_OF_SCOPE",
                List.of(),
                List.of(),
                SceneCommand.none()
        );
    }

    private SourceRef pointSource(CampusPoint point) {
        return new SourceRef(
                point.getSourceTitle() == null ? "已审核点位资料" : point.getSourceTitle(),
                point.getSourceUrl(),
                point.getDescription(),
                null
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private record Draft(
            String answer,
            String status,
            List<SourceRef> sources,
            List<MediaRef> media,
            SceneCommand command
    ) {
    }
}

