package com.leonardtrinh.supportsaas.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MessageUsageRepository extends JpaRepository<MessageUsage, MessageUsageId> {

    @Query(value = "SELECT * FROM message_usage WHERE business_id = :businessId AND year_month = :yearMonth",
           nativeQuery = true)
    Optional<MessageUsage> findByBusinessIdAndYearMonth(
        @Param("businessId") UUID businessId,
        @Param("yearMonth") String yearMonth);

    /**
     * Atomically increments the message count for the given tenant and month.
     * Uses INSERT ... ON CONFLICT DO UPDATE to ensure atomicity.
     * No JdbcTemplate — uses @Modifying @Query(nativeQuery=true) per project conventions.
     */
    @Modifying
    @Query(value = """
        INSERT INTO message_usage (business_id, year_month, msg_count)
        VALUES (:businessId, :yearMonth, 1)
        ON CONFLICT (business_id, year_month)
        DO UPDATE SET msg_count = message_usage.msg_count + 1
        """, nativeQuery = true)
    void upsertIncrement(
        @Param("businessId") UUID businessId,
        @Param("yearMonth") String yearMonth);
}
