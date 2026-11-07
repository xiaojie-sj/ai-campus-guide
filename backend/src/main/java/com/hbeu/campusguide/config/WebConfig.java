package com.hbeu.campusguide.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures admin JWT enforcement and local development CORS.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AdminAuthInterceptor authInterceptor;
    private final AppProperties properties;

    public WebConfig(AdminAuthInterceptor authInterceptor, AppProperties properties) {
        this.authInterceptor = authInterceptor;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/admin/**", "/api/auth/me", "/api/auth/logout");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = properties.cors() == null || properties.cors().allowedOrigins() == null
                ? new String[0]
                : properties.cors().allowedOrigins().toArray(String[]::new);
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Trace-Id", "X-Requested-With")
                .exposedHeaders("X-Trace-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

