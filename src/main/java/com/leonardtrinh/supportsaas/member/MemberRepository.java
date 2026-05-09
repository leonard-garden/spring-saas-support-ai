package com.leonardtrinh.supportsaas.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {

    // Native queries bypass the Hibernate tenant filter — needed for public auth endpoints
    // where TenantContext is not yet set (login, signup duplicate-check)
    @Query(value = "SELECT * FROM members WHERE email = :email LIMIT 1", nativeQuery = true)
    Optional<Member> findByEmail(@Param("email") String email);

    @Query(value = "SELECT COUNT(*) > 0 FROM members WHERE email = :email", nativeQuery = true)
    boolean existsByEmail(@Param("email") String email);

    // Count members in a specific tenant (native — used when TenantContext may not be set)
    @Query(value = "SELECT COUNT(*) FROM members WHERE business_id = :businessId", nativeQuery = true)
    long countByBusinessId(@Param("businessId") UUID businessId);

    // Check if email already a member in this tenant (native)
    @Query(value = "SELECT COUNT(*) > 0 FROM members WHERE business_id = :businessId AND email = :email", nativeQuery = true)
    boolean existsByEmailInTenant(@Param("businessId") UUID businessId, @Param("email") String email);
}
