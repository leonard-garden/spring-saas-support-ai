# API Spec — M3: AI Chat + Embeddable Widget

**Version:** 1.0
**Date:** 2026-05-29
**Base URL:** /api/v1/

---

## Overview

| Property | Value |
|----------|-------|
| Auth | Bearer JWT in `Authorization` header (except public widget endpoints) |
| Response envelope | `ApiResponse<T>` — `{success: bool, data: T, error: string}` |
| Error format | RFC 7807 ProblemDetail |
| Content-Type | `application/json` (REST) · `text/event-stream` (SSE streaming endpoints) |
| Multi-tenancy | All tenant-scoped endpoints implicitly filter by the JWT's `tenant_id`. Clients never pass `tenantId` in the request body. |

### SecurityConfig changes required for M3

The following paths must be added to the `permitAll()` block in `SecurityConfig`:
```
/api/v1/widget/**      — public widget chat (no JWT)
/widget.js             — widget JS static asset
```

`allowedHeaders` in CORS config must also include `X-Session-Id` (used by the widget for session continuity).

---

## Section 1 — Chatbot Configuration (ADMIN only)

### POST /api/v1/chatbots

**Description:** Create a new Chatbot config linked to a KnowledgeBase. Activates the public widget endpoint immediately.
**Auth required:** YES
**Roles:** ADMIN

**Request body:**
```json
{
  "name": "string — display name shown in widget header (max 100 chars)",
  "welcomeMessage": "string — first message shown to end users (max 500 chars)",
  "primaryColor": "string — hex color code e.g. #3B82F6 (optional, default #3B82F6)",
  "kbId": "uuid — ID of the KnowledgeBase to use for RAG retrieval"
}
```

**Response 201 Created:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "name": "Support Bot",
    "welcomeMessage": "Hi! How can I help you today?",
    "primaryColor": "#3B82F6",
    "kbId": "uuid",
    "isActive": true,
    "embedSnippet": "<script src=\"https://app.supportsaas.io/widget.js\" data-chatbot-id=\"uuid\"></script>",
    "createdAt": "2026-05-29T10:00:00Z"
  },
  "error": null
}
```

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 400 | Validation failed — blank name, invalid hex color, missing kbId |
| 401 | Missing or invalid JWT |
| 403 | Insufficient role — MEMBER cannot create chatbots |
| 404 | `kbId` not found or belongs to a different tenant |

---

### GET /api/v1/chatbots

**Description:** List all Chatbot configs for the authenticated tenant.
**Auth required:** YES
**Roles:** ADMIN, MEMBER

**Query params:** none (small dataset, no pagination needed)

**Response 200 OK:**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "name": "Support Bot",
      "welcomeMessage": "Hi! How can I help you today?",
      "primaryColor": "#3B82F6",
      "kbId": "uuid",
      "isActive": true,
      "createdAt": "2026-05-29T10:00:00Z"
    }
  ],
  "error": null
}
```

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 401 | Missing or invalid JWT |

---

### GET /api/v1/chatbots/{id}

**Description:** Get a single Chatbot config by ID.
**Auth required:** YES
**Roles:** ADMIN, MEMBER

**Path params:** `id` — UUID of the chatbot

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "name": "Support Bot",
    "welcomeMessage": "Hi! How can I help you today?",
    "primaryColor": "#3B82F6",
    "kbId": "uuid",
    "isActive": true,
    "embedSnippet": "<script src=\"https://app.supportsaas.io/widget.js\" data-chatbot-id=\"uuid\"></script>",
    "createdAt": "2026-05-29T10:00:00Z"
  },
  "error": null
}
```

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 401 | Missing or invalid JWT |
| 404 | Chatbot not found or belongs to a different tenant |

---

### PUT /api/v1/chatbots/{id}

**Description:** Update Chatbot config. All fields are optional — only provided fields are updated.
**Auth required:** YES
**Roles:** ADMIN

**Path params:** `id` — UUID of the chatbot

**Request body:**
```json
{
  "name": "string — optional",
  "welcomeMessage": "string — optional",
  "primaryColor": "string — optional hex color",
  "kbId": "uuid — optional, must belong to the same tenant",
  "isActive": "boolean — optional, set false to deactivate widget"
}
```

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "name": "Updated Bot Name",
    "welcomeMessage": "Updated welcome",
    "primaryColor": "#10B981",
    "kbId": "uuid",
    "isActive": false,
    "createdAt": "2026-05-29T10:00:00Z"
  },
  "error": null
}
```

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 400 | Validation failed — invalid hex color, blank name |
| 401 | Missing or invalid JWT |
| 403 | Insufficient role |
| 404 | Chatbot not found, or new `kbId` not found in this tenant |

