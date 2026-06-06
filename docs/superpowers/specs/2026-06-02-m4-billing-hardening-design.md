# M4 Design Spec — Billing + Production Hardening (v1.0.0)

**Date:** 2026-06-02  
**Milestone:** M4 → v1.0.0  
**Goal:** Real revenue via Stripe + production-grade hardening

---

## 1. Goals & Non-Goals

### Goals
- Stripe Checkout hosted flow — users can actually pay
- Plan enforcement with grace period (10% overage for paid, hard block for Free)
- 14-day Pro trial on signup, auto-downgrade to Free on expiry
- Rate limiting (IP-based + tenant-based via Bucket4j)
- Security headers hardening
- Actuator health checks (liveness/readiness for Render)
- Prometheus metrics via Micrometer
- Structured logging with MDC (tenantId, requestId, userId)

### Non-Goals
- Stripe Elements / custom payment form (future)
- Stripe Customer Portal (future)
- Redis-backed rate limiting (in-memory sufficient for v1.0)
- Advanced analytics dashboard
- Multiple LLM providers

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                     M4 Components                        │
├──────────────────────┬──────────────────────────────────┤
│   Billing            │   Production Hardening            │
│                      │                                   │
│  StripeService       │  RateLimitFilter (Bucket4j)       │
│  BillingService      │  SecurityHardeningConfig          │
│  WebhookController   │  ActuatorConfig                   │
│  PlanController      │  PrometheusMetrics                │
│  SubscriptionService │  StructuredLoggingFilter (MDC)    │
│  QuotaService ───────┼► quota check = DB only, no Stripe │
│  TrialScheduler      │  call on hot path                 │
└──────────────────────┴──────────────────────────────────┘
```

**Core principle:** DB is source of truth. Stripe is the payment processor. Webhooks trigger DB updates — never call Stripe API on the quota check hot path.

**Subscription state machine:**
```
TRIAL → ACTIVE → PAST_DUE → CANCELED
  └──────────────────────────► FREE (downgrade)
```

---

## 3. Billing

### 3.1 Stripe Checkout Flow

```
Frontend                 Backend                      Stripe
   │                        │                            │
   ├─ POST /billing/checkout─►                           │
   │  {planSlug: "starter"} │                            │
   │                        ├─ getOrCreateCustomer() ───►│
   │                        │◄─ customerId ──────────────┤
   │                        ├─ createCheckoutSession() ──►│
   │                        │◄─ {sessionId, url} ────────┤
   │◄─ {checkoutUrl} ───────┤                            │
   │                        │                            │
   ├─ redirect to Stripe ───────────────────────────────►│
   │                        │       user pays            │
   │◄─ redirect to /billing/success ────────────────────┤
   │                        │                            │
   │                        │◄─ webhook: checkout.completed
   │                        ├─ BillingService.activate() │
   │                        │  update subscription in DB │
```

**New API endpoints:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/billing/plans` | Public | List available plans |
| GET | `/api/v1/billing/subscription` | Bearer | Current tenant subscription |
| POST | `/api/v1/billing/checkout` | Bearer | Create Stripe Checkout session |
| GET | `/api/v1/billing/success` | Bearer | Post-checkout success handler |
| POST | `/api/v1/billing/cancel` | Bearer | Cancel subscription (at period end) |
| POST | `/api/v1/billing/upgrade` | Bearer | Upgrade to higher plan (immediate + proration) |
| POST | `/api/v1/billing/downgrade` | Bearer | Downgrade to lower plan (at period end) |
| POST | `/api/v1/billing/webhook` | Stripe-sig | Stripe webhook receiver |

### 3.2 Upgrade / Downgrade Flow

**Upgrade (e.g. Starter → Pro) — immediate with proration:**
```
POST /api/v1/billing/upgrade {planSlug: "pro"}
  └─► StripeService.updateSubscription(stripeSubId, newPriceId, prorate=true)
        Stripe charges prorated diff immediately
        └─► webhook: customer.subscription.updated
              BillingService.handleSubscriptionUpdated()
                update subscription.planId + current_period_end in DB
```

**Downgrade (e.g. Pro → Starter) — deferred to period end:**
```
POST /api/v1/billing/downgrade {planSlug: "starter"}
  └─► StripeService.scheduleSubscriptionUpdate(stripeSubId, newPriceId)
        Stripe sets pending change for next renewal, no immediate charge
        └─► BillingService.setPendingDowngrade(tenantId, targetPlanSlug)
              DB: subscription.pending_plan_id = starter_plan_id
              ─► webhook: customer.subscription.updated (at period end)
                    apply downgrade: update planId, clear pending_plan_id
```

