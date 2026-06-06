# Sequence Diagram — Billing Web UI

## Overview

These diagrams cover the full async billing lifecycle: page load, the Stripe Checkout upgrade flow
(including webhook processing), and the supporting flows for cancel, downgrade, and trial banner dismiss.

---

## Page Load Sequence

All four API calls are fired in parallel when the owner navigates to `/billing`.
`GET /billing/usage` and `GET /billing/invoices` are planned endpoints (M4 backend SRS).

```mermaid
sequenceDiagram
    actor Owner
    participant UI as React SPA /billing
    participant API as Spring Boot API
    participant DB as PostgreSQL

    Owner->>UI: navigate to /billing

    par fetch plans
        UI->>API: GET /api/v1/billing/plans
        API->>DB: planRepository.findAll() filter active=true
        DB-->>API: List of Plan rows
        API-->>UI: 200 ApiResponse list of PlanResponse
    and fetch subscription
        UI->>API: GET /api/v1/billing/subscription
        API->>DB: subscriptionRepository.findActiveByBusinessId(tenantId)
        DB-->>API: Subscription + Plan rows
        API-->>UI: 200 ApiResponse SubscriptionResponse
    and fetch usage
        UI->>API: GET /api/v1/billing/usage
        API->>DB: quota counters per resource
        DB-->>API: usage totals
        API-->>UI: 200 ApiResponse UsageResponse
    and fetch invoices
        UI->>API: GET /api/v1/billing/invoices
        API->>DB: invoice records (or Stripe API call)
        DB-->>API: List of InvoiceResponse
        API-->>UI: 200 ApiResponse list of InvoiceResponse
    end

    UI-->>Owner: render plan card, usage bars, invoice table
```

---

## Upgrade Flow (Stripe Checkout)

### Step 1 — Owner initiates checkout

```mermaid
sequenceDiagram
    actor Owner
    participant UI as React SPA /billing
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant Stripe as Stripe API

    Owner->>UI: click Upgrade button
    UI-->>Owner: open UpgradeDialog (plan selector)

    Owner->>UI: select plan and click Confirm

    UI->>API: POST /api/v1/billing/checkout body planSlug
    note over API: BillingController.createCheckout()<br/>requires OWNER role JWT

    API->>DB: planRepository.findBySlug(planSlug)
    DB-->>API: Plan entity with stripePriceId

    API->>DB: subscriptionRepository.findActiveByBusinessId(tenantId)
    DB-->>API: Subscription (status check - throws if ACTIVE)

    API->>Stripe: stripeService.getOrCreateCustomer(email, businessId)
    Stripe-->>API: Customer object with customer.id

    API->>DB: business.setStripeCustomerId(customer.id) save
    DB-->>API: saved

    API->>Stripe: stripeService.createCheckoutSession(customerId, stripePriceId, successUrl, cancelUrl, idempotencyKey)
    note over Stripe: idempotencyKey = businessId:checkout:TODAY
    Stripe-->>API: Session with session.url

    API-->>UI: 200 ApiResponse CheckoutResponse sessionUrl

    UI->>UI: window.location.href = sessionUrl
    note over UI: browser leaves the SPA and<br/>navigates to Stripe hosted page
```

### Step 2 — Owner pays on Stripe and lands on success page

```mermaid
sequenceDiagram
    actor Owner
    participant Browser
    participant UI as React SPA /billing/success
    participant API as Spring Boot API

    Browser->>Browser: Stripe Checkout page (hosted by Stripe)
    Owner->>Browser: complete payment on Stripe

    Browser->>UI: GET /billing/success?session_id=cs_xxx
    note over UI: frontend route - renders success banner

    UI->>API: GET /api/v1/billing/success?session_id=cs_xxx
    note over API: BillingController.checkoutSuccess()<br/>does NOT activate subscription<br/>activation happens via webhook only
    API-->>UI: 200 "Checkout initiated. Subscription will be activated after payment confirmation."

    UI-->>Owner: show success message and link back to /billing
```

---

## Webhook Processing

Stripe fires this asynchronously after payment. No JWT — Stripe calls the backend directly.

