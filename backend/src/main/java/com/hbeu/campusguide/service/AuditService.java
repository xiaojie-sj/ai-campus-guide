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
        log.setAction(truncate(action, 80));
        log.setTargetType(truncate(targetType, 80));
        log.setTargetId(truncate(targetId, 100));
        log.setDetail(truncate(detail, 2000));
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