---

### DELETE /api/v1/chatbots/{id}

**Description:** Delete a Chatbot config. The public widget endpoint for this `chatbotId` will return 404 immediately.
**Auth required:** YES
**Roles:** ADMIN

**Path params:** `id` — UUID of the chatbot

**Response 204 No Content** (no body)

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 401 | Missing or invalid JWT |
| 403 | Insufficient role |
| 404 | Chatbot not found or belongs to a different tenant |

---

### GET /api/v1/chatbots/{id}/embed

**Description:** Get the HTML embed snippet for copy-paste into any website.
**Auth required:** YES
**Roles:** ADMIN, MEMBER

**Path params:** `id` — UUID of the chatbot

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "snippet": "<script src=\"https://app.supportsaas.io/widget.js\" data-chatbot-id=\"{uuid}\"></script>",
    "widgetUrl": "https://app.supportsaas.io/widget.js",
    "chatbotId": "uuid"
  },
  "error": null
}
```

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 401 | Missing or invalid JWT |
| 404 | Chatbot not found |

---

## Section 2 — Authenticated Dashboard Chat (JWT required)

### POST /api/v1/chat/conversations

**Description:** Create a new Conversation linked to a Chatbot. Returns the `conversationId` to use for subsequent message sends.
**Auth required:** YES
**Roles:** ADMIN, MEMBER

**Request body:**
```json
{
  "chatbotId": "uuid — which chatbot config to use for this conversation"
}
```

**Response 201 Created:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "chatbotId": "uuid",
    "createdAt": "2026-05-29T10:00:00Z"
  },
  "error": null
}
```

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 400 | Missing chatbotId |
| 401 | Missing or invalid JWT |
| 404 | Chatbot not found or belongs to a different tenant |

---

### GET /api/v1/chat/conversations

**Description:** List all conversations for the authenticated tenant, ordered by most recent first.
**Auth required:** YES
**Roles:** ADMIN, MEMBER

**Query params:**
| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | 0 | Zero-based page index |
| `size` | int | 20 | Items per page (max 100) |

**Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "uuid",
        "chatbotId": "uuid",
        "chatbotName": "Support Bot",
        "messageCount": 6,
        "lastMessageAt": "2026-05-29T11:30:00Z",
        "createdAt": "2026-05-29T10:00:00Z"
      }
    ],
    "total": 42,
    "page": 0,
    "limit": 20
  },
  "error": null
}
```

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 401 | Missing or invalid JWT |

---

### GET /api/v1/chat/conversations/{id}/messages

**Description:** Get the full message history for a conversation, ordered chronologically.
**Auth required:** YES
**Roles:** ADMIN, MEMBER

**Path params:** `id` — UUID of the conversation

**Response 200 OK:**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "role": "USER",
      "content": "How do I reset my password?",
      "createdAt": "2026-05-29T10:01:00Z"
    },
    {
      "id": "uuid",
      "role": "ASSISTANT",
      "content": "To reset your password, click the 'Forgot Password' link on the login page...",
      "createdAt": "2026-05-29T10:01:03Z"
    }
  ],
  "error": null
}
```

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 401 | Missing or invalid JWT |
| 404 | Conversation not found or belongs to a different tenant |

