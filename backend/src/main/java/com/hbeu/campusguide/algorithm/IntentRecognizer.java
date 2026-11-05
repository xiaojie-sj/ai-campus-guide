package com.hbeu.campusguide.algorithm;

import com.hbeu.campusguide.domain.CampusPoint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic intent recognizer used both online and in offline demo mode.
 * Rules are intentionally isolated from the chat controller so they can be
 * measured and regression-tested independently.
 */
@Component
public class IntentRecognizer {
    private static final List<String> ROUTE_MARKERS =
            List.of("怎么走", "怎么去", "带我去", "导航", "路线", "前往", "步行到", "从");
    private static final List<String> BUILDING_MARKERS =
            List.of("介绍一下", "介绍", "讲解", "是什么", "有什么", "做什么", "看看", "详情");
    private static final List<String> INFO_MARKERS =
            List.of(
                    "几点", "开放", "时间", "专业", "报到", "招生", "电话", "地址", "官网",
                    "政策", "学费", "宿舍分配", "学校", "学院", "类型", "性质", "办学",
                    "教学单位", "校训", "规模"
            );
    private static final List<String> LOCATION_MARKERS =
            List.of("在哪", "哪里", "位置", "怎么找", "在哪里");
    private static final Pattern FROM_TO = Pattern.compile(
            "从(.{1,24}?)(?:到|去|前往)(.{1,24}?)(?:怎么走|的路线|$|[，。！？?])");
    private static final Pattern LEAD_TO = Pattern.compile(
            "(?:带我去|前往|步行到|去)(.{1,24}?)(?:吧|谢谢|怎么走|的路线|$|[，。！？?])");

    /**
     * Recognize one utterance. The context point is only used to fill an omitted
     * route start or to resolve a pronoun such as "那里".
     */
    public IntentResult recognize(String rawText, List<CampusPoint> points, Long contextPointId) {
        String text = normalize(rawText);
        if (text.isBlank()) {
            return IntentResult.unknown(text);
        }

        CampusPoint contextPoint = findById(points, contextPointId);
        boolean hasPronoun = text.matches(".*(那里|这儿|这儿|刚才那个|这个楼|那个楼|它).*");
        CampusPoint directPoint = findPoint(text, points);
        CampusPoint referencedPoint = directPoint != null ? directPoint : (hasPronoun ? contextPoint : null);

        IntentResult scene = recognizeScene(text, referencedPoint);
        if (scene != null) {
            return scene;
        }

        IntentResult route = recognizeRoute(text, points, contextPoint);
        if (route != null) {
            return route;
        }

        if (directPoint != null && containsAny(text, LOCATION_MARKERS)) {
            return new IntentResult(
                    IntentType.BUILDING_INTRO, 0.95, directPoint.getId(),
                    null, null, null, null, text);
        }

        if (containsAny(text, INFO_MARKERS)) {
            return new IntentResult(IntentType.INFO, 0.92, null, null, null, null, null, text);
        }

        if (referencedPoint != null && (containsAny(text, BUILDING_MARKERS) || hasPronoun || text.length() <= 15)) {
            return new IntentResult(
                    IntentType.BUILDING_INTRO, 0.94, referencedPoint.getId(),
                    null, null, null, null, text);
        }

        if (directPoint != null) {
            return new IntentResult(
                    IntentType.BUILDING_INTRO, 0.72, directPoint.getId(),
                    null, null, null, null, text);
        }

        if (hasPronoun) {
            return new IntentResult(
                    IntentType.CLARIFY, 0.45, null, null, null,
                    "ASK_POINT", null, text);
        }
        return IntentResult.unknown(text);
    }

