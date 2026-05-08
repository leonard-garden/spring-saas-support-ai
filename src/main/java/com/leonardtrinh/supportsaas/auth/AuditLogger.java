package com.leonardtrinh.supportsaas.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private final JdbcTemplate jdbcTemplate;

    public AuditLogger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Async("taskExecutor")
    public void logLoginAsync(UUID memberId, UUID businessId) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO audit_logs (id, business_id, member_id, action, created_at) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(), businessId, memberId, "LOGIN", Timestamp.from(Instant.now()));
        } catch (Exception ex) {
            log.warn("audit_log_failure action=LOGIN member={} business={} error={}", memberId, businessId, ex.getMessage());
        }
    }
}
