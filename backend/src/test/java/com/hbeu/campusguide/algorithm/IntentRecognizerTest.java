package com.hbeu.campusguide.algorithm;

import com.hbeu.campusguide.domain.CampusPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntentRecognizerTest {
    private IntentRecognizer recognizer;
    private List<CampusPoint> points;
    private CampusPoint gate;
    private CampusPoint library;

    @BeforeEach
    void setUp() {
        recognizer = new IntentRecognizer();
        gate = point(1L, "校门", "大门,入口");
        library = point(2L, "图书馆", "图书楼");
        points = List.of(gate, library);
    }

    @Test
    void recognizesBuildingIntroduction() {
        IntentResult result = recognizer.recognize("请介绍一下图书馆", points, null);

        assertThat(result.type()).isEqualTo(IntentType.BUILDING_INTRO);
        assertThat(result.pointId()).isEqualTo(2L);
        assertThat(result.confidence()).isGreaterThan(0.9);
    }

    @Test
    void recognizesRouteWithStartAndEnd() {
        IntentResult result = recognizer.recognize("从校门到图书馆怎么走", points, null);

        assertThat(result.type()).isEqualTo(IntentType.ROUTE);
        assertThat(result.startPointId()).isEqualTo(1L);
        assertThat(result.endPointId()).isEqualTo(2L);
    }

    @Test
    void resolvesPronounToConversationContext() {
        IntentResult result = recognizer.recognize("带我去那里", points, 2L);

        assertThat(result.type()).isEqualTo(IntentType.ROUTE);
        assertThat(result.endPointId()).isEqualTo(2L);
    }

    @Test
    void recognizesInformationQuestionBeforeGenericBuildingIntent() {
        IntentResult result = recognizer.recognize("图书馆几点开门", points, null);

        assertThat(result.type()).isEqualTo(IntentType.INFO);
    }

    @Test
    void recognizesSchoolOverviewAsKnowledgeQuestion() {
        IntentResult result = recognizer.recognize(
                "湖北工程学院新技术学院是什么类型的学校", points, null);

        assertThat(result.type()).isEqualTo(IntentType.INFO);
    }

    @Test
    void recognizesPointLocationQuestionForPointProfile() {
        IntentResult result = recognizer.recognize("学校大门在哪里？", points, null);

        assertThat(result.type()).isEqualTo(IntentType.BUILDING_INTRO);
        assertThat(result.pointId()).isEqualTo(1L);
    }

    @Test
    void recognizesCameraControl() {
        IntentResult result = recognizer.recognize("切换到图书馆视角", points, null);

        assertThat(result.type()).isEqualTo(IntentType.SCENE_CONTROL);
        assertThat(result.sceneAction()).isEqualTo("FOCUS_POINT");
        assertThat(result.pointId()).isEqualTo(2L);
    }

    @Test
    void rejectsOutOfScopeQuestion() {
        IntentResult result = recognizer.recognize("推荐一家校外的火锅店", points, null);

        assertThat(result.type()).isEqualTo(IntentType.UNKNOWN);
    }

    private CampusPoint point(long id, String name, String aliases) {
        CampusPoint point = new CampusPoint();
        point.setId(id);
        point.setName(name);
        point.setAliases(aliases);
        point.setStatus("PUBLISHED");
        return point;
    }
}
