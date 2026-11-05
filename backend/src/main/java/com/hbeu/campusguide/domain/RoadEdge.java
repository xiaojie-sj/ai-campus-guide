package com.hbeu.campusguide.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("road_edge")
public class RoadEdge {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fromNodeId;
    private Long toNodeId;
    private Double distance;
    private Boolean walkable;
    private Boolean enabled;
}
