package com.hbeu.campusguide.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_document")
public class KnowledgeDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String originalFilename;
    private String storagePath;
    private String sourceTitle;
    private String sourceUrl;
    private String effectiveYear;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String category;
    private String status;
    private String parseStatus;
    private Integer chunkCount;
    private String indexVersion;
    private String reviewer;
    private String checksum;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
