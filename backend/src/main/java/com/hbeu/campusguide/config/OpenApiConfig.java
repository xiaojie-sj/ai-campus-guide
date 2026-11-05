package com.hbeu.campusguide.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * OpenAPI metadata, API groups and Chinese operation summaries for Knife4j.
 */
@Configuration
public class OpenApiConfig {
    public static final String BEARER_AUTH = "bearerAuth";

    private static final Map<String, String> OPERATION_SUMMARIES = Map.ofEntries(
            Map.entry("PublicController#bootstrap", "获取前端启动数据"),
            Map.entry("PublicController#points", "获取公开点位"),
            Map.entry("PublicController#tours", "获取公开精品路线"),
            Map.entry("PublicController#chat", "数字人问答与导览指令"),
            Map.entry("PublicController#feedback", "提交回答反馈"),
            Map.entry("PublicController#transcribe", "语音识别（ASR）"),
            Map.entry("PublicController#synthesize", "语音合成（TTS）"),

            Map.entry("AuthController#login", "管理端登录并获取 JWT"),

            Map.entry("AdminContentController#points", "查询管理端点位"),
            Map.entry("AdminContentController#createPoint", "新建点位"),
            Map.entry("AdminContentController#updatePoint", "更新点位"),
            Map.entry("AdminContentController#deletePoint", "删除点位"),
            Map.entry("AdminContentController#roads", "查询路网节点与道路"),
            Map.entry("AdminContentController#createNode", "新建路网节点"),
            Map.entry("AdminContentController#deleteNode", "删除路网节点"),
            Map.entry("AdminContentController#createEdge", "新建道路"),
            Map.entry("AdminContentController#deleteEdge", "删除道路"),
            Map.entry("AdminContentController#cameras", "查询机位"),
            Map.entry("AdminContentController#createCamera", "新建机位"),
            Map.entry("AdminContentController#updateCamera", "更新机位"),
            Map.entry("AdminContentController#tours", "查询全部精品路线"),
            Map.entry("AdminContentController#stops", "查询路线站点"),
            Map.entry("AdminContentController#createTour", "新建精品路线"),
            Map.entry("AdminContentController#updateTour", "更新精品路线"),
            Map.entry("AdminContentController#faqs", "查询 FAQ"),
            Map.entry("AdminContentController#createFaq", "新建 FAQ"),
            Map.entry("AdminContentController#updateFaq", "更新 FAQ"),
            Map.entry("AdminContentController#deleteFaq", "删除 FAQ"),
            Map.entry("AdminContentController#knowledge", "查询知识库文档"),
            Map.entry("AdminContentController#uploadKnowledge", "上传知识库文档"),
            Map.entry("AdminContentController#publishKnowledge", "发布知识库文档"),
            Map.entry("AdminContentController#reindexKnowledge", "重建知识库索引"),
            Map.entry("AdminContentController#deleteKnowledge", "删除知识库文档"),
            Map.entry("AdminContentController#configs", "查询系统参数"),
            Map.entry("AdminContentController#updateConfig", "更新系统参数"),

            Map.entry("AdminAnalyticsController#chatLogs", "查询问答日志"),
            Map.entry("AdminAnalyticsController#stats", "查询运营统计"),
            Map.entry("AdminAnalyticsController#auditLogs", "查询审计日志")
    );

    @Bean
    public OpenAPI campusGuideOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("湖北工程学院新技术学院 AI 数字人校园导览 API")
                        .version("1.0.0")
                        .description("""
                                三维校园导览后端接口，包含公开导览、数字人问答、知识库 RAG、路径规划和管理端能力。

                                - 公开接口无需认证。
                                - 管理接口需要 JWT：先调用 POST /api/auth/login，再点击右上角 Authorize 填入 token。
                                - 演示账号：admin / editor / reviewer / analyst，密码均为 Demo@123。
                                """))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("管理端 JWT。填写登录接口返回的 token 即可，无需手动添加 Bearer 前缀。")));
    }

    @Bean
    public GroupedOpenApi publicApiGroup() {
        return GroupedOpenApi.builder()
                .group("public")
                .displayName("公开接口")
                .pathsToMatch("/api/public/**", "/api/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminApiGroup() {
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("管理接口")
                .pathsToMatch("/api/admin/**")
                .build();
    }

    @Bean
    public OperationCustomizer operationCustomizer() {
        return (operation, handlerMethod) -> {
            String key = handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
            String summary = OPERATION_SUMMARIES.get(key);
            if (summary != null) {
                operation.setSummary(summary);
            }
            return operation;
        };
    }
}
