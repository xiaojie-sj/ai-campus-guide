package com.hbeu.campusguide.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tour_stop")
public class TourStop {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long routeId;
    private Long pointId;
    private Integer sortOrder;
    private String narration;
    private String transitionType;
}
