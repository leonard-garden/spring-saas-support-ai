package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.audit.AuditAction;
import com.leonardtrinh.supportsaas.audit.AuditLog;
import com.leonardtrinh.supportsaas.audit.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class AuditLogger {

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
            entry.setAction(AuditAction.LOGIN);
            auditLogRepository.save(entry);
        } catch (Exception ex) {
            log.warn("audit_log_failure action=LOGIN member={} business={} error={}", memberId, businessId, ex.getMessage());
        }
    }
}
