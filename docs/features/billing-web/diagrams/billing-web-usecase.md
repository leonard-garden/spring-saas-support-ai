# Use Case Diagram — Billing Web UI

## Overview
Shows all actors (Business Owner, Member, Stripe, Backend Scheduler) and their interactions with the Billing Web UI use cases. Highlights which flows are immediate vs. deferred, and which involve Stripe redirect.

## Diagram

```mermaid
flowchart TB
    %% ── Actors (outside system boundary) ────────────────────────
    Owner["Business Owner<br/>(OWNER role)"]
    Member["Member<br/>(MEMBER role)"]
    Stripe["Stripe<br/>(external)"]
    Scheduler["TrialExpiryScheduler<br/>(backend cron)"]

    %% ── System boundary ─────────────────────────────────────────
    subgraph BillingUI ["/billing — Billing Web UI"]
        direction TB

        subgraph View ["View and Monitor"]
            UC1(["View Current Plan and Status"])
            UC2(["Monitor Resource Usage"])
            UC3(["View Payment History"])
        end

        subgraph UpgradeFlow ["Upgrade Flow"]
            UC4(["Select Upgrade Plan"])
            UC5(["Confirm Upgrade in Dialog"])
            UC6(["Redirect to Stripe Checkout"])
            UC7(["View Success Page"])
        end

        subgraph DowngradeFlow ["Downgrade Flow"]
            UC8(["Select Downgrade Plan"])
            UC9(["Confirm Downgrade - deferred"])
        end

        subgraph CancelFlow ["Cancel Flow"]
            UC10(["Request Cancellation"])
            UC11(["Confirm Cancel at Period End"])
        end

        subgraph Mgmt ["Account Management"]
            UC12(["Manage Payment Method"])
            UC13(["Download Invoice PDF"])
        end
    end

    %% ── Denied access ───────────────────────────────────────────
    DENY["Access Denied — redirect /dashboard"]

    %% ── Owner interactions ───────────────────────────────────────
    Owner --> UC1
    Owner --> UC2
    Owner --> UC3
    Owner --> UC4
    Owner --> UC8
    Owner --> UC10
    Owner --> UC12
    Owner --> UC13

    %% ── Upgrade chain ────────────────────────────────────────────
    UC4 --> UC5
    UC5 --> UC6
    UC6 -->|"checkout.session.completed"| Stripe
    Stripe -->|"webhook: status=ACTIVE"| UC7

    %% ── Downgrade chain ──────────────────────────────────────────
    UC8 --> UC9
    UC9 -->|"pendingPlanId set"| UC1

    %% ── Cancel chain ─────────────────────────────────────────────
    UC10 --> UC11
    UC11 -->|"cancelAtPeriodEnd=true"| UC1

    %% ── Member blocked ───────────────────────────────────────────
    Member -->|"attempts /billing"| DENY

    %% ── Stripe links ─────────────────────────────────────────────
    UC12 -->|"external redirect"| Stripe
    UC13 -->|"PDF URL"| Stripe

    %% ── Scheduler side-effect ────────────────────────────────────
    Scheduler -->|"trial expired: downgrade to Free"| UC1

    %% ── Styles ───────────────────────────────────────────────────
    classDef actor fill:#f5f0e8,stroke:#c9a96e,color:#3d2b00
    classDef usecase fill:#fff,stroke:#d4a017,color:#1a1a1a
    classDef external fill:#e8f0fe,stroke:#4a7fcb,color:#1a3a6b
    classDef denied fill:#fef2f2,stroke:#dc2626,color:#7f1d1d

    class Owner,Member,Scheduler actor
    class UC1,UC2,UC3,UC4,UC5,UC6,UC7,UC8,UC9,UC10,UC11,UC12,UC13 usecase
    class Stripe external
    class DENY denied
```

## Key Notes

- **OWNER only** — Member role is blocked at the route level (`ProtectedRoute` role check); all billing API endpoints return 403 for non-OWNER JWTs.
- **Upgrade is immediate** — POST /billing/checkout → Stripe Checkout → webhook `checkout.session.completed` → status=ACTIVE. No deferred logic.
- **Downgrade is deferred** — `pendingPlanId` set in DB; applied only when `customer.subscription.updated` fires with a new billing period start. Owner sees "pending" indicator on plan card.
- **Cancel is at period end** — access continues until `currentPeriodEnd`; account auto-downgrades to Free via `customer.subscription.deleted` webhook.
- **Scheduler side-effect** — `TrialExpiryScheduler` runs at 02:00 daily; when trial expires, subscription downgrades to Free silently. Owner sees the status change on next `/billing` load.
- **Stripe Portal** (Manage Payment Method) and **Invoice PDF** links are external redirects — no backend proxy, Stripe URLs opened directly.
- **`GET /billing/usage`** and **`GET /billing/invoices`** are new backend endpoints required before UC2 and UC3 can show real data (currently mock data in prototype).