---

### POST /api/v1/chat/conversations/{id}/messages

**Description:** Send a user message. Triggers RAG retrieval + LLM generation and streams the assistant response via SSE. The USER message and final ASSISTANT message are both persisted on completion.
**Auth required:** YES
**Roles:** ADMIN, MEMBER
**Produces:** `text/event-stream`

**Path params:** `id` — UUID of the conversation

**Request body:**
```json
{
  "query": "string — user message (max 2000 chars, must not be blank)"
}
```

**SSE event stream:**
```
data: {"token": "To "}

data: {"token": "reset "}

data: {"token": "your password..."}

data: {"done": true, "messageId": "uuid"}

event: error
data: {"error": "QUOTA_EXCEEDED", "message": "Monthly message limit reached"}
```

| Event field | Description |
|------------|-------------|
| `token` | Partial LLM response text — append to UI |
| `done: true` + `messageId` | Stream complete; `messageId` is the persisted ASSISTANT message UUID |
| `event: error` | Stream aborted; content describes failure reason |

**Error responses (non-streaming, before stream opens):**
| HTTP | Condition |
|------|-----------|
| 400 | Blank query or query exceeds 2000 chars |
| 401 | Missing or invalid JWT |
| 404 | Conversation not found or belongs to a different tenant |
| 429 | Monthly message quota exceeded (`QuotaExceededException`) |

> **Note:** If the LLM call fails after the stream has opened, an `event: error` SSE event is sent and the connection closes. The incomplete ASSISTANT message is NOT persisted.

---

## Section 3 — Public Widget (no JWT)

### POST /api/v1/widget/{chatbotId}/chat

**Description:** Public chat endpoint for the embeddable widget. Resolves tenant from `chatbotId`, enforces rate limiting, and streams the LLM response. Session continuity is maintained via `X-Session-Id`.
**Auth required:** NO
**Roles:** PUBLIC
**Produces:** `text/event-stream`

**Path params:** `chatbotId` — UUID of the chatbot (from the embed snippet)

**Request headers:**
| Header | Required | Description |
|--------|----------|-------------|
| `X-Session-Id` | Recommended | UUID generated by widget JS on first load; stored in `localStorage`. Links messages to the same conversation. If absent or new, a new conversation is created. |

**Request body:**
```json
{
  "query": "string — end user message (max 2000 chars, must not be blank)"
}
```

**SSE event stream:** (identical format to authenticated chat endpoint)
```
data: {"token": "Hello! "}

data: {"token": "I can help with that."}

data: {"done": true, "messageId": "uuid"}

event: error
data: {"error": "QUOTA_EXCEEDED", "message": "Service temporarily unavailable"}
```

**Error responses (non-streaming):**
| HTTP | Condition |
|------|-----------|
| 400 | Blank query or query exceeds 2000 chars |
| 404 | `chatbotId` not found |
| 404 | Chatbot is inactive (`isActive = false`) |
| 429 | Rate limit exceeded — > 20 req/min from the same IP per chatbot |
| 429 | Tenant's monthly message quota exceeded |

> **Security note:** The `TenantContext` for this endpoint is set exclusively from the DB lookup of `chatbotId`. The request body MUST NOT contain or accept a `tenantId` field. `TenantContext.clear()` is called in a `finally` block after the stream closes.

---

## Section 4 — Widget Static Asset

### GET /widget.js

**Description:** Serves the embeddable widget JavaScript file. Vanilla JS, no framework, < 50KB. Reads `data-chatbot-id` from the `<script>` tag and renders a floating chat bubble.
**Auth required:** NO
**Roles:** PUBLIC
**Produces:** `application/javascript`

**Response 200 OK:** JavaScript file content

---

## Common Error Format

All non-streaming errors follow RFC 7807 ProblemDetail:
```json
{
  "type": "https://problems.supportsaas.io/quota-exceeded",
  "title": "Quota Exceeded",
  "status": 429,
  "detail": "Monthly message limit of 100 reached. Upgrade your plan to continue.",
  "instance": "/api/v1/chat/conversations/abc-123/messages"
}
```

