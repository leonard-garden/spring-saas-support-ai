package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.common.BaseIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessedWebhookEventRepositoryIT extends BaseIT {

    @Autowired
    private ProcessedWebhookEventRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE processed_webhook_events");
    }

    @Test
    @DisplayName("existsByStripeEventId returns false for an unknown event ID")
    void existsByStripeEventId_unknownId_returnsFalse() {
        assertThat(repository.existsByStripeEventId("evt_unknown")).isFalse();
    }

    @Test
    @DisplayName("save then existsByStripeEventId returns true for the saved event")
    void save_thenExistsByStripeEventId_returnsTrue() {
        repository.save(new ProcessedWebhookEvent("evt_1", "invoice.paid"));

        assertThat(repository.existsByStripeEventId("evt_1")).isTrue();
    }

    @Test
    @DisplayName("idempotency guard skips duplicate save — only one row persisted")
    void checkThenSave_idempotencyGuard_skipsDuplicateSave() {
        repository.save(new ProcessedWebhookEvent("evt_2", "invoice.paid"));

        boolean exists = repository.existsByStripeEventId("evt_2");

        if (!exists) {
            repository.save(new ProcessedWebhookEvent("evt_2", "invoice.paid"));
        }

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_webhook_events WHERE stripe_event_id = 'evt_2'",
                Long.class);
        assertThat(count).isEqualTo(1L);
    }
}
