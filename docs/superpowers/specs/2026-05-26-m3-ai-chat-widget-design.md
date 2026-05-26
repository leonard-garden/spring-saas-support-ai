# M3 Design Spec — AI Chat + Embeddable Widget

**Date:** 2026-05-26
**Milestone:** v0.3
**Author:** Leonard Trinh
**Status:** Approved

---

## 1. Overview

M3 delivers the core product value of spring-saas-support-ai: a business can configure an AI chatbot trained on their own documentation and embed it on their website in under 5 minutes — without writing code.

**Target audience (dual):**
- Recruiters / hiring managers — see RAG pipeline, Spring AI, PgVector integration
- SMB owners — see "paste one script tag, get a chatbot" experience

**Depends on:** M2 (Knowledge Base + PgVector embeddings must exist)

---

## 2. Scope

### In Scope

| Feature | Notes |
|---------|-------|
| RAG chat endpoint | Spring AI + Claude, non-streaming, conversation memory (last 5 msgs) |
| Embeddable widget (JS) | Vanilla JS, Shadow DOM, `data-widget-id` embed pattern |
| Widget configuration | Bot name, brand color, welcome message, KB selection (multi) |
| Admin preview | Inline widget preview inside dashboard |
| Embed code UI | Copy script tag, widget ID display |
| Chat history | ChatSession list + message thread view in admin |
| Deploy + smoke test | Render deploy, full demo flow verified |

### Out of Scope (M3)

| Feature | Deferred to |
|---------|-------------|
| Streaming responses | M4 — easy migration, saves ~1 day |
| Multiple widgets per business | M4 — remove UNIQUE constraint |
| Widget analytics (message count, resolution rate) | M4 |
| Human handoff / escalation | M4+ |
| Widget position config (left/right) | M4 |
| Conversation export | M4 |

---

## 3. Architecture

### System Components

```
External Website (Customer)
  └── <script src="{base_url}/widget.js" data-widget-id="abc123">
        │
        ├── GET /api/v1/chat/widget/{widgetId}/config  (load config on init)
        └── POST /api/v1/chat/message                  (send messages)

Spring Boot Backend (new in M3)
  ├── ChatController       — public chat endpoints
  ├── WidgetAdminController — admin CRUD
  ├── ChatService          — orchestrates RAG + LLM
  ├── RagService           — PgVector similarity search
  └── WidgetService        — widget + config management

Infrastructure (existing)
  ├── PgVector (M2)        — document chunk embeddings
  └── Claude API           — via Spring AI ChatClient
```

### RAG Chat Flow (POST /chat/message)

1. **Validate** — widget exists + `is_active = true`. 403 if not.
2. **Session** — create new `ChatSession` if `sessionId` null, else load existing.
3. **Persist user message** — `ChatMessage(role=USER)`.
4. **Embed query** — Spring AI embeds the user message via Claude embeddings.
5. **Vector search** — PgVector top-5 chunks filtered by `kb_id IN (widget's selected KBs)`.
6. **Build prompt** — system prompt + retrieved context + last 5 messages (conversation memory).
7. **Claude API call** — `ChatClient.prompt(...).call().content()` (non-streaming).
8. **Persist + return** — save `ChatMessage(role=ASSISTANT)`, return `{ sessionId, reply, sources[] }`.

### Widget JS Architecture

- Served as static resource from Spring Boot: `GET /widget.js`
- Self-contained: zero external dependencies
- Uses **Shadow DOM** to isolate CSS from host page
- Loads config on init from `/chat/widget/{widgetId}/config`
- Stores `visitor_id` (anonymous UUID) in `localStorage`
- Stores `session_id` in `sessionStorage` (new session per tab)

---

## 4. Data Model

### New Entities

#### `widgets`
| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | This IS the `widget_id` used in embed script |
| business_id | UUID FK → businesses | Tenant isolation enforced here |
| is_active | boolean | DEFAULT true |
| created_at | timestamp | |

#### `widget_configs`
| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| widget_id | UUID FK UNIQUE | 1:1 with widget |
| bot_name | varchar(100) | DEFAULT 'Support Bot' |
| brand_color | varchar(7) | DEFAULT '#F59E0B' (project primary) |
| welcome_message | text | DEFAULT 'Xin chào! Tôi có thể giúp gì?' |
| updated_at | timestamp | |