**Cancel — deferred to period end:**
```
POST /api/v1/billing/cancel
  └─► StripeService.cancelAtPeriodEnd(stripeSubId)
        └─► webhook: customer.subscription.deleted (at period end)
              downgrade to Free
```

New DB column needed: `subscriptions.pending_plan_id UUID` (nullable).
New migration: `V22__add_pending_plan_to_subscriptions.sql`

Add webhook event to handle:

| Event | Action |
|-------|--------|
| `customer.subscription.updated` | Apply upgrade/downgrade, update planId + period dates |

---

### 3.3 Idempotency (Full Coverage)

Three layers:

**Layer 1 — Webhook idempotency** (already in spec):
Store `stripe_event_id` in `processed_webhook_events`. Duplicate webhook → skip.

**Layer 2 — Checkout session idempotency** (double-click / browser back):
Before creating a new Checkout session, check if tenant already has an `INCOMPLETE` Stripe subscription or an open session:
```java
// BillingServiceImpl.createCheckoutSession()
Optional<Subscription> existing = subscriptionRepo.findActiveOrTrialByBusinessId(tenantId);
if (existing.isPresent() && existing.get().getStatus() == ACTIVE) {
    throw new AlreadySubscribedException("Already on plan: " + existing.get().getPlanId());
}
```
Stripe also rejects duplicate Checkout sessions for the same customer + price — rely on both.

**Layer 3 — Stripe API call idempotency**:
Pass `IdempotencyKey` header on all mutating Stripe calls:
```java
// deterministic key = tenantId + operation + date (safe to retry within 24h)
String idempotencyKey = tenantId + ":checkout:" + LocalDate.now();
RequestOptions opts = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
Session.create(params, opts);
```

---

### 3.4 Reconciliation

Webhooks can be missed (network errors, Stripe retries for 3 days but not guaranteed).
A daily reconciliation job syncs DB subscription state against Stripe.

```
@Scheduled(cron = "0 30 3 * * *")   ← 3:30am daily (after trial expiry at 2am)
StripeReconciliationScheduler.run()
  └─► for each subscription WHERE status IN (ACTIVE, PAST_DUE, TRIAL):
        ├─ stripeService.retrieveSubscription(stripeSubscriptionId)
        │   (skip if stripeSubscriptionId is null — trial not yet converted)
        ├─ compare Stripe status vs DB status
        └─ if mismatch:
             log.warn("Reconciliation mismatch tenantId={} dbStatus={} stripeStatus={}")
             billingService.syncFromStripe(stripeSubscription)
             metrics.increment("stripe.reconciliation.mismatch")
```

**Reconciliation does NOT replace webhooks** — it is a safety net only. Log every mismatch as a warning so ops can investigate webhook delivery issues.

---

| Event | Action |
|-------|--------|
| `checkout.session.completed` | Activate subscription, set period dates |
| `customer.subscription.updated` | Apply upgrade/downgrade, sync planId + period dates |
| `invoice.payment_succeeded` | Renew subscription, update `current_period_end` |
| `invoice.payment_failed` | Set status `PAST_DUE`, send email warning |
| `customer.subscription.deleted` | Cancel → downgrade to Free |

**Idempotency:** Store processed `stripe_event_id` in `processed_webhook_events` table. Duplicate events → skip.

New migration: `V21__create_processed_webhook_events.sql`
```sql
CREATE TABLE processed_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stripe_event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_processed_webhook_events_event_id ON processed_webhook_events(stripe_event_id);
```

### 3.3 Plan Enforcement (QuotaService)

**Logic:**
- **Free tier** → hard block at limit
- **Paid tier (ACTIVE/TRIAL)** → 10% grace period before block
- **PAST_DUE** → hard block (same as Free)
- **CANCELED** → hard block

```java
// Pseudocode
public void checkKnowledgeBaseQuota(UUID tenantId) {
    Plan plan = getCurrentPlan(tenantId);
    long current = kbRepository.countByBusinessId(tenantId);
    boolean isPaid = isActivePaidSubscription(tenantId);

    int limit = isPaid
        ? (int)(plan.getMaxKnowledgeBases() * 1.1)
        : plan.getMaxKnowledgeBases();

    if (current >= limit) throw new QuotaExceededException("knowledge_bases", limit);
}
```

