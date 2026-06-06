package com.leonardtrinh.supportsaas.config;

import jakarta.persistence.EntityManager;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("vectorStore")
public class VectorStoreHealthIndicator implements HealthIndicator {

    private static final String PGVECTOR_PING_SQL =
            "SELECT extversion FROM pg_extension WHERE extname = 'vector'";

    private final EntityManager entityManager;

    public VectorStoreHealthIndicator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Health health() {
        try {
            String version = (String) entityManager
                    .createNativeQuery(PGVECTOR_PING_SQL)
                    .getSingleResult();
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
