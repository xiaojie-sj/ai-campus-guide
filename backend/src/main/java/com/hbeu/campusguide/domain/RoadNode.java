package com.hbeu.campusguide.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("road_node")
public class RoadNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Double x;
    private Double z;
    private Boolean enabled;
}
