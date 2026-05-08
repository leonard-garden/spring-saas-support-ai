package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.audit.AuditLog;
import com.leonardtrinh.supportsaas.audit.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogger(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async("taskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLoginAsync(UUID memberId, UUID businessId) {
        try {
            AuditLog entry = new AuditLog();
            entry.setBusinessId(businessId);
            entry.setMemberId(memberId);
            entry.setAction("LOGIN");
            auditLogRepository.save(entry);
        } catch (Exception ex) {
            log.warn("audit_log_failure action=LOGIN member={} business={} error={}", memberId, businessId, ex.getMessage());
        }
    }
}