    private IntentResult recognizeScene(String text, CampusPoint targetPoint) {
        if (containsAny(text, List.of("重置视角", "复位", "回到默认视角", "恢复默认"))) {
            return scene("RESET_VIEW", 0.99, targetPoint, text);
        }
        if (containsAny(text, List.of("清除路线", "取消导航", "关闭路线", "清掉路线"))) {
            return scene("CLEAR_ROUTE", 0.99, targetPoint, text);
        }
        if (containsAny(text, List.of("暂停游览", "暂停路线", "停一下"))) {
            return scene("PAUSE_TOUR", 0.97, targetPoint, text);
        }
        if (containsAny(text, List.of("继续游览", "继续路线", "恢复游览"))) {
            return scene("RESUME_TOUR", 0.97, targetPoint, text);
        }
        if (containsAny(text, List.of("切换视角", "切换机位", "看全景", "视角"))) {
            if (targetPoint != null) {
                return scene("FOCUS_POINT", 0.91, targetPoint, text);
            }
            return scene("OPEN_CAMERAS", 0.88, null, text);
        }
        if (containsAny(text, List.of("校园参观", "参观路线", "精品路线", "带我参观"))) {
            return new IntentResult(
                    IntentType.SCENE_CONTROL, 0.90, null, null, null,
                    "START_TOUR", "campus-tour", text);
        }
        return null;
    }

    private IntentResult recognizeRoute(String text, List<CampusPoint> points, CampusPoint contextPoint) {
        boolean routeLanguage = containsAny(text, ROUTE_MARKERS);
        if (!routeLanguage) {
            return null;
        }

        CampusPoint start = null;
        CampusPoint end = null;
        Matcher fromTo = FROM_TO.matcher(text);
        if (fromTo.find()) {
            start = findPoint(fromTo.group(1), points);
            end = findPoint(fromTo.group(2), points);
        }
        if (end == null) {
            Matcher leadTo = LEAD_TO.matcher(text);
            if (leadTo.find()) {
                end = findPoint(leadTo.group(1), points);
            }
        }
        if (end == null) {
            end = findPoint(text, points);
        }
        if (end == null && text.matches(".*(那里|刚才那个|这个楼|那个楼).*")) {
            end = contextPoint;
        }
        if (end == null) {
            return new IntentResult(
                    IntentType.CLARIFY, 0.62, null, null, null,
                    "ASK_DESTINATION", null, text);
        }
        if (start == null) {
            start = contextPoint;
        }
        return new IntentResult(
                IntentType.ROUTE, 0.96,
                null,
                start == null ? null : start.getId(),
                end.getId(),
                null, null, text);
    }

    private IntentResult scene(String action, double confidence, CampusPoint point, String text) {
        return new IntentResult(
                IntentType.SCENE_CONTROL, confidence,
                point == null ? null : point.getId(),
                null, null, action, null, text);
    }

    private CampusPoint findPoint(String text, List<CampusPoint> points) {
        if (text == null || text.isBlank()) {
            return null;
        }
        record Candidate(CampusPoint point, int length, int index) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (CampusPoint point : points) {
            for (String candidate : pointNames(point)) {
                int index = text.indexOf(candidate);
                if (index >= 0) {
                    candidates.add(new Candidate(point, candidate.length(), index));
                }
            }
        }
        return candidates.stream()
                .sorted(Comparator.comparingInt(Candidate::length).reversed()
                        .thenComparingInt(Candidate::index))
                .map(Candidate::point)
                .findFirst()
                .orElse(null);
    }

    private List<String> pointNames(CampusPoint point) {
        List<String> names = new ArrayList<>();
        names.add(point.getName());
        if (point.getAliases() != null) {
            for (String alias : point.getAliases().split("[,，、]")) {
                if (!alias.isBlank()) {
                    names.add(alias.trim());
                }
            }
        }
        return names;
    }

    private CampusPoint findById(List<CampusPoint> points, Long id) {
        if (id == null) {
            return null;
        }
        return points.stream().filter(point -> id.equals(point.getId())).findFirst().orElse(null);
    }

    private boolean containsAny(String text, List<String> markers) {
        return markers.stream().anyMatch(text::contains);
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}]+", "")
                .replace("。", "")
                .replace("？", "")
                .replace("！", "");
    }
}
