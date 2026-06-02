# Flowchart Diagram — M4 Billing

## Overview

Four business logic decision trees that drive M4 behavior: quota enforcement (the most complex, with grace-period branching), webhook event routing, upgrade/downgrade validation, and the full subscription state machine.

---

## Flowchart 1 — Quota Enforcement (QuotaServiceImpl)

```mermaid
flowchart TD
    A([checkQuota called\nresource + tenantId]) --> B[Load subscription\ngetCurrentSubscription]
    B --> C[Load plan\ngetCurrentPlan]
    C --> D[Count current usage\nDB query]
    D --> E{subscription.status?}

    E -->|TRIALING| F[isPaid = true]
    E -->|ACTIVE| F
    E -->|PAST_DUE| G[isPaid = false]
    E -->|CANCELED| G
    E -->|no subscription| G

    F --> H{plan.slug = 'business'?}
    H -->|yes| PASS([✅ Allow — unlimited plan])
    H -->|no| I[effectiveLimit =\nplan.maxResource × 1.1\nfloor to int]

    G --> J[effectiveLimit =\nplan.maxResource\nno grace]

    I --> K{current >= effectiveLimit?}
    J --> K

    K -->|no| PASS
    K -->|yes| FAIL([❌ throw QuotaExceededException\nmetric, limit, current\n→ HTTP 402])

    style PASS fill:#dcfce7,stroke:#16a34a,color:#14532d
    style FAIL fill:#fee2e2,stroke:#dc2626,color:#7f1d1d
```

---

## Flowchart 2 — Webhook Event Router (WebhookController)

```mermaid
flowchart TD
    START([POST /billing/webhook\nraw body + Stripe-Signature]) --> SIG{Validate\nStripe-Signature}

    SIG -->|invalid| SIGFAIL([HTTP 400\nSignatureVerificationException])
    SIG -->|valid| IDEM{stripe_event_id\nalready in DB?}

    IDEM -->|yes| SKIP([HTTP 200 — skip\nduplicate event])
    IDEM -->|no| STORE[INSERT processed_webhook_events\nstrike_event_id, event_type]

    STORE --> ROUTE{event.type?}

    ROUTE -->|checkout.session.completed| A1[Extract:\nstripeSubId\nstripeCustomerId\nperiodStart / periodEnd]
    A1 --> A2[BillingService.activate\nstatus = ACTIVE\nplanId = selected plan\nstripe fields set]
    A2 --> OK

    ROUTE -->|customer.subscription.updated| B1{pending_plan_id\nscheduled?}
    B1 -->|downgrade pending\n+ period just ended| B2[apply downgrade:\nplanId = pendingPlanId\npending_plan_id = NULL]
    B1 -->|upgrade or renewal| B3[update planId\ncurrent_period_end\nupdated_at]
    B2 --> OK
    B3 --> OK

    ROUTE -->|invoice.payment_succeeded| C1[status = ACTIVE\ncurrent_period_end updated\nupdated_at = now]
    C1 --> OK

    ROUTE -->|invoice.payment_failed| D1[status = PAST_DUE\nupdated_at = now]
    D1 --> D2[send payment-failed email\n@Async]
    D2 --> OK

    ROUTE -->|customer.subscription.deleted| E1[planId = FREE_PLAN_ID\nstatus = ACTIVE\ncancel_at_period_end = false\nupdated_at = now]
    E1 --> OK

    ROUTE -->|other event type| IGNORE([HTTP 200 — ignore\nunhandled event type])

    OK([HTTP 200\nreceived: true])

    style OK fill:#dcfce7,stroke:#16a34a,color:#14532d
    style SKIP fill:#fef9c3,stroke:#ca8a04,color:#713f12
    style IGNORE fill:#fef9c3,stroke:#ca8a04,color:#713f12
    style SIGFAIL fill:#fee2e2,stroke:#dc2626,color:#7f1d1d
```

---

## Flowchart 3 — Upgrade / Downgrade Validation (BillingServiceImpl)

