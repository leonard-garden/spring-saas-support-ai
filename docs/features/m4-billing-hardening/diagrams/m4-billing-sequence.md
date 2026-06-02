# Sequence Diagram — M4 Billing

## Overview

Shows the four critical runtime flows for M4 billing: (1) Stripe Checkout session creation, (2) Webhook event processing with idempotency, (3) Quota enforcement on resource creation, and (4) the two scheduled jobs (trial expiry + reconciliation).

---

## Flow 1 — Stripe Checkout (Subscribe to a Plan)

```mermaid
sequenceDiagram
    autonumber

    participant Client
    participant JwtAuthFilter
    participant TenantContext
    participant RateLimitFilter
    participant BillingController
    participant BillingService
    participant SubscriptionService
    participant StripeService
    participant StripeAPI as Stripe API
    participant DB as PostgreSQL

    Client->>JwtAuthFilter: POST /api/v1/billing/checkout {planSlug: "starter"}<br/>Authorization: Bearer <token>
    JwtAuthFilter->>TenantContext: setTenantId(claims.tenantId())
    JwtAuthFilter->>RateLimitFilter: filterChain.doFilter()
    RateLimitFilter->>BillingController: IP + tenant bucket OK

    BillingController->>BillingService: createCheckoutSession(tenantId, "starter")

    BillingService->>SubscriptionService: getCurrentSubscription(tenantId)
    SubscriptionService->>DB: SELECT * FROM subscriptions WHERE business_id = :tenantId
    DB-->>SubscriptionService: Subscription{status=TRIALING}
    SubscriptionService-->>BillingService: subscription

    Note over BillingService: status == ACTIVE? → throw AlreadySubscribedException<br/>status == TRIALING → proceed

    BillingService->>DB: SELECT stripe_customer_id FROM subscriptions WHERE business_id = :tenantId
    DB-->>BillingService: null (no customer yet)

    BillingService->>StripeService: getOrCreateCustomer(tenantId, ownerEmail)
    StripeService->>StripeAPI: POST /v1/customers {email, metadata: {tenantId}}
    StripeAPI-->>StripeService: Customer{id: "cus_..."}
    StripeService-->>BillingService: "cus_..."

    BillingService->>DB: UPDATE subscriptions SET stripe_customer_id = "cus_..." WHERE business_id = :tenantId

    Note over BillingService: idempotencyKey = tenantId + ":checkout:" + today

    BillingService->>StripeService: createCheckoutSession("cus_...", starterPriceId, idempotencyKey)
    StripeService->>StripeAPI: POST /v1/checkout/sessions (Idempotency-Key header)
    StripeAPI-->>StripeService: Session{id: "cs_...", url: "https://checkout.stripe.com/..."}
    StripeService-->>BillingService: checkoutUrl

    BillingService-->>BillingController: checkoutUrl
    BillingController-->>Client: 200 {success:true, data:{checkoutUrl}}

    Note over Client,StripeAPI: User completes payment on Stripe-hosted page

    Client->>BillingController: GET /api/v1/billing/success?session_id=cs_...
    BillingController-->>Client: 200 {success:true, data:{message:"Payment received. Activating..."}}

    Note over BillingController: ⚠ Do NOT activate here — wait for webhook
```

---

## Flow 2 — Stripe Webhook Processing (Activation + Idempotency)

