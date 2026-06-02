package com.leonardtrinh.supportsaas.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorStoreHealthIndicatorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private VectorStoreHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new VectorStoreHealthIndicator(jdbcTemplate);
    }

    @Test
    @DisplayName("health returns UP when pgvector extension is installed")
    void health_pgvectorInstalled_returnsUp() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(String.class)))
                .thenReturn("0.7.0");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("extension", "vector");
        assertThat(health.getDetails()).containsEntry("version", "0.7.0");
    }

    @Test
    @DisplayName("health returns DOWN when pgvector extension is not installed")
    void health_pgvectorMissing_returnsDown() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(String.class)))
                .thenReturn(null);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("reason");
    }

    @Test
    @DisplayName("health returns DOWN when database is unreachable")
    void health_databaseUnreachable_returnsDown() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(String.class)))
                .thenThrow(new RuntimeException("connection refused"));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }
}