Quota checks apply to:
- `KnowledgeBaseService.create()` — max KBs
- `DocumentService.upload()` — max docs per KB
- `ChatService.streamMessage()` — max messages/month

**Monthly message counter reset:** `MessageUsage` table already tracks usage per tenant per billing period. `QuotaServiceImpl.countMessagesThisMonth()` queries `message_usage` WHERE `period_start >= subscription.current_period_start`. No separate reset job needed — the window slides naturally when Stripe renews the subscription and updates `current_period_start` via webhook.

`QuotaExceededException` → `GlobalExceptionHandler` → `402 Payment Required`
```json
{
  "success": false,
  "error": "Knowledge base limit reached (1/1). Upgrade to add more.",
  "upgradeUrl": "/billing/plans"
}
```

### 3.4 Trial Flow

```
Signup
  └─► SubscriptionService.createTrial(businessId)
        Subscription {
          status: TRIAL,
          planId: PRO_PLAN_ID,
          trialEndsAt: now() + 14 days
        }

@Scheduled(cron = "0 0 2 * * *")   ← 2am daily
TrialExpiryScheduler.processExpiredTrials()
  └─► findAllByStatusAndTrialEndsAtBefore(TRIAL, now())
      for each expired:
        ├─ subscription.planId = FREE_PLAN_ID
        ├─ subscription.status = ACTIVE
        └─ emailService.sendTrialExpiredEmail(member)
```

---

## 4. Rate Limiting (Bucket4j)

### 4.1 Two-tier Strategy

**Tier 1 — IP-based (brute force / DDoS protection):**
- `/auth/**` → 20 req/min per IP
- `/api/v1/widget/**` → 60 req/min per IP (public endpoint)
- All others → 200 req/min per IP

**Tier 2 — Tenant-based (per authenticated tenant):**

| Plan | Limit |
|------|-------|
| Free | 100 req/min |
| Starter | 500 req/min |
| Pro | 2,000 req/min |
| Business | Unlimited |

**Implementation:** `RateLimitFilter extends OncePerRequestFilter`
- Tier 1 runs before auth
- Tier 2 runs after JWT validation (tenantId available)
- Buckets stored in `ConcurrentHashMap<UUID, Bucket>` (in-memory, no Redis for v1.0)

**Response on limit exceeded:**
```
HTTP 429 Too Many Requests
Retry-After: 30
{"success": false, "error": "Rate limit exceeded. Retry after 30s"}
```

---

## 5. Security Hardening

### 5.1 Security Headers

New `SecurityHeadersFilter extends OncePerRequestFilter`:

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: default-src 'self'; script-src 'self' js.stripe.com; frame-src js.stripe.com
X-XSS-Protection: 0
Referrer-Policy: strict-origin-when-cross-origin
```

Note: `script-src` and `frame-src` include Stripe domains for Checkout redirect.

### 5.2 Input Sanitization Audit

- **Document filename** → strip path traversal (`../`, `..\\`) before storing to MinIO
- **Chat message** → trim + enforce max 4000 chars before sending to LLM
- **All `@RequestBody`** → already validated via `@Valid` — retain as-is

### 5.3 Auth Cleanup

- Add `ProcessedJtiCleanupScheduler`: weekly job purging expired JTIs from `refresh_tokens` table

---

## 6. Observability

### 6.1 Actuator + Health Checks

```yaml
management:
  endpoints.web.exposure.include: health, metrics, prometheus, info
  endpoint.health.show-details: when-authorized
  endpoint.health.probes.enabled: true   # /health/liveness + /health/readiness