```mermaid
sequenceDiagram
    autonumber

    participant StripeAPI as Stripe
    participant WebhookController
    participant StripeService
    participant WebhookRepo as ProcessedWebhookEventRepository
    participant BillingService
    participant SubscriptionRepo as SubscriptionRepository
    participant DB as PostgreSQL

    StripeAPI->>WebhookController: POST /api/v1/billing/webhook<br/>Stripe-Signature: t=...,v1=...<br/>Body: raw JSON event

    Note over WebhookController: No JWT auth — Stripe-Signature replaces it

    WebhookController->>StripeService: constructWebhookEvent(rawBody, sigHeader)
    StripeService->>StripeAPI: Stripe.constructEvent() — validates HMAC

    alt Invalid signature
        StripeService-->>WebhookController: throws SignatureVerificationException
        WebhookController-->>StripeAPI: 400 Bad Request
    else Valid signature
        StripeService-->>WebhookController: Event{id: "evt_...", type: "checkout.session.completed"}

        WebhookController->>WebhookRepo: existsByStripeEventId("evt_...")
        WebhookRepo->>DB: SELECT 1 FROM processed_webhook_events WHERE stripe_event_id = "evt_..."

        alt Duplicate event
            DB-->>WebhookRepo: found
            WebhookRepo-->>WebhookController: true
            WebhookController-->>StripeAPI: 200 {received: true} (skip — already processed)
        else First occurrence
            DB-->>WebhookRepo: not found
            WebhookRepo-->>WebhookController: false

            WebhookController->>WebhookRepo: save(ProcessedWebhookEvent("evt_...", "checkout.session.completed"))
            WebhookRepo->>DB: INSERT INTO processed_webhook_events (stripe_event_id, event_type)
            Note over DB: UNIQUE constraint on stripe_event_id guards concurrent duplicates

            WebhookController->>BillingService: activate(sessionId, stripeSubId, customerId, periodStart, periodEnd)
            BillingService->>SubscriptionRepo: findActiveByBusinessId(tenantId)
            SubscriptionRepo->>DB: SELECT * FROM subscriptions WHERE business_id = :tenantId
            DB-->>SubscriptionRepo: Subscription
            SubscriptionRepo-->>BillingService: subscription

            BillingService->>DB: UPDATE subscriptions SET<br/>status=ACTIVE, plan_id=starterId,<br/>stripe_subscription_id=...,<br/>current_period_start=..., current_period_end=...,<br/>updated_at=now()<br/>WHERE business_id = :tenantId

            BillingService-->>WebhookController: done
            WebhookController-->>StripeAPI: 200 {received: true}
        end
    end
```

---

## Flow 3 — Quota Enforcement (Knowledge Base Creation)

```mermaid
sequenceDiagram
    autonumber

    participant Client
    participant JwtAuthFilter
    participant TenantContext
    participant KbController as KnowledgeBaseController
    participant KbService as KnowledgeBaseService
    participant QuotaService
    participant SubscriptionService
    participant SubscriptionRepo as SubscriptionRepository
    participant KbRepo as KnowledgeBaseRepository
    participant GlobalExHandler as GlobalExceptionHandler
    participant DB as PostgreSQL

    Client->>JwtAuthFilter: POST /api/v1/kb {name: "Support KB"}<br/>Authorization: Bearer <token>
    JwtAuthFilter->>TenantContext: setTenantId(claims.tenantId())
    JwtAuthFilter->>KbController: filterChain.doFilter()

    KbController->>KbService: create(tenantId, request)

    KbService->>QuotaService: checkKnowledgeBaseQuota(tenantId)

    QuotaService->>SubscriptionService: getCurrentPlan(tenantId)
    SubscriptionService->>SubscriptionRepo: findActiveByBusinessId(tenantId)
    SubscriptionRepo->>DB: SELECT * FROM subscriptions JOIN plans ON plan_id = plans.id
    DB-->>SubscriptionRepo: Subscription + Plan
    SubscriptionRepo-->>SubscriptionService: subscription
    SubscriptionService-->>QuotaService: Plan{slug:"free", maxKnowledgeBases:1}

    QuotaService->>SubscriptionService: isActivePaid(tenantId)
    SubscriptionService-->>QuotaService: false (TRIALING on free ≡ paid grace)

    QuotaService->>KbRepo: countByBusinessId(tenantId)
    KbRepo->>DB: SELECT COUNT(*) FROM knowledge_bases WHERE business_id = :tenantId
    DB-->>KbRepo: 1

    Note over QuotaService: isPaid=false → limit = maxKnowledgeBases = 1<br/>current(1) >= limit(1) → BLOCK

    QuotaService->>KbService: throws QuotaExceededException("knowledge_bases", 1, 1)
    KbService->>GlobalExHandler: exception propagates
    GlobalExHandler-->>Client: 402 Payment Required<br/>{success:false, error:"Knowledge base limit reached (1/1). Upgrade to add more.", upgradeUrl:"/billing/plans"}

    Note over Client: If tenant were ACTIVE (paid): limit = 1 * 1.1 = 1 (floor) → same result<br/>On Starter plan (max=3): limit=3, current=1 → ALLOW
```

---

## Flow 4 — Trial Expiry Scheduler (02:00 daily)