SSE error events use a simplified format (not ProblemDetail — stream is already open):
```
event: error
data: {"error": "QUOTA_EXCEEDED", "message": "Monthly message limit reached"}
```

---

## Rate Limiting

| Endpoint | Limit | Scope |
|----------|-------|-------|
| `POST /api/v1/widget/{chatbotId}/chat` | 20 req/min | Per source IP + chatbotId |
| All authenticated endpoints | No hard limit in M3 (tenant quota enforced at message level) | — |

Rate limit headers returned on 429:
```
X-RateLimit-Limit: 20
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1748512345
```

---

## Swagger Annotations Reference

### ChatbotController
```java
@Operation(summary = "Create chatbot config")
@ApiResponse(responseCode = "201", description = "Chatbot created")
@ApiResponse(responseCode = "400", description = "Validation failed")
@ApiResponse(responseCode = "401", description = "Unauthorized")
@ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required")
@ApiResponse(responseCode = "404", description = "KnowledgeBase not found")

@Operation(summary = "List chatbots for tenant")
@ApiResponse(responseCode = "200", description = "List returned")
@ApiResponse(responseCode = "401", description = "Unauthorized")

@Operation(summary = "Update chatbot config")
@ApiResponse(responseCode = "200", description = "Chatbot updated")
@ApiResponse(responseCode = "400", description = "Validation failed")
@ApiResponse(responseCode = "401", description = "Unauthorized")
@ApiResponse(responseCode = "403", description = "Forbidden")
@ApiResponse(responseCode = "404", description = "Chatbot or KnowledgeBase not found")

@Operation(summary = "Delete chatbot config")
@ApiResponse(responseCode = "204", description = "Chatbot deleted")
@ApiResponse(responseCode = "401", description = "Unauthorized")
@ApiResponse(responseCode = "403", description = "Forbidden")
@ApiResponse(responseCode = "404", description = "Chatbot not found")

@Operation(summary = "Get embed snippet for chatbot")
@ApiResponse(responseCode = "200", description = "Embed snippet returned")
@ApiResponse(responseCode = "401", description = "Unauthorized")
@ApiResponse(responseCode = "404", description = "Chatbot not found")
```

### ChatController
```java
@Operation(summary = "Create conversation")
@ApiResponse(responseCode = "201", description = "Conversation created")
@ApiResponse(responseCode = "400", description = "Validation failed")
@ApiResponse(responseCode = "401", description = "Unauthorized")
@ApiResponse(responseCode = "404", description = "Chatbot not found")

@Operation(summary = "List conversations for tenant")
@ApiResponse(responseCode = "200", description = "Paginated conversation list")
@ApiResponse(responseCode = "401", description = "Unauthorized")

@Operation(summary = "Get message history for conversation")
@ApiResponse(responseCode = "200", description = "Message list returned")
@ApiResponse(responseCode = "401", description = "Unauthorized")
@ApiResponse(responseCode = "404", description = "Conversation not found")

@Operation(summary = "Send message — streams SSE response")
@ApiResponse(responseCode = "200", description = "SSE stream — see event format in api-spec.md")
@ApiResponse(responseCode = "400", description = "Blank or too-long query")
@ApiResponse(responseCode = "401", description = "Unauthorized")
@ApiResponse(responseCode = "404", description = "Conversation not found")
@ApiResponse(responseCode = "429", description = "Monthly quota exceeded")
```

### WidgetController
```java
@Operation(summary = "Public widget chat — streams SSE response (no auth)")
@ApiResponse(responseCode = "200", description = "SSE stream — see event format in api-spec.md")
@ApiResponse(responseCode = "400", description = "Blank or too-long query")
@ApiResponse(responseCode = "404", description = "Chatbot not found or inactive")
@ApiResponse(responseCode = "429", description = "Rate limit or quota exceeded")
```
