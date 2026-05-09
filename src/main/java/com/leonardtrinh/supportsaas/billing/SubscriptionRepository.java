package com.leonardtrinh.supportsaas.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    // Native query — bypasses tenant filter for use in contexts without TenantContext
    @Query(value = "SELECT * FROM subscriptions WHERE business_id = :businessId AND status IN ('ACTIVE', 'TRIALING') ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    Optional<Subscription> findActiveByBusinessId(@Param("businessId") UUID businessId);
}