```mermaid
flowchart TD
    REQ([POST /billing/upgrade\nor /billing/downgrade\n{planSlug}]) --> LOAD[Load current subscription\n+ current plan]

    LOAD --> NOSUB{stripeSubscriptionId\nexists?}
    NOSUB -->|no| NOSUB_FAIL([HTTP 400\nNo active Stripe subscription\nStill on trial without payment])
    NOSUB -->|yes| PLANEXIST{target plan\nexists in DB?}

    PLANEXIST -->|no| PLANFAIL([HTTP 400\nUnknown planSlug])
    PLANEXIST -->|yes| SAME{targetPlan ==\ncurrentPlan?}

    SAME -->|yes| SAMEFAIL([HTTP 400\nAlready on this plan])
    SAME -->|no| DIR{request type?}

    DIR -->|upgrade| UPGCHECK{targetPlan.priceMonthly >\ncurrentPlan.priceMonthly?}
    UPGCHECK -->|no| UPGFAIL([HTTP 400\nTarget plan is not higher])
    UPGCHECK -->|yes| UPG[StripeService.updateSubscription\nstripeSubId, newPriceId\nprorate = true\nCharge diff immediately]
    UPG --> UPGOK([HTTP 200\nUpgrade initiated\nDB updated via webhook])

    DIR -->|downgrade| DWNCHECK{targetPlan.priceMonthly <\ncurrentPlan.priceMonthly?}
    DWNCHECK -->|no| DWNFAIL([HTTP 400\nTarget plan is not lower])
    DWNCHECK -->|yes| PENDING{pending_plan_id\nalready set?}

    PENDING -->|yes| PENDFAIL([HTTP 400\nDowngrade already pending])
    PENDING -->|no| DWN[StripeService.scheduleSubscriptionUpdate\nstripeSubId, newPriceId\nNo immediate charge]
    DWN --> DWNDB[DB: pending_plan_id = targetPlanId]
    DWNDB --> DWNOK([HTTP 200\nDowngrade scheduled\neffectiveAt = currentPeriodEnd])

    style UPGOK fill:#dcfce7,stroke:#16a34a,color:#14532d
    style DWNOK fill:#dcfce7,stroke:#16a34a,color:#14532d
    style NOSUB_FAIL fill:#fee2e2,stroke:#dc2626,color:#7f1d1d
    style PLANFAIL fill:#fee2e2,stroke:#dc2626,color:#7f1d1d
    style SAMEFAIL fill:#fee2e2,stroke:#dc2626,color:#7f1d1d
    style UPGFAIL fill:#fee2e2,stroke:#dc2626,color:#7f1d1d
    style DWNFAIL fill:#fee2e2,stroke:#dc2626,color:#7f1d1d
    style PENDFAIL fill:#fee2e2,stroke:#dc2626,color:#7f1d1d
```

---

## Flowchart 4 — Subscription State Machine

```mermaid
flowchart LR
    INIT([Signup]) -->|SubscriptionService.createTrial| TRIAL

    TRIAL["🟡 TRIALING\nplanId = PRO\ntrialEndsAt = now+14d\nno Stripe sub yet"]
    ACTIVE["🟢 ACTIVE\nStripe sub attached\ncurrent_period_end set"]
    PASTDUE["🟠 PAST_DUE\nAccess restricted\nhard quota block"]
    CANCELED["🔴 CANCELED\nstripe sub deleted"]
    FREE["⬜ ACTIVE (Free)\nplanId = FREE\nno Stripe sub"]

    TRIAL -->|"checkout.session.completed\nwebhook"| ACTIVE
    TRIAL -->|"TrialExpiryScheduler\ntrial_ends_at < now()"| FREE

    ACTIVE -->|"invoice.payment_succeeded\nwebhook → renew"| ACTIVE
    ACTIVE -->|"invoice.payment_failed\nwebhook"| PASTDUE
    ACTIVE -->|"POST /billing/cancel\n+ customer.subscription.deleted\nwebhook at period end"| FREE
    ACTIVE -->|"POST /billing/upgrade\n+ customer.subscription.updated"| ACTIVE
    ACTIVE -->|"POST /billing/downgrade\n+ customer.subscription.updated\nat period end"| ACTIVE

    PASTDUE -->|"invoice.payment_succeeded\n(retry succeeds)"| ACTIVE
    PASTDUE -->|"customer.subscription.deleted\n(never paid)"| FREE

    FREE -->|"POST /billing/checkout\nnew subscription"| ACTIVE

    CANCELED -->|"POST /billing/checkout\nnew subscription"| ACTIVE

    style TRIAL fill:#fef9c3,stroke:#ca8a04,color:#713f12
    style ACTIVE fill:#dcfce7,stroke:#16a34a,color:#14532d
    style PASTDUE fill:#ffedd5,stroke:#ea580c,color:#7c2d12
    style CANCELED fill:#fee2e2,stroke:#dc2626,color:#7f1d1d
    style FREE fill:#f1f5f9,stroke:#94a3b8,color:#334155
```

## Key Notes

- **Flowchart 1 (Quota):** `TRIALING` counts as paid (gets 10% grace). `PAST_DUE` is treated identically to `Free` — hard block at limit. `business` plan short-circuits to allow before any count is loaded.
- **Flowchart 2 (Webhook):** The `UNIQUE` constraint on `stripe_event_id` is the safety net — even if two threads both pass the `existsByStripeEventId` check concurrently, only one `INSERT` succeeds; the other gets a `DataIntegrityViolationException` which the controller catches and treats as a skip.
- **Flowchart 3 (Upgrade/Downgrade):** Upgrade and downgrade are validated by comparing `priceMonthly` — not by ordinal/rank — to keep the logic decoupled from plan ordering assumptions.
- **Flowchart 4 (State machine):** There is no direct `TRIALING → CANCELED` transition. A trial that is never converted simply expires via the scheduler (→ `FREE`). The `CANCELED` state is only reached via Stripe's `customer.subscription.deleted` event.
- **`ACTIVE` self-loop on upgrade/downgrade** — these transitions keep `status = ACTIVE`; only `planId` (and optionally `pending_plan_id`) changes. Status does not change during plan switches.
