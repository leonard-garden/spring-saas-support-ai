package com.leonardtrinh.supportsaas.tenant;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "businesses")
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public UUID getPlanId() { return planId; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public Instant getSuspendedAt() { return suspendedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setSlug(String slug) { this.slug = slug; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }
    public void setSuspendedAt(Instant suspendedAt) { this.suspendedAt = suspendedAt; }
}
