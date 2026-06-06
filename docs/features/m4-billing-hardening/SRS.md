# Software Requirements Specification — M4 Billing + Production Hardening

**Version:** 1.0
**Date:** 2026-06-02
**Status:** Draft
**Author:** Leonard Trinh

---

## 1. Introduction

### 1.1 Purpose

This document specifies the functional and non-functional requirements for Milestone 4 (M4) of the spring-saas-support-ai platform, targeting release v1.0.0. It serves as the authoritative contract for the development team and AI agents implementing the Stripe billing integration, plan enforcement, rate limiting, security hardening, and observability features.

### 1.2 Scope

M4 adds real revenue collection and production-grade reliability to the platform:

- **Stripe Checkout** — hosted payment flow enabling tenants to subscribe to paid plans
- **Plan enforcement** — quota checks on knowledge bases, documents, and monthly messages
- **14-day Pro trial** — auto-provisioned at signup, auto-downgrades on expiry
- **Rate limiting** — two-tier Bucket4j strategy (IP-based + tenant-based)
- **Security headers** — CSP, HSTS, X-Frame-Options, and input sanitization
- **Observability** — Actuator health probes, Prometheus metrics via Micrometer, structured MDC logging

**Out of scope for M4:** Stripe Elements/custom payment form, Stripe Customer Portal, Redis-backed rate limiting, multiple LLM providers, advanced analytics dashboard.

### 1.3 Definitions & Acronyms

| Term | Definition |
|------|-----------|
| JWT | JSON Web Token — used for authentication |
| MDC | Mapped Diagnostic Context — structured logging context |
| RAG | Retrieval-Augmented Generation |
| RRF | Reciprocal Rank Fusion — used in hybrid search |
| SSE | Server-Sent Events — used for streaming chat |
| CSP | Content Security Policy |
| HSTS | HTTP Strict Transport Security |
| RRL | Request Rate Limit |
| JTI | JWT ID — unique claim per token, used for revocation |

### 1.4 References

- [Design Spec](../../superpowers/specs/2026-06-02-m4-billing-hardening-design.md)
- [CLAUDE.md](../../../CLAUDE.md)
- [Architecture](../../../.claude/memory/architecture.md)
- [Tech Stack](../../../.claude/memory/tech-stack.md)
- [Constraints](../../../.claude/memory/constraints.md)

---

## 2. Overall Description

### 2.1 Product Perspective

spring-saas-support-ai is a multi-tenant, white-label AI customer support SaaS. M4 is the final milestone (v1.0.0), adding the billing layer that converts the platform from a demo into a revenue-generating product. It sits atop M3's AI chat and embeddable widget without modifying those subsystems.

The billing feature wraps Stripe as the payment processor. The DB is the authoritative source of subscription state — Stripe webhooks trigger DB updates; the quota hot-path never calls Stripe directly.

Subscription state machine:
```
TRIAL → ACTIVE → PAST_DUE → CANCELED
  └──────────────────────────► FREE (downgrade)
```

### 2.2 User Classes & Characteristics

| User Class | Description | Access Level |
|-----------|-------------|-------------|
| Business Owner | Created account, manages billing and team | ADMIN role |
| Member | Invited by owner, uses support tools | MEMBER role |
| End User | Customer using the embedded widget | PUBLIC (no JWT) |
| Stripe | External webhook sender | Validated by signature |

### 2.3 Operating Environment

- Java 21 (records, sealed interfaces, pattern matching), Spring Boot 3.3
- PostgreSQL 16 + pgvector extension
- Spring AI 1.1+ (Anthropic Claude / OpenAI embeddings)
- Bucket4j (in-memory rate limiting, no Redis required for v1.0)
- Micrometer + Prometheus
- Deployed on Render / Railway

### 2.4 Design Constraints

- No Lombok — use Java 21 records for DTOs
- No H2 — integration tests use Testcontainers (`pgvector/pgvector:pg16`)
- No JdbcTemplate — use `@Modifying @Query(nativeQuery=true)` on JpaRepository
- No bare `@Async` — always `@Async("processingExecutor")`
- Multi-tenancy: all business entities must extend `TenantEntity` with `business_id`
- Virtual threads disabled (`spring.threads.virtual.enabled=false`)
- Stripe API must never be called on the quota-check hot path
- Webhook idempotency is mandatory — duplicate events must be silently skipped

### 2.5 Assumptions & Dependencies

