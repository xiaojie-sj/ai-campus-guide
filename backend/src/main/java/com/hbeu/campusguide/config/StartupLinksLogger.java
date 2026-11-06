package com.hbeu.campusguide.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Prints the most useful local URLs once the application is ready, so the
 * startup console doubles as a quick reference during development.
 */
@Component
public class StartupLinksLogger {
    private static final Logger log = LoggerFactory.getLogger(StartupLinksLogger.class);

    private final Environment environment;
    private final DatabaseCapabilities databaseCapabilities;

    public StartupLinksLogger(Environment environment, DatabaseCapabilities databaseCapabilities) {
        this.environment = environment;
        this.databaseCapabilities = databaseCapabilities;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void printStartupLinks() {
        String port = environment.getProperty("local.server.port", "8080");
        String base = "http://127.0.0.1:" + port;
        String frontend = environment.getProperty("app.links.frontend", "http://127.0.0.1:5173");
        boolean docsEnabled = Boolean.parseBoolean(
                environment.getProperty("springdoc.api-docs.enabled", "true"));
        String docsState = docsEnabled ? "" : "disabled (KNIFE4J_ENABLED=false)";

        log.info("""

                ------------------------------------------------------------------
                  Campus Guide backend is ready
                  API base        : {}
                  Knife4j docs    : {}
                  Swagger UI      : {}
                  OpenAPI JSON    : {}
                  Health check    : {}/actuator/health
                  Public site     : {}/
                  Admin console   : {}/admin/login
                  Database        : {} (postgis={}, pgrouting={})
                ------------------------------------------------------------------""",
                base,
                docsEnabled ? base + "/doc.html" : docsState,
                docsEnabled ? base + "/swagger-ui.html" : docsState,
                docsEnabled ? base + "/v3/api-docs" : docsState,
                base,
                frontend,
                frontend,
                environment.getProperty("spring.datasource.url", ""),
                databaseCapabilities.postgis(),
                databaseCapabilities.pgRouting());
    }
}