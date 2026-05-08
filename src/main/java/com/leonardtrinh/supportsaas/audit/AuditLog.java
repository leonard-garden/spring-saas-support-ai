package com.leonardtrinh.supportsaas.audit;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "member_id")
    private UUID memberId;

    @Column(name = "impersonator_id")
    private UUID impersonatorId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public UUID getMemberId() { return memberId; }
    public UUID getImpersonatorId() { return impersonatorId; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public Instant getCreatedAt() { return createdAt; }

    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public void setMemberId(UUID memberId) { this.memberId = memberId; }
    public void setImpersonatorId(UUID impersonatorId) { this.impersonatorId = impersonatorId; }
    public void setAction(String action) { this.action = action; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public void setResourceId(UUID resourceId) { this.resourceId = resourceId; }
}
