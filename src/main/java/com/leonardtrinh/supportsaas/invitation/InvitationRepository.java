package com.leonardtrinh.supportsaas.invitation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    // Native query — bypasses Hibernate filter (needed when TenantContext not set at accept time)
    @Query(value = "SELECT * FROM invitations WHERE token_hash = :tokenHash LIMIT 1", nativeQuery = true)
    Optional<Invitation> findByTokenHash(@Param("tokenHash") String tokenHash);

    // Native query — check pending invitation for this email in this tenant
    @Query(value = "SELECT COUNT(*) > 0 FROM invitations WHERE business_id = :businessId AND email = :email AND accepted_at IS NULL AND expires_at > now()", nativeQuery = true)
    boolean existsPendingByBusinessIdAndEmail(@Param("businessId") UUID businessId, @Param("email") String email);

    // JPQL — Hibernate tenant filter auto-applies WHERE business_id = :tenantId
    @Query("SELECT i FROM Invitation i WHERE i.acceptedAt IS NULL AND i.expiresAt > :now")
    Page<Invitation> findAllPending(@Param("now") Instant now, Pageable pageable);
}
