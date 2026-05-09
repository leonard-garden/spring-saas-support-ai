package com.leonardtrinh.supportsaas.billing;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "price_usd_monthly", nullable = false)
    private BigDecimal priceUsdMonthly;

    @Column(name = "stripe_price_id")
    private String stripePriceId;

    @Column(name = "max_knowledge_bases", nullable = false)
    private int maxKnowledgeBases;

    @Column(name = "max_documents_per_kb", nullable = false)
    private int maxDocumentsPerKb;

    @Column(name = "max_messages_per_month", nullable = false)
    private int maxMessagesPerMonth;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public BigDecimal getPriceUsdMonthly() { return priceUsdMonthly; }
    public String getStripePriceId() { return stripePriceId; }
    public int getMaxKnowledgeBases() { return maxKnowledgeBases; }
    public int getMaxDocumentsPerKb() { return maxDocumentsPerKb; }
    public int getMaxMessagesPerMonth() { return maxMessagesPerMonth; }
    public int getMaxMembers() { return maxMembers; }
    public boolean isActive() { return isActive; }
}
