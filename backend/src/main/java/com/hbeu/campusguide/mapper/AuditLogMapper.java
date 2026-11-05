package com.hbeu.campusguide.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbeu.campusguide.domain.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