```mermaid
sequenceDiagram
    participant Stripe as Stripe
    participant WC as WebhookController
    participant SS as StripeService
    participant WS as WebhookServiceImpl
    participant PWER as ProcessedWebhookEventRepository
    participant SubRepo as SubscriptionRepository
    participant PlanRepo as PlanRepository

    Stripe->>WC: POST /api/v1/billing/webhook<br/>header Stripe-Signature: t=...,v1=...

    WC->>SS: stripeService.constructWebhookEvent(payload, sigHeader)
    note over SS: HMAC signature validation<br/>throws StripeGatewayException if invalid
    SS-->>WC: Event object

    alt invalid signature
        WC-->>Stripe: 400 Bad Request
    end

    WC->>WS: webhookService.handle(event)

    WS->>PWER: existsByStripeEventId(eventId)
    note over WS: idempotency gate
    PWER-->>WS: boolean

    alt event already processed
        WS-->>WC: return (skip)
        WC-->>Stripe: 200 received true
    end

    WS->>PWER: save ProcessedWebhookEvent(eventId, eventType)
    note over WS: record BEFORE handling to prevent<br/>duplicate execution on Stripe retry

    alt eventType = checkout.session.completed
        WS->>WS: handleCheckoutCompleted(event)
        WS->>SubRepo: findByStripeCustomerId(session.customer)
        SubRepo-->>WS: Subscription entity

        WS->>SS: stripeService.retrieveSubscription(session.subscription)
        SS-->>WS: Stripe Subscription with items and price

        WS->>PlanRepo: findByStripePriceId(priceId)
        PlanRepo-->>WS: local Plan entity

        WS->>SubRepo: save sub with status=ACTIVE, planId, stripeSubscriptionId, periodStart, periodEnd
        SubRepo-->>WS: saved
    else eventType = customer.subscription.updated
        WS->>WS: handleSubscriptionUpdated(event)
        WS->>SubRepo: findByStripeSubscriptionId(stripeSubId)
        SubRepo-->>WS: Subscription

        alt pendingPlanId set and period renewed
            note over WS: deferred downgrade: apply pendingPlanId to planId and clear pendingPlanId
        else upgrade or renewal
            WS->>PlanRepo: findByStripePriceId(priceId)
            PlanRepo-->>WS: local Plan
        end

        WS->>SubRepo: save with synced status, cancelAtPeriodEnd, periodStart, periodEnd
    else eventType = customer.subscription.deleted
        WS->>WS: handleSubscriptionDeleted(event)
        WS->>PlanRepo: findBySlug("free")
        PlanRepo-->>WS: free Plan
        WS->>SubRepo: save sub with planId=free, status=ACTIVE, cancelAtPeriodEnd=false, stripeSubscriptionId=null
    else eventType = invoice.payment_succeeded
        WS->>WS: handleInvoicePaymentSucceeded(event)
        WS->>SubRepo: findByStripeSubscriptionId(stripeSubId) then save status=ACTIVE and period timestamps
    else eventType = invoice.payment_failed
        WS->>WS: handleInvoicePaymentFailed(event)
        WS->>SubRepo: save status=PAST_DUE
        WS->>WS: asyncEmailSender.sendPaymentFailedAsync(ownerEmail)
    end

    WS-->>WC: return
    WC-->>Stripe: 200 received true
```

---

## Cancel Flow

```mermaid
sequenceDiagram
    actor Owner
    participant UI as React SPA /billing
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant Stripe as Stripe API

    Owner->>UI: click Cancel Plan button
    UI-->>Owner: show confirmation dialog

    Owner->>UI: confirm cancellation

    UI->>API: POST /api/v1/billing/cancel
    note over API: requires OWNER role JWT<br/>no request body

    API->>DB: subscriptionRepository.findActiveByBusinessId(tenantId)
    DB-->>API: Subscription

    note over API: throws if free plan, already cancelAtPeriodEnd=true, or no stripeSubscriptionId

    API->>Stripe: stripeService.cancelAtPeriodEnd(stripeSubId, idempotencyKey)
    note over Stripe: idempotencyKey = businessId:cancel:TODAY
    Stripe-->>API: updated Stripe Subscription

    API-->>UI: 200 ApiResponse CancelSubscriptionResponse cancelled=true, periodEnd=timestamp

    UI-->>Owner: show "Your plan will cancel on DATE" banner
```

---

## Downgrade Flow

```mermaid
sequenceDiagram
    actor Owner
    participant UI as React SPA /billing
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant Stripe as Stripe API

    Owner->>UI: click Downgrade button and select lower plan
    UI-->>Owner: show confirmation with "takes effect at period end" notice

    Owner->>UI: confirm downgrade

    UI->>API: POST /api/v1/billing/downgrade body planSlug
    note over API: requires OWNER role JWT

    API->>DB: subscriptionRepository.findActiveByBusinessId(tenantId)
    DB-->>API: Subscription

    note over API: throws if no stripeSubscriptionId, pendingPlanId already set,<br/>or target plan price not lower than current

    API->>DB: planRepository.findBySlug(planSlug)
    DB-->>API: target Plan with stripePriceId

    API->>Stripe: stripeService.scheduleSubscriptionUpdate(stripeSubId, targetPriceId, idempotencyKey)
    note over Stripe: idempotencyKey = businessId:downgrade:planSlug:TODAY<br/>sets subscription schedule for end of period
    Stripe-->>API: updated Stripe Subscription

    API->>DB: subscription.setPendingPlanId(targetPlan.id) save
    DB-->>API: saved

    API-->>UI: 200 ApiResponse DowngradeResponse planName, effectiveDate=periodEnd

    UI-->>Owner: show "Downgrading to PLAN on DATE" banner
    note over UI: actual plan switch happens when Stripe fires<br/>customer.subscription.updated after period renewal
```

---

## Trial Banner Dismiss

```mermaid
sequenceDiagram
    actor Owner
    participant UI as React SPA /billing

    note over UI: trial banner shown when subscription.status = TRIALING
    Owner->>UI: click dismiss on trial banner
    UI->>UI: localStorage.setItem("trial_banner_dismissed", "true")
    UI-->>Owner: banner hidden for this session
    note over UI: no API call — dismiss is purely client-side<br/>banner reappears after clearing local storage
```

---

## Key Notes

- `GET /billing/success` is an **acknowledgement only** — it does not activate the subscription. Activation is driven exclusively by the `checkout.session.completed` webhook.
- The webhook idempotency gate (`ProcessedWebhookEventRepository.existsByStripeEventId`) records the event **before** processing to prevent duplicate execution on Stripe retries.
- `POST /billing/checkout` uses `Propagation.NOT_SUPPORTED` — no outer transaction — because Stripe API calls must not be wrapped in a DB transaction.
- The downgrade is deferred: `pendingPlanId` is set in the DB immediately, but the plan switch is applied by `handleSubscriptionUpdated` only when the billing period renews (`newPeriodStart.isAfter(currentPeriodStart)`).
- `POST /billing/upgrade` calls `stripeService.updateSubscription` with immediate proration; the DB `planId` is synced by the resulting `customer.subscription.updated` webhook, not by the HTTP response.
- All billing endpoints (except `/webhook`) require a valid JWT with `OWNER` role. The webhook endpoint has no JWT — it is authenticated by Stripe HMAC signature only.
