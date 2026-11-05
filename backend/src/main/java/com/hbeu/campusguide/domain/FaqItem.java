package com.hbeu.campusguide.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("faq_item")
public class FaqItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String question;
    private String aliases;
    private String answer;
    private String category;
    private String sourceTitle;
    private String sourceUrl;
    private Integer priority;
    private String status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private LocalDateTime updatedAt;
}