```mermaid
sequenceDiagram
    autonumber

    participant Cron as Spring Scheduler<br/>(cron: 0 0 2 * * *)
    participant TrialScheduler as TrialExpiryScheduler
    participant SubscriptionRepo as SubscriptionRepository
    participant PlanRepo as PlanRepository
    participant EmailService
    participant DB as PostgreSQL

    Cron->>TrialScheduler: processExpiredTrials()

    TrialScheduler->>SubscriptionRepo: findAllByStatusAndTrialEndsAtBefore(TRIALING, now())
    SubscriptionRepo->>DB: SELECT * FROM subscriptions<br/>WHERE status = 'TRIALING' AND trial_ends_at < now()
    DB-->>SubscriptionRepo: [Subscription{tenantId: A}, Subscription{tenantId: B}]
    SubscriptionRepo-->>TrialScheduler: expiredSubscriptions

    loop for each expired subscription
        TrialScheduler->>PlanRepo: findBySlug("free")
        PlanRepo->>DB: SELECT * FROM plans WHERE slug = 'free'
        DB-->>PlanRepo: Plan{id: freePlanId}

        TrialScheduler->>DB: UPDATE subscriptions SET<br/>plan_id = freePlanId,<br/>status = 'ACTIVE',<br/>updated_at = now()<br/>WHERE id = :subscriptionId

        TrialScheduler->>EmailService: sendTrialExpiredEmail(ownerEmail, upgradeUrl)
        Note over EmailService: @Async("processingExecutor")<br/>TenantContext propagated by TenantContextCopyingDecorator
    end

    TrialScheduler->>TrialScheduler: log.info("Expired {} trials", count)<br/>metrics.increment("trial.expired.total", count)
```

---

## Flow 5 — Reconciliation Scheduler (03:30 daily)

```mermaid
sequenceDiagram
    autonumber

    participant Cron as Spring Scheduler<br/>(cron: 0 30 3 * * *)
    participant RecScheduler as StripeReconciliationScheduler
    participant SubscriptionRepo as SubscriptionRepository
    participant StripeService
    participant BillingService
    participant Metrics as MeterRegistry
    participant StripeAPI as Stripe API
    participant DB as PostgreSQL

    Cron->>RecScheduler: run()

    RecScheduler->>SubscriptionRepo: findAllByStatusIn([ACTIVE, PAST_DUE, TRIALING])
    SubscriptionRepo->>DB: SELECT * FROM subscriptions WHERE status IN ('ACTIVE','PAST_DUE','TRIALING')
    DB-->>SubscriptionRepo: subscriptions
    SubscriptionRepo-->>RecScheduler: subscriptions (may include null stripeSubscriptionId)

    loop for each subscription
        alt stripeSubscriptionId is null
            RecScheduler->>RecScheduler: skip (trial not yet converted to Stripe sub)
        else has stripeSubscriptionId
            RecScheduler->>StripeService: retrieveSubscription(stripeSubscriptionId)
            StripeService->>StripeAPI: GET /v1/subscriptions/{id}
            StripeAPI-->>StripeService: Stripe.Subscription{status, current_period_end}
            StripeService-->>RecScheduler: stripeSub

            RecScheduler->>RecScheduler: compare dbStatus vs stripeStatus

            alt statuses match
                RecScheduler->>RecScheduler: no-op
            else mismatch detected
                RecScheduler->>RecScheduler: log.warn("Reconciliation mismatch tenantId={} dbStatus={} stripeStatus={}")
                RecScheduler->>BillingService: syncFromStripe(stripeSub)
                BillingService->>DB: UPDATE subscriptions SET status=..., current_period_end=..., updated_at=now()
                RecScheduler->>Metrics: increment("stripe.reconciliation.mismatch")
            end
        end
    end

    RecScheduler->>RecScheduler: log.info("Reconciliation complete. Checked: {}, Mismatches: {}", total, mismatches)
```

## Key Notes

- **Flow 1 + 2 are always paired** — the checkout API creates the Stripe session; `GET /billing/success` only acknowledges receipt. The DB update only happens in Flow 2 (webhook). Never activate on the success redirect.
- **Idempotency in Flow 2** runs at two levels: `existsByStripeEventId` check (application level) + `UNIQUE` DB constraint (database level). The constraint catches concurrent duplicate deliveries that both pass the application check.
- **Flow 3 quota logic** — `isPaid=true` → limit = `max * 1.1` (10% grace); `isPaid=false` or `PAST_DUE` → limit = `max` exactly (hard block). `TRIALING` counts as paid.
- **Flow 4 (02:00) runs before Flow 5 (03:30)** — this ordering is intentional. Trials are expired first so reconciliation sees the correct `ACTIVE` (Free) status, not `TRIALING`.
- **TenantContext in schedulers** — schedulers are not HTTP-scoped, so `TenantContext` is `null`. Repositories called from schedulers must use native queries that bypass the Hibernate tenant filter (as in `SubscriptionRepository.findActiveByBusinessId`).
- **Email in Flow 4 is `@Async`** — uses `processingExecutor` bean wired with `TenantContextCopyingDecorator`. If the tenant context is needed in the email task, it must be captured before the async handoff.
