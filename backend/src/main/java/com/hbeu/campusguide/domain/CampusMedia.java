package com.hbeu.campusguide.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Approved media attached to a published campus point.
 */
@Data
@TableName("campus_media")
public class CampusMedia {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pointId;
    private String title;
    private String mediaType;
    private String fileUrl;
    private String altText;
    private String sourceTitle;
    private String sourceUrl;
    private String status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