#### `widget_knowledge_bases` (junction)
| Column | Type | Notes |
|--------|------|-------|
| widget_id | UUID FK | Composite PK |
| knowledge_base_id | UUID FK → knowledge_bases | Composite PK |

RAG filter: `WHERE kb_id IN (SELECT knowledge_base_id FROM widget_knowledge_bases WHERE widget_id = ?)`.

#### `chat_sessions`
| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| widget_id | UUID FK → widgets | |
| visitor_id | varchar(36) | Anonymous UUID from browser localStorage |
| started_at | timestamp | |
| last_message_at | timestamp | Updated on each message |
| message_count | int | Denormalized for fast list queries |

#### `chat_messages`
| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| session_id | UUID FK → chat_sessions | |
| role | enum USER \| ASSISTANT | |
| content | text | |
| created_at | timestamp | |

### Relationships

```
businesses ──1:1── widgets ──1:1── widget_configs
                      │
                      ├──N:M── knowledge_bases  (via widget_knowledge_bases)
                      │
                      └──1:N── chat_sessions ──1:N── chat_messages
```

### Flyway Migrations

- `V13__create_widgets.sql`
- `V14__create_widget_configs.sql`
- `V15__create_widget_knowledge_bases.sql`
- `V16__create_chat_sessions.sql`
- `V17__create_chat_messages.sql`

---

## 5. API Design

### Public Endpoints (no JWT — `widgetId` is the auth token)

#### `GET /api/v1/chat/widget/{widgetId}/config`
Called by widget JS on init. Returns display config only (no sensitive data).
```json
{
  "botName": "Support Bot",
  "brandColor": "#F59E0B",
  "welcomeMessage": "Xin chào! Tôi có thể giúp gì?"
}
```

#### `POST /api/v1/chat/message`
Core chat endpoint. Creates session if `sessionId` is null.
```json
// Request
{ "widgetId": "uuid", "sessionId": "uuid | null", "message": "string" }

// Response
{ "sessionId": "uuid", "reply": "string", "sources": ["Doc title…"] }
```
Error cases: 403 widget inactive, 404 widget not found, 500 LLM failure.

#### `GET /widget.js`
Serves the embeddable widget JavaScript bundle as static content.

---

### Admin Endpoints (JWT required — tenant scoped via TenantContext)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/chat/widget` | Get current tenant's widget + config. 404 if not created. |
| POST | `/api/v1/chat/widget` | Create widget + default WidgetConfig. 409 if already exists. No KB assignment on create — use PUT /knowledge-bases separately. |
| PUT | `/api/v1/chat/widget/{widgetId}/config` | Update bot_name, brand_color, welcome_message |
| PUT | `/api/v1/chat/widget/{widgetId}/knowledge-bases` | Replace KB list (array of UUIDs) |
| GET | `/api/v1/chat/sessions?page=0&size=20` | List sessions, sort by last_message_at DESC |
| GET | `/api/v1/chat/sessions/{sessionId}/messages` | Full message thread, ordered by created_at ASC |

---

## 6. Widget UX

### Visual Design
- **Color scheme:** Brand color drives all primary elements (bubble, header, send button, user message bubbles)
- **Default color:** `#F59E0B` (matches admin dashboard primary)
- **Typography:** System font stack (no external font load)
- **Background:** Light warm (`#faf9f7`) matching project aesthetic

### States

**Collapsed (bubble):**
- Fixed position bottom-right, z-index high
- Amber chat bubble button (52×52px, shadow)
- Proactive greeting tooltip after 3s delay (dismissible)

**Expanded (drawer):**
- 320×390px panel, bottom-right anchored
- Header: brand color background, bot name, "● Online" indicator
- Message area: scrollable, bot messages left-aligned (light bg), user messages right-aligned (brand color)
- Each bot message shows `📄 Source: [doc name]` citation below
- Input: rounded text field + send button