```

Custom `HealthIndicator` beans:
- `DatabaseHealthIndicator` — SELECT 1 on postgres
- `VectorStoreHealthIndicator` — ping pgvector extension
- `MinioHealthIndicator` — ping MinIO bucket

### 6.2 Prometheus Metrics (Micrometer)

| Metric | Type | Tags |
|--------|------|------|
| `api.requests.total` | Counter | method, path, status, tenantPlan |
| `api.request.duration` | Timer | path |
| `chat.messages.total` | Counter | tenantId |
| `document.ingestion.duration` | Timer | status |
| `quota.exceeded.total` | Counter | resource, plan |
| `stripe.webhook.processed` | Counter | event, status |
| `trial.expired.total` | Counter | — |

### 6.3 Structured Logging (MDC)

Extend existing `RequestIdFilter` to also set:
```java
MDC.put("tenantId", tenantId);    // from TenantContext
MDC.put("userId", userId);         // from SecurityContext
MDC.put("requestId", requestId);   // already set
```

Log output format (JSON via logback):
```json
{"level":"INFO","tenantId":"abc-123","requestId":"req-456","userId":"user-789","message":"..."}
```

---

## 7. New Files Summary

### Backend

| File | Purpose |
|------|---------|
| `billing/BillingController.java` | Plans, checkout, cancel endpoints |
| `billing/BillingService.java` | Interface |
| `billing/BillingServiceImpl.java` | Stripe calls, subscription activation |
| `billing/StripeService.java` | Stripe SDK wrapper (customer, session, webhook) |
| `billing/WebhookController.java` | POST /billing/webhook |
| `billing/QuotaService.java` | Interface |
| `billing/QuotaServiceImpl.java` | Quota checks per resource type |
| `billing/SubscriptionService.java` | Interface |
| `billing/SubscriptionServiceImpl.java` | Trial creation, status transitions |
| `billing/TrialExpiryScheduler.java` | Daily cron, downgrade expired trials |
| `billing/StripeReconciliationScheduler.java` | Daily cron, sync DB ↔ Stripe state |
| `billing/ProcessedWebhookEvent.java` | Entity for webhook idempotency |
| `billing/ProcessedWebhookEventRepository.java` | JPA repo |
| `config/RateLimitFilter.java` | Two-tier Bucket4j filter |
| `config/SecurityHeadersFilter.java` | HTTP security headers |
| `config/ActuatorSecurityConfig.java` | Secure actuator endpoints |
| `health/DatabaseHealthIndicator.java` | Postgres health check |
| `health/VectorStoreHealthIndicator.java` | PgVector health check |
| `health/MinioHealthIndicator.java` | MinIO health check |
| `config/MetricsConfig.java` | Custom Micrometer meters |

### Database Migrations

| File | Purpose |
|------|---------|
| `V21__create_processed_webhook_events.sql` | Webhook idempotency table |
| `V22__add_pending_plan_to_subscriptions.sql` | Deferred downgrade support |

### Dependencies to add (pom.xml)

```xml
<dependency>
  <groupId>com.stripe</groupId>
  <artifactId>stripe-java</artifactId>
  <!-- verify latest at: https://mvnrepository.com/artifact/com.stripe/stripe-java -->
  <version>LATEST</version>
</dependency>
<dependency>
  <groupId>com.bucket4j</groupId>
  <artifactId>bucket4j-core</artifactId>
  <!-- verify latest at: https://mvnrepository.com/artifact/com.bucket4j/bucket4j-core -->
  <version>LATEST</version>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

---

## 8. Environment Variables (new)

| Variable | Description |
|----------|-------------|
| `STRIPE_SECRET_KEY` | Stripe API secret key (sk_live_...) |
| `STRIPE_WEBHOOK_SECRET` | Webhook signing secret (whsec_...) |
| `STRIPE_PRICE_ID_STARTER` | Stripe Price ID for Starter plan |
| `STRIPE_PRICE_ID_PRO` | Stripe Price ID for Pro plan |
| `STRIPE_PRICE_ID_BUSINESS` | Stripe Price ID for Business plan |
| `APP_FRONTEND_URL` | Frontend URL for Stripe redirect (success/cancel) |

---

## 9. Testing Plan

| Test | Type | Covers |
|------|------|--------|
| `BillingServiceTest` | Unit | Checkout session creation, plan lookup |
| `QuotaServiceTest` | Unit | Grace period logic, hard block for Free |
| `TrialExpirySchedulerTest` | Unit | Downgrade logic, email trigger |
| `WebhookControllerTest` | Unit | Stripe signature validation, idempotency |
| `RateLimitFilterTest` | Unit | Bucket refill, 429 response |
| `BillingIT` | Integration (Testcontainers) | Full checkout → webhook → quota flow |
| `TrialExpiryIT` | Integration | Trial creation → expiry → downgrade |
| `UpgradeDowngradeTest` | Unit | Proration on upgrade, deferred downgrade scheduling |
| `WebhookIdempotencyTest` | Unit | Duplicate event → skip, no double-apply |
| `ReconciliationSchedulerTest` | Unit | Mismatch detected → syncFromStripe called, metric incremented |
| `CheckoutIdempotencyTest` | Unit | Already-active tenant → AlreadySubscribedException |
