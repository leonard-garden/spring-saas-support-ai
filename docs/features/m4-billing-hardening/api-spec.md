# API Spec — M4 Billing

**Version:** 1.0
**Date:** 2026-06-02
**Base URL:** `/api/v1/`

---

## Overview

| Property | Value |
|----------|-------|
| Auth | Bearer JWT in `Authorization` header (except public endpoints noted below) |
| Response envelope | `ApiResponse<T>` — `{"success": bool, "data": T, "error": string}` |
| Error format | RFC 7807 `ProblemDetail` |
| Content-Type | `application/json` (all endpoints except webhook) |
| Multi-tenancy | All tenant-scoped endpoints implicitly filter by `tenant_id` extracted from the JWT — callers never pass `tenantId` explicitly |

**Public endpoints (no JWT required):**
- `GET /api/v1/billing/plans`
- `POST /api/v1/billing/webhook`

---

## Endpoints

---

### GET /api/v1/billing/plans

**Description:** Returns the full plan catalogue with prices and feature limits. Used to render the pricing page.  
**Auth required:** NO  
**Roles:** PUBLIC

**Response 200 OK:**
```json
{
  "success": true,
  "data": [
    {
      "slug": "free",
      "name": "Free",
      "priceMonthly": 0,
      "maxKnowledgeBases": 1,
      "maxDocsPerKb": 5,
      "maxMessagesPerMonth": 100,
      "maxMembers": 1
    },
    {
      "slug": "starter",
      "name": "Starter",
      "priceMonthly": 29,
      "maxKnowledgeBases": 3,
      "maxDocsPerKb": 50,
      "maxMessagesPerMonth": 1000,
      "maxMembers": 3
    },
    {
      "slug": "pro",
      "name": "Pro",
      "priceMonthly": 99,
      "maxKnowledgeBases": 10,
      "maxDocsPerKb": 500,
      "maxMessagesPerMonth": 10000,
      "maxMembers": 10
    },
    {
      "slug": "business",
      "name": "Business",
      "priceMonthly": 299,
      "maxKnowledgeBases": null,
      "maxDocsPerKb": null,
      "maxMessagesPerMonth": null,
      "maxMembers": null
    }
  ],
  "error": null
}
```

*`null` limits mean unlimited.*

**Swagger annotations:**
```java
@Operation(summary = "List available subscription plans")
@ApiResponse(responseCode = "200", description = "Plan catalogue returned")
```

---

### GET /api/v1/billing/subscription

**Description:** Returns the current tenant's active subscription, including plan details, status, and billing period dates.  
**Auth required:** YES  
**Roles:** ADMIN, MEMBER

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "planSlug": "pro",
    "planName": "Pro",
    "status": "TRIAL",
    "currentPeriodStart": "2026-06-02T00:00:00Z",
    "currentPeriodEnd": "2026-06-16T00:00:00Z",
    "trialEndsAt": "2026-06-16T00:00:00Z",
    "pendingDowngradePlan": null,
    "cancelAtPeriodEnd": false
  },
  "error": null
}
```

`status` values: `TRIAL | ACTIVE | PAST_DUE | CANCELED`  
`pendingDowngradePlan` is non-null when a downgrade is scheduled (e.g., `"starter"`).

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 401  | Missing or invalid JWT |

**Swagger annotations:**
```java
@Operation(summary = "Get current tenant subscription")
@ApiResponse(responseCode = "200", description = "Subscription returned")
@ApiResponse(responseCode = "401", description = "Unauthorized")
```

---

### POST /api/v1/billing/checkout

**Description:** Creates a Stripe Checkout session for the specified plan and returns the hosted payment URL. Redirects the user to Stripe to complete payment.  
**Auth required:** YES  
**Roles:** ADMIN only

**Request body:**
```json
{
  "planSlug": "starter"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `planSlug` | string | Yes | Target plan: `starter`, `pro`, `business` |

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "checkoutUrl": "https://checkout.stripe.com/pay/cs_live_..."
  },
  "error": null
}
```

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 400  | `planSlug` is missing, blank, or `free` |
| 400  | Tenant already has an `ACTIVE` subscription (`AlreadySubscribedException`) |
| 401  | Missing or invalid JWT |
| 403  | Caller is not ADMIN |
| 422  | Stripe rejected the request (e.g., invalid price ID) |

**Idempotency:** Repeated calls within 24 h with same `tenantId` + `planSlug` return the same Stripe session (via `IdempotencyKey: {tenantId}:checkout:{date}`).

**Swagger annotations:**
```java
@Operation(summary = "Create Stripe Checkout session for plan upgrade")
@ApiResponse(responseCode = "200", description = "Checkout URL returned")
@ApiResponse(responseCode = "400", description = "Invalid plan or tenant already subscribed")
@ApiResponse(responseCode = "401", description = "Unauthorized")
@ApiResponse(responseCode = "403", description = "Admin role required")
```

---

### GET /api/v1/billing/success

**Description:** Post-checkout landing page handler. Called when Stripe redirects the user back after successful payment. Returns a confirmation payload the frontend uses to show a success screen.

> Stripe may redirect here before the `checkout.session.completed` webhook fires. Do NOT activate the subscription here — activation happens in the webhook handler. This endpoint only returns a "we're processing your payment" acknowledgement.

**Auth required:** YES  
**Roles:** ADMIN, MEMBER

**Query parameters:**
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `session_id` | string | Yes | Stripe Checkout session ID (`cs_live_...`) |

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "message": "Payment received. Your subscription will be activated within a few seconds.",
    "sessionId": "cs_live_..."
  },
  "error": null
}
```

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 400  | `session_id` missing |
| 401  | Missing or invalid JWT |

