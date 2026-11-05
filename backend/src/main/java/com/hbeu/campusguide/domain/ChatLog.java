package com.hbeu.campusguide.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_log")
public class ChatLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private String message;
    private String inputType;
    private String intent;
    private Double confidence;
    private Long pointId;
    private String answerStatus;
    private String answer;
    private String sourcesJson;
    private Long latencyMs;
    private String feedback;
    private LocalDateTime createdAt;
}
