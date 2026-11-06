package com.hbeu.campusguide.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Probes optional PostgreSQL extensions once at startup so feature gates
 * reflect the real database instead of a Spring profile name.
 */
@Component
public class DatabaseCapabilities {
    private static final Logger log = LoggerFactory.getLogger(DatabaseCapabilities.class);
    private final boolean postgis;
    private final boolean pgRouting;

    public DatabaseCapabilities(JdbcTemplate jdbcTemplate) {
        this.postgis = probe(jdbcTemplate, "SELECT postgis_version()");
        this.pgRouting = probe(jdbcTemplate, "SELECT pgr_version()");
        log.info("Database capabilities detected: postgis={}, pgrouting={}", postgis, pgRouting);
    }

    private static boolean probe(JdbcTemplate jdbcTemplate, String sql) {
        try {
            jdbcTemplate.queryForObject(sql, String.class);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean postgis() {
        return postgis;
    }

    public boolean pgRouting() {
        return pgRouting;
    }
}