**Swagger annotations:**
```java
@Operation(summary = "Post-checkout success handler — returns activation acknowledgement")
@ApiResponse(responseCode = "200", description = "Checkout acknowledged")
@ApiResponse(responseCode = "400", description = "Missing session_id")
@ApiResponse(responseCode = "401", description = "Unauthorized")
```

---

### POST /api/v1/billing/cancel

**Description:** Schedules subscription cancellation at the current period end. The tenant retains access until `current_period_end`; subscription downgrades to Free when the `customer.subscription.deleted` webhook fires.  
**Auth required:** YES  
**Roles:** ADMIN only

**Request body:** *(empty)*

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "cancelAtPeriodEnd": true,
    "currentPeriodEnd": "2026-07-02T00:00:00Z"
  },
  "error": null
}
```

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 400  | Tenant is on Free plan (nothing to cancel) |
| 400  | Subscription is already scheduled for cancellation |
| 401  | Missing or invalid JWT |
| 403  | Admin role required |

**Swagger annotations:**
```java
@Operation(summary = "Cancel subscription at period end")
@ApiResponse(responseCode = "200", description = "Cancellation scheduled")
@ApiResponse(responseCode = "400", description = "No active subscription or already cancelling")
@ApiResponse(responseCode = "401", description = "Unauthorized")
@ApiResponse(responseCode = "403", description = "Admin role required")
```

---

### POST /api/v1/billing/upgrade

**Description:** Immediately upgrades the tenant to a higher plan with Stripe proration. Charge is applied at call time. DB is updated asynchronously via the `customer.subscription.updated` webhook.  
**Auth required:** YES  
**Roles:** ADMIN only

**Request body:**
```json
{
  "planSlug": "pro"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `planSlug` | string | Yes | Target plan (must be higher than current) |

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "message": "Upgrade initiated. Your plan will be updated within a few seconds.",
    "targetPlan": "pro"
  },
  "error": null
}
```

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 400  | `planSlug` is not higher than current plan |
| 400  | Tenant has no active Stripe subscription (still on trial without payment) |
| 401  | Missing or invalid JWT |
| 403  | Admin role required |
| 422  | Stripe API error |

**Swagger annotations:**
```java
@Operation(summary = "Upgrade to a higher plan (immediate with proration)")
@ApiResponse(responseCode = "200", description = "Upgrade initiated")
@ApiResponse(responseCode = "400", description = "Invalid target plan or no active subscription")
@ApiResponse(responseCode = "401", description = "Unauthorized")
@ApiResponse(responseCode = "403", description = "Admin role required")
```

---

### POST /api/v1/billing/downgrade

**Description:** Schedules a plan downgrade to take effect at the next billing period renewal. No immediate charge. Sets `pending_plan_id` in DB; the downgrade is applied when the `customer.subscription.updated` webhook fires at period end.  
**Auth required:** YES  
**Roles:** ADMIN only

**Request body:**
```json
{
  "planSlug": "starter"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `planSlug` | string | Yes | Target plan (must be lower than current) |

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "message": "Downgrade scheduled. Takes effect at period end.",
    "targetPlan": "starter",
    "effectiveAt": "2026-07-02T00:00:00Z"
  },
  "error": null
}
```

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 400  | `planSlug` is not lower than current plan |
| 400  | Tenant has no active Stripe subscription |
| 400  | A downgrade is already pending |
| 401  | Missing or invalid JWT |
| 403  | Admin role required |

**Swagger annotations:**
```java
@Operation(summary = "Schedule a downgrade to a lower plan (deferred to period end)")
@ApiResponse(responseCode = "200", description = "Downgrade scheduled")
@ApiResponse(responseCode = "400", description = "Invalid target plan or downgrade already pending")
@ApiResponse(responseCode = "401", description = "Unauthorized")
@ApiResponse(responseCode = "403", description = "Admin role required")
```

---

### POST /api/v1/billing/webhook

**Description:** Stripe webhook receiver. Validates the `Stripe-Signature` header using the `STRIPE_WEBHOOK_SECRET` before processing any event. Idempotent — duplicate events are silently skipped via `processed_webhook_events`.  
**Auth required:** NO (Stripe-signature validation replaces JWT auth)  
**Roles:** PUBLIC (Stripe only)

**Headers:**
| Header | Required | Description |
|--------|----------|-------------|
| `Stripe-Signature` | Yes | HMAC signature from Stripe (`t=...;v1=...`) |

**Request body:** Raw Stripe event JSON (must not be pre-parsed — signature validation requires the raw bytes)

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "received": true
  },
  "error": null
}
```

