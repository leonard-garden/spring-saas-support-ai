package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.common.BaseIT;
import com.leonardtrinh.supportsaas.email.AsyncEmailSender;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Integration test for the trial-expiry flow:
 *   1. Create a tenant (business + owner member) with a TRIALING / Pro subscription.
 *   2. Manually back-date trialEndsAt to yesterday.
 *   3. Call {@link TrialExpiryScheduler#downgradeExpiredTrials()} directly.
 *   4. Assert subscription is downgraded to Free (status=ACTIVE, planSlug=free).
 *   5. Assert the trial-expired email notification was dispatched.
 *
 * <p>The scheduler uses native SQL queries that bypass the Hibernate tenant filter,
 * so TenantContext is only needed when building the subscription entity for the
 * {@code business_id} write — not for the scheduler itself.
 *
 * <p>Plans (free, pro, …) are seeded by Flyway V8__seed_plans.sql and are therefore
 * present in the Testcontainers DB after migrations run.
 */
@Testcontainers
class TrialExpiryIT extends BaseIT {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TrialExpiryScheduler scheduler;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AsyncEmailSender asyncEmailSender;

    // ---------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------

    /**
     * Inserts a minimal business row and returns its generated id.
     */
    private UUID insertBusiness(String name) {
        UUID businessId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO businesses (id, name, slug) VALUES (?, ?, ?)",
                businessId, name, "slug-" + businessId);
        return businessId;
    }

    /**
     * Inserts an owner member for the business so that
     * {@link SubscriptionRepository#findOwnerEmailByBusinessId} can find an email.
     */
    private void insertOwnerMember(UUID businessId, String email) {
        jdbcTemplate.update(
                """
                INSERT INTO members (business_id, email, password_hash, role, email_verified)
                VALUES (?, ?, ?, 'OWNER', true)
                """,
                businessId, email, "hashed-pw");
    }

    /**
     * Creates a TRIALING subscription for the given business using the supplied plan,
     * with trialEndsAt set to tomorrow (still active at creation time).
     * Returns the saved {@link Subscription}.
     */
    private Subscription createTrialingSubscription(UUID businessId, Plan plan) {
        TenantContext.setTenantId(businessId);
        try {
            Subscription sub = new Subscription();
            sub.setBusinessId(businessId);
            sub.setPlanId(plan.getId());
            sub.setStatus(SubscriptionStatus.TRIALING);
            sub.setTrialEndsAt(Instant.now().plus(14, ChronoUnit.DAYS));
            sub.setCurrentPeriodStart(Instant.now());
            sub.setCurrentPeriodEnd(Instant.now().plus(14, ChronoUnit.DAYS));
            return subscriptionRepository.save(sub);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Back-dates {@code trialEndsAt} to yesterday via JDBC so that the scheduler
     * considers the trial expired.  Uses native SQL to bypass the tenant filter.
     */
    private void expireTrialInDb(UUID subscriptionId) {
        Timestamp yesterday = Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS));
        jdbcTemplate.update(
                "UPDATE subscriptions SET trial_ends_at = ? WHERE id = ?",
                yesterday, subscriptionId);
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @BeforeEach
    void setUp() {
        configureRestTemplate();
    }

    @Test
    @DisplayName("downgradeExpiredTrials: TRIALING sub with past trialEndsAt is downgraded to Free and email is sent")
    void downgradeExpiredTrials_expiredTrial_downgradesToFreeAndSendsEmail() {
        // --- Arrange ---
        Plan proPlan = planRepository.findBySlug("pro")
                .orElseThrow(() -> new IllegalStateException("Pro plan not seeded — check V8__seed_plans.sql"));
        Plan freePlan = planRepository.findBySlug("free")
                .orElseThrow(() -> new IllegalStateException("Free plan not seeded — check V8__seed_plans.sql"));

        UUID businessId = insertBusiness("Trial Expiry Test Co");
        String ownerEmail = "owner-" + UUID.randomUUID() + "@example.com";
        insertOwnerMember(businessId, ownerEmail);

        Subscription sub = createTrialingSubscription(businessId, proPlan);
        UUID subId = sub.getId();

        // Verify initial state
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(sub.getPlanId()).isEqualTo(proPlan.getId());

        // Manually expire the trial
        expireTrialInDb(subId);

        // --- Act ---
        scheduler.downgradeExpiredTrials();

        // --- Assert: subscription downgraded to Free ---
        Subscription updated = subscriptionRepository.findById(subId)
                .orElseThrow(() -> new AssertionError("Subscription not found after downgrade"));

        assertThat(updated.getStatus())
                .as("status must be ACTIVE after trial expiry downgrade")
                .isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(updated.getPlanId())
                .as("planId must be the Free plan after downgrade")
                .isEqualTo(freePlan.getId());

        // --- Assert: trial-expired email dispatched to the owner ---
        verify(asyncEmailSender).sendTrialExpiredAsync(ownerEmail);
    }

    @Test
    @DisplayName("downgradeExpiredTrials: TRIALING sub with future trialEndsAt is NOT downgraded")
    void downgradeExpiredTrials_activeTrial_notDowngraded() {
        // --- Arrange ---
        Plan proPlan = planRepository.findBySlug("pro")
                .orElseThrow(() -> new IllegalStateException("Pro plan not seeded"));

        UUID businessId = insertBusiness("Active Trial Co");
        insertOwnerMember(businessId, "active-owner-" + UUID.randomUUID() + "@example.com");

        Subscription sub = createTrialingSubscription(businessId, proPlan);
        UUID subId = sub.getId();

        // trialEndsAt is still in the future — do NOT expire it

        // --- Act ---
        scheduler.downgradeExpiredTrials();

        // --- Assert: subscription untouched ---
        Subscription untouched = subscriptionRepository.findById(subId)
                .orElseThrow(() -> new AssertionError("Subscription not found"));

        assertThat(untouched.getStatus())
                .as("status must remain TRIALING when trial has not expired")
                .isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(untouched.getPlanId())
                .as("planId must remain Pro when trial has not expired")
                .isEqualTo(proPlan.getId());
    }

    @Test
    @DisplayName("downgradeExpiredTrials: idempotent — second run finds no eligible rows")
    void downgradeExpiredTrials_secondRun_isIdempotent() {
        // --- Arrange ---
        Plan proPlan = planRepository.findBySlug("pro")
                .orElseThrow(() -> new IllegalStateException("Pro plan not seeded"));
        Plan freePlan = planRepository.findBySlug("free")
                .orElseThrow(() -> new IllegalStateException("Free plan not seeded"));

        UUID businessId = insertBusiness("Idempotent Trial Co");
        insertOwnerMember(businessId, "idempotent-owner-" + UUID.randomUUID() + "@example.com");

        Subscription sub = createTrialingSubscription(businessId, proPlan);
        expireTrialInDb(sub.getId());

        // --- Act: run twice ---
        scheduler.downgradeExpiredTrials();
        scheduler.downgradeExpiredTrials();

        // --- Assert: still Free/ACTIVE after second run ---
        Subscription updated = subscriptionRepository.findById(sub.getId())
                .orElseThrow(() -> new AssertionError("Subscription not found"));

        assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(updated.getPlanId()).isEqualTo(freePlan.getId());
    }
}
