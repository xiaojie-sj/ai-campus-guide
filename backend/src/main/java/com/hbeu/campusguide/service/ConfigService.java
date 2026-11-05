package com.hbeu.campusguide.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hbeu.campusguide.common.BusinessException;
import com.hbeu.campusguide.common.ErrorCode;
import com.hbeu.campusguide.config.AdminContext;
import com.hbeu.campusguide.domain.SystemConfig;
import com.hbeu.campusguide.dto.SystemPublicConfig;
import com.hbeu.campusguide.mapper.SystemConfigMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates and exposes runtime settings used by the scene and chat editor.
 */
@Service
public class ConfigService {
    private final SystemConfigMapper mapper;
    private final AuditService auditService;

    public ConfigService(SystemConfigMapper mapper, AuditService auditService) {
        this.mapper = mapper;
        this.auditService = auditService;
    }

    public List<SystemConfig> list() {
        return mapper.selectList(Wrappers.<SystemConfig>lambdaQuery().orderByAsc(SystemConfig::getId));
    }

    public String get(String key, String fallback) {
        SystemConfig config = mapper.selectOne(
                Wrappers.<SystemConfig>lambdaQuery().eq(SystemConfig::getConfigKey, key));
        return config == null ? fallback : config.getConfigValue();
    }

    public double getDouble(String key, double fallback) {
        try {
            return Double.parseDouble(get(key, Double.toString(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        return Boolean.parseBoolean(get(key, Boolean.toString(fallback)));
    }

    public SystemPublicConfig publicConfig() {
        return new SystemPublicConfig(
                getDouble("avatar.speechRate", 1.0),
                getDouble("avatar.volume", 0.85),
                getDouble("scene.roamSpeed", 2.4),
                getDouble("camera.flightSeconds", 2.0),
                (long) getDouble("camera.highlightMs", 2400),
                getBoolean("avatar.autoSpeak", true),
                get("runtime.mode", "DEMO"),
                get("runtime.notice", "当前为项目演示数据，未经校方审核的点位不会作为正式校园事实发布。")
        );
    }

    public Map<String, String> publicMap() {
        SystemPublicConfig config = publicConfig();
        Map<String, String> map = new LinkedHashMap<>();
        map.put("avatar.speechRate", Double.toString(config.avatarSpeechRate()));
        map.put("avatar.volume", Double.toString(config.avatarVolume()));
        map.put("scene.roamSpeed", Double.toString(config.roamSpeed()));
        map.put("camera.flightSeconds", Double.toString(config.cameraFlightSeconds()));
        map.put("camera.highlightMs", Long.toString(config.highlightMs()));
        map.put("avatar.autoSpeak", Boolean.toString(config.autoSpeak()));
        map.put("runtime.mode", config.mode());
        map.put("runtime.notice", config.notice());
        return map;
    }

    public SystemConfig update(String key, String value) {
        SystemConfig config = mapper.selectOne(
                Wrappers.<SystemConfig>lambdaQuery().eq(SystemConfig::getConfigKey, key));
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "参数不存在");
        }
        validate(config, value);
        String oldValue = config.getConfigValue();
        config.setConfigValue(value);
        config.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(config);
        auditService.record(AdminContext.current().userId(), "CONFIG_UPDATE", "SYSTEM_CONFIG",
                key, "旧值=" + oldValue + "; 新值=" + value);
        return config;
    }

    private void validate(SystemConfig config, String value) {
        if ("NUMBER".equals(config.getValueType())) {
            double parsed;
            try {
                parsed = Double.parseDouble(value);
            } catch (NumberFormatException exception) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "参数必须是数字");
            }
            if (config.getMinValue() != null && parsed < config.getMinValue()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "参数小于允许的最小值");
            }
            if (config.getMaxValue() != null && parsed > config.getMaxValue()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "参数大于允许的最大值");
            }
        }
        if ("BOOLEAN".equals(config.getValueType())
                && !List.of("true", "false").contains(value.toLowerCase())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "布尔参数只能是 true 或 false");
        }
    }
}