> Return `200` even for events you ignore — Stripe retries on non-2xx responses.

**Handled webhook events:**

| Event | Action |
|-------|--------|
| `checkout.session.completed` | `BillingService.activate()` — set `status=ACTIVE`, `current_period_start/end` |
| `customer.subscription.updated` | `BillingService.handleSubscriptionUpdated()` — apply upgrade/downgrade, sync period dates |
| `invoice.payment_succeeded` | Renew subscription, update `current_period_end` |
| `invoice.payment_failed` | Set `status=PAST_DUE`, send email warning to tenant owner |
| `customer.subscription.deleted` | Downgrade to Free plan (`status=ACTIVE`, `planId=FREE`) |

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 400  | Missing or invalid `Stripe-Signature` header |

**Swagger annotations:**
```java
@Operation(summary = "Stripe webhook receiver — validates signature before processing")
@ApiResponse(responseCode = "200", description = "Event received and processed (or skipped as duplicate)")
@ApiResponse(responseCode = "400", description = "Invalid Stripe signature")
```

---

## Quota Error Responses (Modified Existing Endpoints)

The following existing endpoints now return `402 Payment Required` when a quota is exceeded. This is enforced by `QuotaService` inside the service layer — the controller receives a `QuotaExceededException` which `GlobalExceptionHandler` maps to the response below.

**Affected endpoints:**
- `POST /api/v1/kb` — knowledge base limit
- `POST /api/v1/kb/{kbId}/documents/upload` — documents-per-KB limit
- `POST /api/v1/chat/{chatbotId}/stream` — monthly message limit

**Response 402 Payment Required:**
```json
{
  "success": false,
  "data": null,
  "error": "Knowledge base limit reached (1/1). Upgrade to add more.",
  "upgradeUrl": "/billing/plans"
}
```

> Note: `upgradeUrl` is an extra field on the `ApiResponse`-like error body. `GlobalExceptionHandler` adds it specifically for `QuotaExceededException`. Standard `ProblemDetail` fields are also returned in the response.

---

## Rate Limit Responses (All Endpoints)

All endpoints are subject to two-tier rate limiting via `RateLimitFilter`.

**Response 429 Too Many Requests:**
```json
{
  "success": false,
  "data": null,
  "error": "Rate limit exceeded. Retry after 30s"
}
```

**Headers returned:**
```
HTTP/1.1 429 Too Many Requests
Retry-After: 30
```

**Tier 1 — IP-based (pre-auth):**

| Path Pattern | Limit |
|-------------|-------|
| `/auth/**` | 20 req/min per IP |
| `/api/v1/widget/**` | 60 req/min per IP |
| All others | 200 req/min per IP |

**Tier 2 — Tenant-based (post-JWT):**

| Plan | Limit |
|------|-------|
| Free | 100 req/min |
| Starter | 500 req/min |
| Pro | 2,000 req/min |
| Business | Unlimited |

---

## Common Error Format

All non-quota errors follow RFC 7807 `ProblemDetail`:
```json
{
  "type": "https://problems.supportsaas.io/already-subscribed",
  "title": "Already Subscribed",
  "status": 400,
  "detail": "Already on plan: starter",
  "instance": "/api/v1/billing/checkout"
}
```

---

## DTO Reference

### PlanResponse (used in GET /plans)
```java
record PlanResponse(
    String slug,
    String name,
    int priceMonthly,
    Integer maxKnowledgeBases,   // null = unlimited
    Integer maxDocsPerKb,
    Integer maxMessagesPerMonth,
    Integer maxMembers
) {}
```

### SubscriptionResponse (used in GET /subscription)
```java
record SubscriptionResponse(
    String planSlug,
    String planName,
    String status,               // TRIAL | ACTIVE | PAST_DUE | CANCELED
    Instant currentPeriodStart,
    Instant currentPeriodEnd,
    Instant trialEndsAt,         // null if not on trial
    String pendingDowngradePlan, // null if no pending downgrade
    boolean cancelAtPeriodEnd
) {}
```

### CheckoutRequest
```java
record CheckoutRequest(
    @NotBlank String planSlug
) {}
```

### CheckoutResponse
```java
record CheckoutResponse(String checkoutUrl) {}
```

### UpgradeDowngradeRequest
```java
record UpgradeDowngradeRequest(
    @NotBlank String planSlug
) {}
```
