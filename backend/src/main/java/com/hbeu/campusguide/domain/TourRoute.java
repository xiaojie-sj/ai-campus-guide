package com.hbeu.campusguide.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("tour_route")
public class TourRoute {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String routeKey;
    private String name;
    private String summary;
    private String audience;
    private Integer estimatedMinutes;
    private String status;
    private String verificationStatus;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Integer sortOrder;
}