- Stripe account configured with products and prices for Starter, Pro, and Business plans
- Environment variables `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, and `STRIPE_PRICE_ID_*` are set before startup
- `APP_FRONTEND_URL` is set and reachable (used for Stripe success/cancel redirect)
- Existing `subscriptions` table has `stripe_subscription_id` and `stripe_customer_id` columns (or they are added in M4 migrations)
- Email service is functional (used to send trial expiry notifications)

---

## 3. Functional Requirements

### 3.1 Billing — Stripe Checkout

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-001 | `POST /api/v1/billing/checkout` creates a Stripe Checkout session for the requested plan and returns `{checkoutUrl}` | High | Response contains a valid Stripe-hosted URL; redirecting to it shows the Stripe payment page |
| FR-002 | On `checkout.session.completed` webhook, `BillingService.activate()` updates subscription status to `ACTIVE` and sets `current_period_start/end` in DB | High | After webhook delivery, `GET /api/v1/billing/subscription` returns `status=ACTIVE` with correct dates |
| FR-003 | `GET /api/v1/billing/plans` returns the full plan catalogue (Free, Starter, Pro, Business) with prices and limits | Med | Response includes all four plans; no auth required |
| FR-004 | `GET /api/v1/billing/subscription` returns the current tenant's subscription plan, status, and `current_period_end` | High | Authenticated request returns correct plan data from DB |
| FR-005 | `POST /api/v1/billing/cancel` schedules subscription cancellation at period end via `StripeService.cancelAtPeriodEnd()` | High | Stripe subscription shows `cancel_at_period_end=true`; DB status unchanged until `customer.subscription.deleted` webhook fires |
| FR-006 | `POST /api/v1/billing/upgrade` applies an immediate plan upgrade with Stripe proration | High | Stripe charges prorated amount; webhook `customer.subscription.updated` triggers `BillingService.handleSubscriptionUpdated()` which updates `planId` in DB |
| FR-007 | `POST /api/v1/billing/downgrade` schedules a plan downgrade to take effect at period end | High | DB sets `pending_plan_id`; no immediate charge; downgrade applied on next renewal webhook |
| FR-008 | Attempting to create a Checkout session when tenant already has `ACTIVE` subscription returns `400 AlreadySubscribedException` | Med | Response body contains `"error": "Already on plan: ..."` with HTTP 400 |
| FR-009 | All mutating Stripe API calls include an idempotency key (`tenantId:operation:date`) | High | Replaying the same request within 24h returns the same Stripe object, no duplicate charges |

### 3.2 Billing — Webhooks

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-010 | `POST /api/v1/billing/webhook` validates the Stripe-Signature header before processing | High | Request with invalid/missing signature returns HTTP 400; no DB changes occur |
| FR-011 | Processed webhook events are stored in `processed_webhook_events` by `stripe_event_id` | High | Inserting duplicate `stripe_event_id` is silently skipped; no double-apply of state |
| FR-012 | `invoice.payment_succeeded` sets subscription `status=ACTIVE` and updates `current_period_end` | High | After renewal webhook, subscription shows updated period end |
| FR-013 | `invoice.payment_failed` sets subscription `status=PAST_DUE` and triggers an email warning | High | DB status changes to `PAST_DUE`; email is sent to the tenant owner |
| FR-014 | `customer.subscription.deleted` downgrades tenant to the Free plan | High | After webhook, `GET /api/v1/billing/subscription` returns `planId=FREE`, `status=ACTIVE` |

### 3.3 Billing — Trial Flow

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-015 | On business signup, `SubscriptionService.createTrial()` creates a subscription with `status=TRIAL`, `planId=PRO`, `trialEndsAt=now()+14d` | High | New tenant immediately has Pro-level quota for 14 days |
| FR-016 | `TrialExpiryScheduler` runs daily at 02:00 and downgrades all trials where `trial_ends_at < now()` to Free | High | After scheduler run, expired trials have `planId=FREE`, `status=ACTIVE`; non-expired trials are untouched |
| FR-017 | Trial expiry sends a notification email to the tenant owner | Med | Email is delivered to the owner's address; email contains an upgrade call-to-action URL |

### 3.4 Billing — Quota Enforcement

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-018 | `KnowledgeBaseService.create()` calls `QuotaService.checkKnowledgeBaseQuota()` before persisting | High | Creating a KB beyond plan limit returns HTTP 402 with `QuotaExceededException` |
| FR-019 | `DocumentService.upload()` calls `QuotaService.checkDocumentQuota()` before storing | High | Uploading beyond doc-per-KB limit returns HTTP 402 |
| FR-020 | `ChatService.streamMessage()` calls `QuotaService.checkMessageQuota()` before streaming | High | Sending a message beyond monthly limit returns HTTP 402 |
| FR-021 | Paid tenants (`ACTIVE`/`TRIAL`) receive a 10% overage grace period; Free/`PAST_DUE`/`CANCELED` tenants are hard-blocked at the plan limit | High | Paid tenant at 100% of limit can still add one more (10% grace); Free tenant at limit is rejected immediately |
| FR-022 | `QuotaExceededException` is mapped by `GlobalExceptionHandler` to HTTP 402 with body `{"success":false,"error":"...","upgradeUrl":"/billing/plans"}` | High | HTTP status is exactly 402; body includes `upgradeUrl` |
| FR-023 | Monthly message counter uses `message_usage.period_start >= subscription.current_period_start` — no separate reset job | Med | After Stripe renewal webhook updates `current_period_start`, the counter resets automatically for the new period |

### 3.5 Billing — Reconciliation

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-024 | `StripeReconciliationScheduler` runs daily at 03:30 and fetches Stripe subscription state for all `ACTIVE`/`PAST_DUE`/`TRIAL` subscriptions that have a `stripe_subscription_id` | Med | Scheduler runs without error; log output includes count of subscriptions checked |
| FR-025 | Mismatches between DB status and Stripe status trigger `BillingService.syncFromStripe()` and increment metric `stripe.reconciliation.mismatch` | Med | After forcing a DB/Stripe mismatch, scheduler corrects it and the Prometheus counter increments |

### 3.6 Rate Limiting

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-026 | IP-based rate limit: `/auth/**` → 20 req/min, `/api/v1/widget/**` → 60 req/min, all others → 200 req/min | High | Sending requests beyond limit returns HTTP 429 with `Retry-After` header |
| FR-027 | Tenant-based rate limit (after JWT auth): Free=100, Starter=500, Pro=2000, Business=unlimited req/min | High | Authenticated tenant hitting limit returns HTTP 429; Business plan tenant is never limited |
| FR-028 | HTTP 429 response body: `{"success":false,"error":"Rate limit exceeded. Retry after 30s"}` with `Retry-After: 30` header | High | Response matches spec exactly |
| FR-029 | Buckets are stored in `ConcurrentHashMap` (in-memory); no Redis dependency for v1.0 | Med | Application starts and rate-limits correctly without Redis running |

### 3.7 Security Hardening

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-030 | `SecurityHeadersFilter` adds `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `HSTS: max-age=31536000; includeSubDomains`, `CSP: default-src 'self'; script-src 'self' js.stripe.com; frame-src js.stripe.com`, `X-XSS-Protection: 0`, `Referrer-Policy: strict-origin-when-cross-origin` to every response | High | Browser DevTools / `curl -I` shows all six headers on any response |
| FR-031 | Document upload strips path traversal sequences (`../`, `..\`) from filenames before storing to MinIO | High | Uploading a file named `../../etc/passwd` stores it as `etc_passwd` or equivalent safe name |
| FR-032 | Chat messages are trimmed and rejected if longer than 4000 characters before reaching the LLM | High | Sending a 4001-char message returns HTTP 400 |
| FR-033 | `ProcessedJtiCleanupScheduler` purges expired JTIs from `refresh_tokens` weekly | Low | After scheduler run, rows with `expires_at < now()` are deleted |

### 3.8 Observability

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-034 | Actuator exposes `/health/liveness` and `/health/readiness` probes | High | Both endpoints return HTTP 200 with `status:UP` when system is healthy |
| FR-035 | `DatabaseHealthIndicator`, `VectorStoreHealthIndicator`, and `MinioHealthIndicator` are registered and included in `/health` details | Med | `/health` (authenticated) shows each indicator's status |
| FR-036 | Micrometer exports metrics to Prometheus at `/actuator/prometheus` | High | Endpoint returns text in Prometheus exposition format containing at least `api_requests_total` |
| FR-037 | Custom counters `chat.messages.total`, `quota.exceeded.total`, `stripe.webhook.processed`, and `trial.expired.total` are incremented at the correct code paths | Med | Exercising each path increments the corresponding counter visible at `/actuator/prometheus` |
| FR-038 | `RequestIdFilter` sets `tenantId`, `userId`, and `requestId` in MDC for every request; log output includes these fields in JSON format | High | Log line for any authenticated request contains `tenantId`, `requestId`, `userId` fields |

---

## 4. Non-Functional Requirements

### 4.1 Performance

- Quota check (`QuotaService`) must complete in < 20 ms (DB-only, no Stripe call)
- Stripe Checkout session creation: < 2 s (network-bound, acceptable)
- Rate limit decision (Bucket4j in-memory): < 1 ms per request
- Reconciliation scheduler processes up to 10,000 subscriptions within the 30-minute window before the 04:00 business day start

### 4.2 Security

- Stripe webhook signature validated via `Stripe.constructEvent()` before any processing
- Webhook idempotency enforced via `processed_webhook_events.stripe_event_id UNIQUE` constraint
- Stripe API keys never logged, never returned in API responses
- All `/actuator/**` endpoints (except `/health`) require authentication
- Rate limiting applied to all public-facing endpoints
- JTI revocation list cleaned weekly to prevent unbounded table growth

### 4.3 Reliability

- Webhook processing failures must not corrupt subscription state — DB transaction wraps the full handler
- `StripeReconciliationScheduler` is a safety net: logs mismatches as `WARN` and corrects them, but does not replace webhook delivery
- `TrialExpiryScheduler` is idempotent — re-running after failure produces the same result (downgrade of same expired trials, no double email)
- Stripe API calls include idempotency keys to survive retry storms

### 4.4 Scalability

- In-memory Bucket4j is sufficient for single-instance v1.0; Redis migration path is documented for future horizontal scaling
- Subscription state in DB is the single source of truth; horizontal scaling does not require session affinity for quota checks

---

## 5. Use Cases

### UC-001: Tenant Subscribes to Starter Plan

**Actor:** Business Owner  
**Preconditions:** Owner is logged in; tenant is on Free plan or trial  
**Main Flow:**
1. Owner calls `POST /api/v1/billing/checkout` with `{"planSlug": "starter"}`
2. Backend calls `StripeService.getOrCreateCustomer()` to obtain/create a Stripe customer
3. Backend calls `StripeService.createCheckoutSession()` and returns `{checkoutUrl}`
4. Owner is redirected to the Stripe-hosted payment page
5. Owner enters payment details and completes checkout
6. Stripe sends `checkout.session.completed` webhook
7. `WebhookController` validates signature, checks idempotency, calls `BillingService.activate()`
8. DB subscription is updated: `status=ACTIVE`, `planId=STARTER`, `current_period_start/end` set

**Alternate Flows:**
- A1: Owner already has `ACTIVE` subscription → step 2 throws `AlreadySubscribedException` → HTTP 400
- A2: Stripe webhook arrives twice → second event is skipped (idempotency check in step 7)

**Postconditions:** Tenant has Starter plan quota; next `GET /api/v1/billing/subscription` returns `status=ACTIVE`

---

### UC-002: Tenant Upgrades from Starter to Pro

**Actor:** Business Owner  
**Preconditions:** Tenant has `status=ACTIVE`, `planId=STARTER`  
**Main Flow:**
1. Owner calls `POST /api/v1/billing/upgrade` with `{"planSlug": "pro"}`
2. `StripeService.updateSubscription()` called with `prorate=true`
3. Stripe charges prorated difference immediately
4. Stripe sends `customer.subscription.updated` webhook
5. `BillingService.handleSubscriptionUpdated()` updates `planId=PRO` and `current_period_end` in DB

**Postconditions:** Tenant immediately has Pro quotas

---

### UC-003: Tenant Downgrades from Pro to Starter

**Actor:** Business Owner  
**Preconditions:** Tenant has `status=ACTIVE`, `planId=PRO`  
**Main Flow:**
1. Owner calls `POST /api/v1/billing/downgrade` with `{"planSlug": "starter"}`
2. `StripeService.scheduleSubscriptionUpdate()` sets a pending change on Stripe (no immediate charge)
3. DB sets `subscription.pending_plan_id = STARTER_PLAN_ID`
4. At period end, Stripe sends `customer.subscription.updated` webhook
5. Backend applies downgrade: `planId=STARTER`, `pending_plan_id=NULL`

**Postconditions:** Tenant stays on Pro until period ends, then drops to Starter

---

### UC-004: Trial Expires — Auto-Downgrade to Free

**Actor:** `TrialExpiryScheduler` (system)  
**Preconditions:** Tenant has `status=TRIAL`, `trial_ends_at < now()`  
**Main Flow:**
1. Scheduler runs at 02:00
2. Finds all expired trials via `findAllByStatusAndTrialEndsAtBefore(TRIAL, now())`
3. For each: sets `planId=FREE`, `status=ACTIVE`
4. Sends trial-expired email to tenant owner

**Postconditions:** Tenant is on Free plan; owner receives email with upgrade link

---

### UC-005: Tenant Hits Knowledge Base Quota

**Actor:** Business Owner / Member  
**Preconditions:** Tenant on Free plan, already has 1 KB (plan limit = 1)  
**Main Flow:**
1. User calls `POST /api/v1/kb` to create a second KB
2. `KnowledgeBaseService.create()` calls `QuotaService.checkKnowledgeBaseQuota(tenantId)`
3. Count = 1, limit = 1 (Free, no grace) → throws `QuotaExceededException`
4. `GlobalExceptionHandler` maps to HTTP 402 with `upgradeUrl`

**Postconditions:** KB is not created; user sees actionable upgrade message

---

### UC-006: IP Rate Limit Exceeded

**Actor:** End User / Bot  
**Preconditions:** 20+ requests to `/auth/**` from same IP in one minute  
**Main Flow:**
1. 21st request arrives at `RateLimitFilter`
2. IP bucket is exhausted
3. Filter returns HTTP 429 with `Retry-After: 30` and JSON error body

**Postconditions:** No auth processing occurs; client must wait before retrying

---

## 6. External Interface Requirements

### 6.1 API Interfaces

Full API contract: see [api-spec.md](api-spec.md) *(generate with `/api-spec`)*

**New endpoints summary:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/billing/plans` | Public | List available plans |
| GET | `/api/v1/billing/subscription` | Bearer | Current tenant subscription |
| POST | `/api/v1/billing/checkout` | Bearer | Create Stripe Checkout session |
| GET | `/api/v1/billing/success` | Bearer | Post-checkout success handler |
| POST | `/api/v1/billing/cancel` | Bearer | Cancel subscription at period end |
| POST | `/api/v1/billing/upgrade` | Bearer | Upgrade to higher plan (immediate + proration) |
| POST | `/api/v1/billing/downgrade` | Bearer | Downgrade to lower plan (at period end) |
| POST | `/api/v1/billing/webhook` | Stripe-sig | Stripe webhook receiver |

**Modified endpoints:**

| Method | Path | Change |
|--------|------|--------|
| POST | `/api/v1/kb` | Quota check added |
| POST | `/api/v1/kb/{id}/documents/upload` | Quota check added |
| POST | `/api/v1/chat/stream` | Monthly message quota check added |

### 6.2 Database Interfaces

Full schema: see [db-schema.md](db-schema.md) *(generate with `/db-schema`)*

**New migrations:**

| Migration | Purpose |
|-----------|---------|
| `V21__create_processed_webhook_events.sql` | Webhook idempotency table (`stripe_event_id UNIQUE`) |
| `V22__add_pending_plan_to_subscriptions.sql` | `subscriptions.pending_plan_id UUID` (nullable) for deferred downgrade |

### 6.3 External Services

| Service | Integration Point | Direction |
|---------|-----------------|-----------|
| Stripe API | `StripeService` — customer, checkout session, subscription update | Outbound |
| Stripe Webhooks | `WebhookController POST /billing/webhook` | Inbound |
| Email (Resend/SMTP) | `AsyncEmailSender` — trial expiry, payment failure | Outbound |
| Prometheus | `/actuator/prometheus` — scraped by monitoring stack | Outbound pull |

### 6.4 Environment Variables

| Variable | Description |
|----------|-------------|
| `STRIPE_SECRET_KEY` | Stripe API secret key (`sk_live_...`) |
| `STRIPE_WEBHOOK_SECRET` | Webhook signing secret (`whsec_...`) |
| `STRIPE_PRICE_ID_STARTER` | Stripe Price ID for Starter plan |
| `STRIPE_PRICE_ID_PRO` | Stripe Price ID for Pro plan |
| `STRIPE_PRICE_ID_BUSINESS` | Stripe Price ID for Business plan |
| `APP_FRONTEND_URL` | Frontend base URL for Stripe redirect (success/cancel) |

---

## 7. Out of Scope

- Stripe Elements / custom payment form (future milestone)
- Stripe Customer Portal (self-service billing management)
- Redis-backed rate limiting (planned when horizontally scaling)
- Advanced analytics / revenue dashboard
- Multiple LLM providers
- Webhooks for anything other than Stripe (Slack, Intercom, etc.)
- Proration on downgrade (downgrade is deferred to period end, no proration)
- Partial refunds or credit notes
- Coupon / discount code support