### Technical
- Shadow DOM isolation — zero CSS conflict with host page
- `visitor_id`: UUID v4 generated on first load, stored in `localStorage`
- `session_id`: stored in `sessionStorage` (resets per tab/window)
- No cookies, no tracking beyond session scope

---

## 7. Admin Dashboard — New Pages

### Sidebar addition
New "💬 Chat Widget" item below Knowledge Base separator.

### `/chat-widget` — Configure tab (default)
- **Widget Settings form:** Bot name, brand color (hex input + color swatch), welcome message (textarea)
- **Knowledge Bases:** Multi-select checklist — all tenant KBs listed, checkboxes, "N selected" badge
- **Save Changes** button (amber primary)

### `/chat-widget` — Preview tab
- Inline iframe or rendered widget component showing live preview with current config
- "This is a preview — messages are not saved" disclaimer

### `/chat-widget` — Embed Code section (on Configure tab)
- Syntax-highlighted script tag block
- "📋 Copy Script" button (copies to clipboard)
- "👁 Open Preview" button (switches to Preview tab)

### `/chat-widget/conversations` — Conversations tab
- Paginated table: Visitor ID, started at, last message at, message count
- Click row → expand inline or navigate to detail view
- Detail view: full message thread, read-only

---

## 8. Requirements

### New Requirements (M3)

| ID | Description |
|----|-------------|
| CHAT-01 | Admin can preview AI chatbot within dashboard |
| CHAT-02 | Admin can copy embeddable widget script tag |
| CHAT-03 | Widget loads and renders on any external website via script tag |
| CHAT-04 | Chatbot answers using RAG from selected Knowledge Bases |
| CHAT-05 | Anonymous visitors can chat without an account |
| CHAT-06 | Admin can configure bot name, brand color, welcome message |
| CHAT-07 | Admin can select multiple KBs for the widget to search |
| CHAT-08 | Admin can view list of chat conversations (sessions) |
| CHAT-09 | Admin can view individual conversation message thread |
| CHAT-10 | Bot responses include source document citation |

---

## 9. Phase Plan (Vertical Slices)

| Phase | Name | Delivers | Duration est. |
|-------|------|----------|---------------|
| 1 | Schema + RAG API | DB migrations, chat endpoint working (testable via curl) | 1.5 days |
| 2 | Widget JS | Live embeddable widget on external site, connects to Phase 1 API | 1.5 days |
| 3 | Widget Config + Admin UI | Configure page, embed code UI, KB multi-select | 1.5 days |
| 4 | Chat History | Sessions list + conversation detail in admin | 1 day |
| 5 | Deploy + Smoke Test | Render deploy, CORS update, full demo flow | 0.5 days |
| **Total** | | | **~6 days** |

**Cut strategy (if time runs short):** Phase 4 (Chat History) is the safest to defer to M4. Core value (widget live + admin config) is delivered by Phase 3.

---

## 10. Success Criteria

The milestone is complete when:
1. A business can sign up, upload a document (M2), configure the widget, and copy the embed script — all in under 5 minutes
2. Pasting the script tag on any HTML page renders a functional chat bubble
3. The chatbot correctly answers questions from the uploaded document (RAG verified)
4. Source citations appear in bot responses
5. Admin can see a list of past conversations in the dashboard
6. Full demo flow deployed on Render and accessible at public URL

---

## 11. Key Decisions Log

| Decision | Rationale |
|----------|-----------|
| Non-streaming chat | Saves ~1 day, easy to migrate (1-line Spring AI change + widget fetch rewrite) |
| `data-widget-id` embed | Industry standard (Intercom/Crisp pattern), simple auth |
| Widget JS served from Spring Boot | No CDN needed in M3, reduces infra complexity |
| Shadow DOM isolation | Zero CSS conflict guarantee with host page |
| Multi-KB via junction table | More flexible than FK, RAG uses `IN (...)` filter |
| 1 widget per business (M3) | Sufficient for MVP, remove UNIQUE to unlock multi-widget in M4 |
| Anonymous visitor tracking | UUID in localStorage, no account/cookie required |
| Default brand_color = `#F59E0B` | Matches admin dashboard primary color |
| Conversation memory = last 5 msgs | Balance between context quality and token cost |
