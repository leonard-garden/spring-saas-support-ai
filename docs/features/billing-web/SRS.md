# Software Requirements Specification — Billing Web UI

**Version:** 1.0
**Date:** 2026-06-03
**Status:** Draft
**Author:** Leonard Trinh

---

## 1. Introduction

### 1.1 Purpose
This document specifies the functional and non-functional requirements for the **Billing Web UI** — the React frontend for M4 billing features. It is intended for:
- Frontend developers implementing the UI
- Backend developers adding supporting API endpoints (usage, invoices)
- AI agents implementing or reviewing this feature

### 1.2 Scope
The Billing Web UI provides authenticated Business Owners with a single `/billing` page to:
- View their current subscription plan and status
- Monitor resource usage against plan limits
- Upgrade, downgrade, or cancel their subscription
- View payment history and download invoices

It is part of the `spring-saas-support-ai` admin React frontend. It does **not** include:
- The embeddable chat widget UI
- Admin super-user billing management
- Real-time usage streaming or webhooks to the frontend
- Mobile-native views

### 1.3 Definitions & Acronyms
| Term | Definition |
|------|-----------|
| JWT  | JSON Web Token — used for authenticated API calls |
| SPA  | Single Page Application — the React admin frontend |
| CSP  | Current period / Stripe concept for billing cycle |
| TRIALING | Subscription status during the 14-day free Pro trial |
| ACTIVE | Paid subscription in good standing |
| PAST_DUE | Payment failed, service still running with grace |
| CANCELED | Subscription ended (active until period end) |
| Grace period | 10% overage allowed above limit for paid plans |
| Stripe Checkout | Hosted Stripe payment page for upgrades |

### 1.4 References
- [CLAUDE.md](../../../CLAUDE.md)
- [Architecture](../../../.claude/memory/architecture.md)
- [Tech Stack](../../../.claude/memory/tech-stack.md)
- [M4 Backend SRS](../m4-billing-hardening/SRS.md)
- [M4 API Spec](../m4-billing-hardening/api-spec.md)

---

## 2. Overall Description

### 2.1 Product Perspective
The Billing Web UI is a route (`/billing`) within the existing React SPA. It sits behind `ProtectedRoute` and is accessible only to authenticated users. It communicates exclusively with the Spring Boot backend at `http://localhost:8081/api/v1` (configurable via env var).

The page is the **only** surface where owners manage their subscription — no billing logic lives in other pages.

### 2.2 User Classes & Characteristics
| User Class | Description | Access |
|-----------|-------------|--------|
| Business Owner (OWNER) | Created the account; primary billing contact | Full access to /billing |
| Member | Invited by owner; uses KB and chat | No access to /billing |
| End User | Widget customer | No access (public widget only) |

Only `OWNER` role may access `/billing`. Members attempting to navigate there are redirected.

### 2.3 Operating Environment
- React 18 + TypeScript (strict mode)
- React Router v6 (`/billing` route inside `AppShell`)
- TanStack Query v5 (data fetching, caching)
- Tailwind CSS + shadcn/ui component library
- Fonts: DM Sans (body), Fraunces (display/headings)
- Design tokens: warm stone palette, amber primary (`hsl(38 92% 50%)`)
- Vite dev server on `:5173`, backend on `:8081`

### 2.4 Design Constraints
- **No Lombok** in backend DTO records (Java 21 records)
- **No H2** in tests — Testcontainers only
- **No JdbcTemplate** — `@Modifying @Query(nativeQuery=true)` on JpaRepository
- **No bare `@Async`** — always `@Async("processingExecutor")`
- Multi-tenancy: all business entities extend `TenantEntity` with `business_id`
- Virtual threads disabled (`spring.threads.virtual.enabled=false`)
- Frontend: no `any` types, no `console.log` in production code
- API calls must use the shared axios instance from `lib/api.ts` (handles JWT refresh)
- All API modules follow the pattern in `lib/memberApi.ts` / `lib/kbApi.ts`

### 2.5 Assumptions & Dependencies
- Backend billing endpoints already implemented (M4 backend): `GET /billing/plans`, `GET /billing/subscription`, `POST /billing/checkout`, `POST /billing/cancel`, `POST /billing/upgrade`, `POST /billing/downgrade`
- Two new backend endpoints **required** before full implementation: `GET /billing/usage` and `GET /billing/invoices`
- Stripe Checkout is in test mode locally; `VITE_STRIPE_PUBLISHABLE_KEY` not required (redirect-based flow, not embedded)
- `GET /billing/success?session_id=xxx` backend endpoint exists for post-checkout confirmation
- shadcn/ui `Progress`, `Card`, `Badge`, `Button`, `Dialog` components already installed

---

## 3. Functional Requirements

