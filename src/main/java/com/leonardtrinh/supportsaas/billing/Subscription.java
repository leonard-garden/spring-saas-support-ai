package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.common.TenantEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public UUID getPlanId() { return planId; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getTrialEndsAt() { return trialEndsAt; }
    public Instant getCurrentPeriodStart() { return currentPeriodStart; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public Instant getCreatedAt() { return createdAt; }

    public void setPlanId(UUID planId) { this.planId = planId; }
    public void setStatus(SubscriptionStatus status) { this.status = status; }
    public void setTrialEndsAt(Instant trialEndsAt) { this.trialEndsAt = trialEndsAt; }
    public void setCurrentPeriodStart(Instant currentPeriodStart) { this.currentPeriodStart = currentPeriodStart; }
    public void setCurrentPeriodEnd(Instant currentPeriodEnd) { this.currentPeriodEnd = currentPeriodEnd; }
    public void setStripeSubscriptionId(String stripeSubscriptionId) { this.stripeSubscriptionId = stripeSubscriptionId; }

    @Override
    public void setBusinessId(UUID businessId) {
        super.setBusinessId(businessId);
    }
}
