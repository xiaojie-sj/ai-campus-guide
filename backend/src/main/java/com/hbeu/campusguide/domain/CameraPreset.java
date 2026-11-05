package com.hbeu.campusguide.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("camera_preset")
public class CameraPreset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String presetKey;
    private String name;
    private Long pointId;
    private Double x;
    private Double y;
    private Double z;
    private Double targetX;
    private Double targetY;
    private Double targetZ;
    private Boolean publicVisible;
    private String status;
    private Integer sortOrder;
}
