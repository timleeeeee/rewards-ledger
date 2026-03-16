package com.example.rewards.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final String appVersion;

    public HealthController(JdbcTemplate jdbcTemplate, @Value("${app.version}") String appVersion) {
        this.jdbcTemplate = jdbcTemplate;
        this.appVersion = appVersion;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        String dbStatus;
        try {
            Integer value = jdbcTemplate.queryForObject("select 1", Integer.class);
            dbStatus = value != null && value == 1 ? "UP" : "DOWN";
        } catch (Exception ex) {
            dbStatus = "DOWN";
        }

        String status = "UP".equals(dbStatus) ? "UP" : "DEGRADED";
        return new HealthResponse(status, appVersion, Map.of("database", dbStatus));
    }
}