### 3.1 Page Access & Routing

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-001 | `/billing` route accessible only to OWNER role | High | Member role → redirected to `/dashboard`; unauthenticated → `/login` |
| FR-002 | Sidebar shows "Billing" nav link with `CreditCard` icon | High | Link visible when logged in; active state highlighted when on `/billing` |
| FR-003 | `/billing/success` page shown after Stripe checkout redirect | High | Page loads with `?session_id=xxx` param; shows plan name and "Go to Dashboard" button |

### 3.2 Subscription Status Card

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-010 | Display current plan name, price, and status badge | High | Badge shows TRIALING/ACTIVE/PAST_DUE/CANCELED with correct color |
| FR-011 | TRIALING status shows trial countdown in days | High | "X days left" text present; computed from `trialEndsAt` field |
| FR-012 | Show current period end date | High | "Period ends: Jun 30, 2026" format |
| FR-013 | Show "Cancels at period end" warning when `cancelAtPeriodEnd=true` | Med | Warning text visible in red; no Cancel button shown |
| FR-014 | "Manage Payment Method" button links to Stripe Customer Portal | Med | Button present; opens Stripe portal URL (from `POST /billing/portal-session` or external link) |
| FR-015 | "Cancel Subscription" button visible when subscription is cancellable | High | Hidden when `cancelAtPeriodEnd=true` or `status=CANCELED` |

### 3.3 Status Banners

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-020 | Trial banner shown when `status=TRIALING` | High | Amber banner with days remaining + "Upgrade Now" CTA button |
| FR-021 | Past-due banner shown when `status=PAST_DUE` | High | Red banner with "Update Payment" button |
| FR-022 | Banners are dismissible (no persistence — reappear on reload) | Low | Close (×) button on each banner |

### 3.4 Usage Meters

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-030 | Display usage for: Knowledge Bases, Documents, Messages, Members | High | All 4 meters visible with `used / limit` counts |
| FR-031 | Progress bar color: green (<80%), amber (80–94%), red (≥95%) | High | Color changes automatically at thresholds |
| FR-032 | Unlimited limits (`-1`) display "Unlimited" — no progress bar | High | Business plan shows "Unlimited" text, no bar |
| FR-033 | "Resets on {date}" label shown in usage card header | Med | Date matches `currentPeriodEnd` from subscription |
| FR-034 | Grace period note: "Paid plans include 10% grace period" | Low | Static informational text below card title |
| FR-035 | Usage data fetched from `GET /billing/usage` (new endpoint) | High | Real data loaded; skeleton shown while loading |

### 3.5 Plans Grid

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-040 | Display all 4 plans: Free ($0), Starter ($29), Pro ($99), Business ($299) | High | All 4 cards visible with correct prices and features |
| FR-041 | Current plan card shows "Current Plan" button (disabled) + status badge | High | No upgrade/downgrade button on current plan |
| FR-042 | Higher-priced plans show "Upgrade to {Plan}" primary button | High | Button triggers upgrade dialog |
| FR-043 | Lower-priced plans show "Downgrade to {Plan}" ghost button | High | Button triggers downgrade dialog |
| FR-044 | Most popular plan (Pro) has visual highlight + "Most Popular" badge | Med | Ring border + badge visible |
| FR-045 | Plans data fetched from `GET /billing/plans` | High | Real plan data with feature list displayed |

### 3.6 Upgrade Flow

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-050 | Clicking Upgrade opens confirmation dialog | High | Dialog shows: current plan, target plan, prices |
| FR-051 | "Continue to Stripe →" calls `POST /billing/checkout` then redirects | High | User redirected to Stripe Checkout URL |
| FR-052 | Loading state shown while awaiting `/billing/checkout` response | Med | Button shows spinner; disabled during request |
| FR-053 | API error (4xx/5xx) shown as toast/inline error in dialog | High | Error message visible; dialog stays open |

### 3.7 Downgrade Flow

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-060 | Clicking Downgrade opens warning dialog | High | Dialog lists what user will lose (features removed) |
| FR-061 | Warning: "Takes effect at next billing cycle" | High | Text visible in dialog |
| FR-062 | "Confirm Downgrade" calls `POST /billing/downgrade` | High | `pendingPlanId` set in backend; dialog closes on success |
| FR-063 | After downgrade scheduled, plans grid shows pending indicator | Med | "Downgrade pending at {date}" note on current plan card |

### 3.8 Cancel Flow

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-070 | Cancel button opens confirmation dialog | High | Dialog shows period-end date in plain language |
| FR-071 | Dialog warns: account downgrades to Free after cancellation | High | "Your account will move to the Free plan" visible |
| FR-072 | "Cancel at Period End" calls `POST /billing/cancel` | High | `cancelAtPeriodEnd=true` in backend; dialog closes |
| FR-073 | After cancel confirmed, subscription card updates immediately | High | "Cancels at period end" warning appears without full page reload |

