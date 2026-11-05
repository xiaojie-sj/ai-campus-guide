package com.hbeu.campusguide.service;

import com.hbeu.campusguide.domain.AuditLog;
import com.hbeu.campusguide.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Writes security-relevant administrator actions.
 */
@Service
public class AuditService {
    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void record(Long adminUserId, String action, String targetType, String targetId, String detail) {
        AuditLog log = new AuditLog();
        log.setAdminUserId(adminUserId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }
}
