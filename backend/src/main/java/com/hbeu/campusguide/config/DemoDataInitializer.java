package com.hbeu.campusguide.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hbeu.campusguide.domain.AdminUser;
import com.hbeu.campusguide.domain.CampusPoint;
import com.hbeu.campusguide.domain.ChatLog;
import com.hbeu.campusguide.mapper.AdminUserMapper;
import com.hbeu.campusguide.mapper.CampusPointMapper;
import com.hbeu.campusguide.mapper.ChatLogMapper;
import com.hbeu.campusguide.service.AdminAuthService;
import com.hbeu.campusguide.service.KnowledgeIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Creates local demo accounts, the bundled public-fact knowledge set and a
 * deterministic analytics sample. This runner is inert in production mode so
 * a known demo password or synthetic operations data is never seeded.
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "demo-data", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final int DEMO_CHAT_THRESHOLD = 320;
    private static final int[] INTENT_WEIGHTS = {34, 26, 17, 8, 7, 8};
    private static final String[] INTENTS = {"INFO", "BUILDING_INTRO", "ROUTE", "SCENE_CONTROL", "CLARIFY", "UNKNOWN"};
    private static final int[] HOUR_WEIGHTS = {1, 1, 1, 1, 1, 2, 3, 7, 16, 24, 22, 14, 20, 24, 19, 15, 16, 22, 27, 23, 15, 9, 5, 2};
    private static final int[] POINT_WEIGHTS = {24, 19, 15, 12, 9, 7, 5, 4, 3, 2};
    private static final List<String> BUILDING_QUESTIONS = List.of("介绍一下%s", "%s在哪里", "%s有什么特色", "%s怎么走");
    private static final List<String> ROUTE_QUESTIONS = List.of("带我去%s", "从校门到%s怎么走", "%s怎么走", "导航到%s");
    private static final List<String> INFO_QUESTIONS = List.of("图书馆开放时间", "食堂营业到几点", "校车时刻表", "体育馆怎么预约", "教学楼自习室开放吗", "校园网怎么连接");
    private static final List<String> SCENE_QUESTIONS = List.of("重置视角", "清除路线", "暂停游览", "回到默认视角");
    private static final List<String> CLARIFY_QUESTIONS = List.of("带我去那里", "它开放到几点", "这个怎么走", "再介绍一下");
    private static final List<String> OUT_OF_SCOPE_QUESTIONS = List.of("学校附近有什么好吃的？", "能帮我订一张回家的车票吗？", "明天会下雨吗？", "帮我写一份课程论文");
    private static final List<String> NO_EVIDENCE_QUESTIONS = List.of("图书馆可以借笔记本电脑吗？", "实验楼晚上几点关门？", "校车最晚一班几点？", "体育馆泳池收费标准？", "校长办公室电话是多少？");

    private final AdminUserMapper adminUserMapper;
    private final AdminAuthService authService;
    private final KnowledgeIngestionService knowledgeService;
    private final SecurityProperties securityProperties;
    private final ChatLogMapper chatLogMapper;
    private final CampusPointMapper campusPointMapper;

    public DemoDataInitializer(
            AdminUserMapper adminUserMapper,
            AdminAuthService authService,
            KnowledgeIngestionService knowledgeService,
            SecurityProperties securityProperties,
            ChatLogMapper chatLogMapper,
            CampusPointMapper campusPointMapper
    ) {
        this.adminUserMapper = adminUserMapper;
        this.authService = authService;
        this.knowledgeService = knowledgeService;
        this.securityProperties = securityProperties;
        this.chatLogMapper = chatLogMapper;
        this.campusPointMapper = campusPointMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!securityProperties.demoMode()) {
            log.warn("APP_DEMO_MODE=false: demo accounts, demo knowledge and analytics seeding are disabled.");
            return;
        }
        log.warn("Demo mode is ON. Known demo accounts are available; set APP_DEMO_MODE=false in production.");
        seedUser("admin", "系统管理员", "SYSTEM_ADMIN");
        seedUser("editor", "内容管理员", "CONTENT_ADMIN");
        seedUser("reviewer", "内容审核员", "REVIEWER");
        seedUser("analyst", "数据分析员", "DATA_ANALYST");
        knowledgeService.seedDemoKnowledge();
        seedAnalytics();
    }

    private void seedUser(String username, String displayName, String role) {
        long count = adminUserMapper.selectCount(
                Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getUsername, username));
        if (count > 0) {
            return;
        }
        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(authService.passwordEncoder().encode("Demo@123"));
        user.setRole(role);
        user.setEnabled(true);
        user.setTokenVersion(0L);
        user.setCreatedAt(LocalDateTime.now());
        adminUserMapper.insert(user);
    }

    /**
     * Adds a deterministic 14-day analytics sample when the local database has
     * little or no conversation history. The guard prevents duplicate rows on
     * every restart and leaves any non-demo deployment untouched.
     */
    private void seedAnalytics() {
        long existing = chatLogMapper.selectCount(null);
        if (existing >= DEMO_CHAT_THRESHOLD) {
            return;
        }
        List<CampusPoint> points = campusPointMapper.selectList(
                Wrappers.<CampusPoint>lambdaQuery()
                        .eq(CampusPoint::getStatus, "PUBLISHED")
                        .orderByAsc(CampusPoint::getSortOrder));
        if (points.isEmpty()) {
            points = campusPointMapper.selectList(null);
        }
        if (points.isEmpty()) {
            log.info("Analytics seed skipped because no campus points exist yet.");
            return;
        }

        Random random = new Random(20261107L);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        int inserted = 0;
        for (int dayIndex = 0; dayIndex < 14; dayIndex++) {
            LocalDate day = LocalDate.now().minusDays(13L - dayIndex);
            boolean weekend = day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
            int daily = 62 + dayIndex * 3 + random.nextInt(18) + (weekend ? -11 : 4);
            for (int index = 0; index < daily; index++) {
                String intent = INTENTS[weightedIndex(random, INTENT_WEIGHTS)];
                int hour = weightedIndex(random, HOUR_WEIGHTS);
                LocalDateTime createdAt = day.atTime(hour, random.nextInt(60), random.nextInt(60));
                if (!createdAt.isBefore(now)) {
                    createdAt = now.minusMinutes(4L + random.nextInt(26));
                }
                CampusPoint point = points.get(weightedIndex(random, pointWeights(points.size())));
                String answerStatus = statusFor(intent, random);
                String message = messageFor(intent, answerStatus, point.getName(), random);

                ChatLog chat = new ChatLog();
                chat.setSessionId("demo-" + day + "-" + (index / 3));
                chat.setMessage(message);
                chat.setInputType(random.nextInt(100) < 82 ? "TEXT" : "VOICE");
                chat.setIntent(intent);
                chat.setConfidence(0.55 + random.nextInt(42) / 100.0);
                chat.setPointId("UNKNOWN".equals(intent) || "CLARIFY".equals(intent) || "SCENE_CONTROL".equals(intent)
                        ? null : point.getId());
                chat.setAnswerStatus(answerStatus);
                chat.setAnswer(answerFor(intent, answerStatus, point.getName()));
                chat.setSourcesJson("[]");
                chat.setLatencyMs(48L + random.nextInt(186) + ("INFO".equals(intent) ? 22 : 0));
                int feedbackRoll = random.nextInt(100);
                chat.setFeedback(feedbackRoll < 9 ? "HELPFUL" : feedbackRoll < 12 ? "NOT_HELPFUL" : null);
                chat.setCreatedAt(createdAt);
                chatLogMapper.insert(chat);
                inserted += 1;
            }
        }
        log.info("Seeded {} deterministic demo conversation logs across the last 14 days.", inserted);
    }

    private static int[] pointWeights(int size) {
        int[] weights = new int[size];
        for (int index = 0; index < size; index++) {
            weights[index] = POINT_WEIGHTS[Math.min(index, POINT_WEIGHTS.length - 1)];
        }
        return weights;
    }

    private static String statusFor(String intent, Random random) {
        return switch (intent) {
            case "BUILDING_INTRO" -> "POINT_PROFILE";
            case "ROUTE" -> "ROUTE_READY";
            case "SCENE_CONTROL" -> "SCENE_ACTION";
            case "CLARIFY" -> "CLARIFY";
            case "UNKNOWN" -> "OUT_OF_SCOPE";
            default -> random.nextInt(100) < 12 ? "NO_EVIDENCE" : "ANSWERED";
        };
    }

    private static String messageFor(String intent, String status, String pointName, Random random) {
        if ("NO_EVIDENCE".equals(status)) {
            return NO_EVIDENCE_QUESTIONS.get(random.nextInt(NO_EVIDENCE_QUESTIONS.size()));
        }
        if ("OUT_OF_SCOPE".equals(status)) {
            return OUT_OF_SCOPE_QUESTIONS.get(random.nextInt(OUT_OF_SCOPE_QUESTIONS.size()));
        }
        List<String> templates = switch (intent) {
            case "BUILDING_INTRO" -> BUILDING_QUESTIONS;
            case "ROUTE" -> ROUTE_QUESTIONS;
            case "INFO" -> INFO_QUESTIONS;
            case "SCENE_CONTROL" -> SCENE_QUESTIONS;
            case "CLARIFY" -> CLARIFY_QUESTIONS;
            default -> INFO_QUESTIONS;
        };
        String template = templates.get(random.nextInt(templates.size()));
        return template.contains("%s") ? String.format(template, pointName) : template;
    }

    private static String answerFor(String intent, String status, String pointName) {
        if ("NO_EVIDENCE".equals(status)) {
            return "当前知识库没有检索到可引用的校内资料，建议补充该主题的审核文档。";
        }
        if ("OUT_OF_SCOPE".equals(status)) {
            return "这个问题超出校园导览服务范围，我目前只处理校内建筑、路线与已审核资料查询。";
        }
        return switch (intent) {
            case "BUILDING_INTRO" -> "这里是" + pointName + "，已为你切换到该点位的三维导览视角。";
            case "ROUTE" -> "已为你规划前往" + pointName + "的步行路线，可在三维地图中查看。";
            case "SCENE_CONTROL" -> "已执行场景控制指令，三维视角同步更新。";
            case "CLARIFY" -> "请告诉我要前往的具体点位，例如“带我去图书馆”。";
            default -> "已从校内审核资料中整理" + pointName + "的相关信息。";
        };
    }

    private static int weightedIndex(Random random, int[] weights) {
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        int pick = random.nextInt(total);
        for (int index = 0; index < weights.length; index++) {
            pick -= weights[index];
            if (pick < 0) {
                return index;
            }
        }
        return weights.length - 1;
    }
}