### 3.9 Payment History

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-080 | Invoice table shows: Date, Description, Amount, Status, PDF link | High | All columns present with correct data |
| FR-081 | Status badges: "Paid" (green), "Failed" (red) | High | Correct color per invoice status |
| FR-082 | PDF link downloads invoice from Stripe | Med | Link opens invoice PDF (Stripe-hosted URL) |
| FR-083 | Empty state shown when no invoices exist | Med | "No invoices yet" message visible |
| FR-084 | Invoices fetched from `GET /billing/invoices` (new endpoint) | High | Real data; most recent first; max 12 shown |

---

## 4. Non-Functional Requirements

### 4.1 Performance
- Initial page load (all API calls complete): < 1.5s on local dev
- Plan cards render immediately from `GET /billing/plans` cache (stale-while-revalidate)
- Usage meters show skeleton loaders — no layout shift during load
- Dialogs open instantly (no API call until user confirms)

### 4.2 Security
- All requests sent with `Authorization: Bearer <access_token>` via axios interceptor in `lib/api.ts`
- JWT refresh handled transparently by existing refresh-lock interceptor
- No Stripe secret key in frontend — only publishable key if needed
- Stripe session IDs in URL params are not stored in localStorage
- Role guard: 403 from backend on Member-role access to billing endpoints

### 4.3 Reliability
- TanStack Query retries failed requests once (default `retry: false` overridden to `retry: 1` for billing)
- Optimistic UI: cancel/downgrade confirmation updates local state immediately, rolls back on API error
- Network errors surface as user-readable messages (not raw `axios error` strings)

### 4.4 Accessibility
- All interactive elements keyboard-navigable (Tab/Enter/Space)
- Dialog focus trapped while open; returns to trigger on close
- Progress bars include `aria-label` with text values
- Status badges include accessible color + icon (not color alone)

---

## 5. Use Cases

### UC-001: Owner upgrades from Starter to Pro

**Actor:** Business Owner (OWNER role)
**Preconditions:** Logged in, subscription status = ACTIVE, plan = Starter
**Main Flow:**
1. Owner navigates to `/billing`
2. Page loads: current plan card shows Starter, usage meters show current usage
3. Owner clicks "Upgrade to Pro" on the Pro plan card
4. Upgrade dialog opens: shows Starter ($29) → Pro ($99), "Continue to Stripe →" button
5. Owner clicks "Continue to Stripe →"
6. Frontend calls `POST /billing/checkout {"planSlug": "pro"}`
7. Browser redirects to Stripe Checkout URL
8. Owner completes payment on Stripe
9. Stripe redirects to `/billing/success?session_id=xxx`
10. Success page shown; Stripe webhook fires `checkout.session.completed` → backend activates Pro

**Alternate Flows:**
- A1: API returns error → toast shown, dialog stays open, no redirect
- A2: Owner already ACTIVE → `POST /billing/checkout` returns 400 → error shown

**Postconditions:** Subscription status = ACTIVE, plan = Pro (after webhook processed)

---

### UC-002: Owner schedules downgrade to Starter

**Actor:** Business Owner (OWNER role)
**Preconditions:** Logged in, subscription = ACTIVE, plan = Pro
**Main Flow:**
1. Owner navigates to `/billing`
2. Clicks "Downgrade to Starter" on Starter plan card
3. Downgrade dialog opens with warning: reduced limits + "takes effect next cycle"
4. Owner clicks "Confirm Downgrade"
5. Frontend calls `POST /billing/downgrade {"planSlug": "starter"}`
6. Backend sets `pendingPlanId = starter`, returns 200
7. Dialog closes; plan card shows "Downgrade to Starter pending at {date}"

**Alternate Flows:**
- A1: `pendingPlanId` already set → API returns 400 "pending downgrade already scheduled" → error shown

**Postconditions:** `pendingPlanId` set; actual downgrade applied by webhook on next renewal

---

### UC-003: Owner cancels subscription

**Actor:** Business Owner (OWNER role)
**Preconditions:** Logged in, subscription = ACTIVE, `cancelAtPeriodEnd = false`
**Main Flow:**
1. Owner navigates to `/billing`
2. Clicks "Cancel Subscription" in current plan card
3. Cancel dialog opens: shows period-end date + "account moves to Free plan"
4. Owner clicks "Cancel at Period End"
5. Frontend calls `POST /billing/cancel`
6. Backend sets `cancelAtPeriodEnd = true` via Stripe
7. Dialog closes; subscription card shows "Cancels at period end" in red

**Alternate Flows:**
- A1: Subscription has no `stripeSubscriptionId` → 400 → error shown

