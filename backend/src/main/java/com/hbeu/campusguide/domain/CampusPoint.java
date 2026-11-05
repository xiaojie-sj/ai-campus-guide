package com.hbeu.campusguide.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A public point of interest. Coordinates are scene units and only become
 * public after the point is published.
 */
@Data
@TableName("campus_point")
public class CampusPoint {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String aliases;
    private String category;
    private String description;
    private Double x;
    private Double z;
    private Double buildingHeight;
    private String colorHex;
    private String modelKey;
    private Long roadNodeId;
    private String status;
    private String verificationStatus;
    private String sourceTitle;
    private String sourceUrl;
    private Integer sortOrder;
    private LocalDateTime updatedAt;
}
