package com.leonardtrinh.supportsaas.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("vectorStore")
public class VectorStoreHealthIndicator implements HealthIndicator {

    private static final String PGVECTOR_PING_SQL =
            "SELECT extversion FROM pg_extension WHERE extname = 'vector'";

    private final JdbcTemplate jdbcTemplate;

    public VectorStoreHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            String version = jdbcTemplate.queryForObject(PGVECTOR_PING_SQL, String.class);
            if (version != null) {
                return Health.up()
                        .withDetail("extension", "vector")
                        .withDetail("version", version)
                        .build();
            } else {
                return Health.down()
                        .withDetail("reason", "pgvector extension not installed")
                        .build();
            }
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("reason", "failed to query pgvector extension")
                    .build();
        }
    }
}