**Postconditions:** `cancelAtPeriodEnd = true`; access continues until `currentPeriodEnd`

---

### UC-004: Owner monitors quota usage

**Actor:** Business Owner (OWNER role)
**Preconditions:** Logged in, any subscription status
**Main Flow:**
1. Owner navigates to `/billing`
2. Usage card loads with 4 meters from `GET /billing/usage`
3. Owner sees Knowledge Bases (3/10), Documents (127/500), Messages (2,340/10,000), Members (4/10)
4. All bars green — within limits

**Alternate Flows:**
- A1: Messages at 9,200/10,000 (≥80%) → amber bar + amber number
- A2: Messages at 9,700/10,000 (≥95%) → red bar + red number
- A3: Business plan → "Unlimited" text, no bars

**Postconditions:** Owner informed of current usage; can make informed upgrade decision

---

## 6. External Interface Requirements

### 6.1 Backend API Interfaces (consumed by frontend)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/billing/plans` | Public | List 4 plans with features and pricing |
| GET | `/billing/subscription` | OWNER JWT | Current subscription: status, plan, dates |
| GET | `/billing/usage` | OWNER JWT | **New endpoint** — used/limit for KBs, docs, messages, members |
| GET | `/billing/invoices` | OWNER JWT | **New endpoint** — Stripe invoice list (max 12, desc order) |
| POST | `/billing/checkout` | OWNER JWT | Create Stripe Checkout Session → `{sessionUrl}` |
| POST | `/billing/cancel` | OWNER JWT | Cancel at period end → `{cancelAtPeriodEnd, currentPeriodEnd}` |
| POST | `/billing/upgrade` | OWNER JWT | Immediate upgrade → Stripe update |
| POST | `/billing/downgrade` | OWNER JWT | Deferred downgrade → set `pendingPlanId` |
| GET | `/billing/success` | OWNER JWT | Post-checkout confirmation |

**New endpoint specs required:**

`GET /billing/usage` response:
```json
{
  "success": true,
  "data": {
    "knowledgeBases": { "used": 3, "limit": 10 },
    "documents":      { "used": 127, "limit": 500 },
    "messages":       { "used": 2340, "limit": 10000 },
    "members":        { "used": 4, "limit": 10 }
  }
}
```
Limits `-1` = unlimited (Business plan).

`GET /billing/invoices` response:
```json
{
  "success": true,
  "data": [
    {
      "id": "in_xxx",
      "date": "2026-05-01",
      "description": "Pro Plan — May 2026",
      "amount": 99.00,
      "status": "paid",
      "pdfUrl": "https://invoice.stripe.com/..."
    }
  ]
}
```

### 6.2 Frontend File Interfaces

New files to create:
```
frontend/src/
├── lib/billingApi.ts          — All billing API calls (plans, subscription, usage, invoices, checkout, cancel, upgrade, downgrade)
├── types/billing.ts           — Plan, Subscription, UsageStats, Invoice TypeScript interfaces
├── hooks/useBilling.ts        — TanStack Query hooks: usePlans, useSubscription, useUsage, useInvoices
├── components/billing/
│   ├── CurrentPlanCard.tsx    — Status card (extracted from BillingPage)
│   ├── UsageCard.tsx          — Usage meters (extracted from BillingPage)
│   ├── PlanGrid.tsx           — 4-column plans grid
│   ├── PlanCard.tsx           — Individual plan card
│   ├── UpgradeDialog.tsx      — Confirmation + Stripe redirect
│   ├── DowngradeDialog.tsx    — Warning + deferred confirm
│   └── CancelDialog.tsx       — Period-end cancel confirm
├── pages/BillingPage.tsx      — Main page (orchestrates components)
└── pages/BillingSuccessPage.tsx — Post-Stripe redirect landing
```

Modified files:
```
frontend/src/App.tsx           — Add /billing and /billing/success routes
frontend/src/components/layout/Sidebar.tsx — Add Billing nav link
```

---

## 7. Out of Scope

- **Invoice pagination** — show latest 12 only; no page controls in v1
- **Usage history graphs** — time-series charts of past usage (future M5)
- **Per-member usage breakdown** — who uploaded most / who sent most messages (requires UsageRecord entity, future)
- **Active session count** — JWT is stateless; no session tracking without Redis store
- **Spam/rate-limit violation dashboard** — Bucket4j is in-memory; no persistent violation log
- **Dark mode** — design system is light-only
- **Member access to billing** — OWNER only; no read-only billing view for Members
- **Stripe Customer Portal** — "Manage Payment Method" links to external Stripe URL only (no embedded portal in v1)
- **Proration calculation display** — upgrade cost difference not computed on frontend
- **Email preferences / notification settings** — separate feature
