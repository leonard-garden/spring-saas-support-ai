# Activity Diagram — Chat History View Flow

## Overview
Models the admin workflow for browsing and reading past chat conversations: paginated sessions list with empty-state handling, "Load more" pagination guard, and drill-down into a message thread with tenant-ownership validation.

## Diagram

```mermaid
flowchart TD
    START([Admin navigates to Conversations tab])

    %% ── Phase 1: Load sessions list ─────────────────────────────────
    START --> FETCH["GET /api/v1/chat/conversations\n?page=0&size=20"]
    FETCH --> HAS_DATA{"Conversations\nexist?"}

    HAS_DATA -- "No — empty list" --> EMPTY["Show empty state\n'No conversations yet'"]
    EMPTY --> WAIT_END([End — no further action])

    HAS_DATA -- "Yes" --> TABLE["Render paginated table\ncolumns: chatbotName · lastMessageAt · messageCount"]

    %% ── Phase 2: Pagination ──────────────────────────────────────────
    TABLE --> MORE{"More pages\navailable?\n(page+1)*size < total"}

    MORE -- "No" --> HIDE_BTN["Hide 'Load more' button"]
    MORE -- "Yes" --> SHOW_BTN["Show 'Load more' button"]

    HIDE_BTN --> SELECT
    SHOW_BTN --> CLICK_MORE["Admin clicks 'Load more'"]
    CLICK_MORE --> FETCH_NEXT["GET /api/v1/chat/conversations\n?page=N&size=20"]
    FETCH_NEXT --> APPEND["Append new rows to table"]
    APPEND --> MORE

    %% ── Phase 3: Conversation detail ────────────────────────────────
    SELECT["Admin clicks a conversation row"]
    SELECT --> FETCH_MSGS["GET /api/v1/chat/conversations/{id}/messages"]
    FETCH_MSGS --> OWNS{"Session belongs\nto tenant?"}

    OWNS -- "No — cross-tenant" --> FORBIDDEN["Show error banner\n403 Forbidden"]
    FORBIDDEN --> TABLE

    OWNS -- "Yes" --> FETCH_OK{"Messages\nreturned?"}

    FETCH_OK -- "404 not found" --> NOT_FOUND["Show error banner\n'Conversation not found'"]
    NOT_FOUND --> TABLE

    FETCH_OK -- "200 OK" --> RENDER["Render read-only message thread\nUSER → right-aligned\nASSISTANT → left-aligned"]
    RENDER --> DONE([Admin reads conversation])
```

## Key Notes
- **Empty state branch** exits early — no table, no pagination, no row selection possible until a conversation exists (created via widget interaction).
- **Pagination guard** re-evaluates after every "Load more" response using `(page + 1) * size < total`; the button is hidden once the last page is reached.
- **Tenant-ownership guard** is enforced server-side via the Hibernate tenant filter on `ConversationRepository.findById()` — the 403/404 branch in the diagram reflects what React renders when the API returns an error, not a client-side check.
- **No write path** — this entire flow is read-only; no mutations, no `@Async` boundary.
- **Swimlane mapping** (implicit): `FETCH*` calls originate in **React Dashboard**; routing and DB queries happen in **WidgetAdminController / ConversationService / ConversationRepository**; `HAS_DATA` / `MORE` / `OWNS` decisions reflect **React Dashboard** component state after each API response.
