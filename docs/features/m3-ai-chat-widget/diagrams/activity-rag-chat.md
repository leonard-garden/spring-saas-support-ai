# Activity Diagram — RAG Chat Flow

## Overview
Decision points, parallel activities, and error paths for the RAG chat flow. Covers both the public widget path (no JWT) and the authenticated dashboard path. Four swimlanes: Widget JS, Backend (ChatService), Database, and Claude API.

## Diagram

```mermaid
flowchart TD
    classDef client   fill:#dbeafe,stroke:#3b82f6,color:#1e3a8a
    classDef backend  fill:#dcfce7,stroke:#16a34a,color:#14532d
    classDef db       fill:#fef9c3,stroke:#ca8a04,color:#713f12
    classDef claude   fill:#fce7f3,stroke:#db2777,color:#831843
    classDef err      fill:#fee2e2,stroke:#dc2626,color:#7f1d1d
    classDef decision fill:#f3f4f6,stroke:#6b7280,color:#111827

    START([User types message]):::client
    REQ["POST /widget/{chatbotId}/chat<br/>X-Session-Id header"]:::client

    CHK_BOT{chatbotId found<br/>and isActive?}:::decision
    ERR_404["Return 403<br/>Widget not found / inactive"]:::err

    CHK_SESSION{sessionId exists<br/>in conversations?}:::decision
    NEW_CONV["CREATE new Conversation<br/>with sessionId"]:::db
    RESUME["RESUME existing Conversation<br/>load last N messages"]:::db

    SAVE_USER["Persist USER message<br/>to chat_messages"]:::db
    EMBED["Embed query<br/>via OpenAI text-embedding-3-small"]:::backend

    SEARCH["Hybrid search:<br/>vector cosine + tsvector fulltext<br/>RRF fusion → top-5 chunks"]:::db

    CHK_CHUNKS{chunks found<br/>> 0?}:::decision
    PROMPT_CTX["Build LLM prompt:<br/>system + context chunks<br/>+ conversation history"]:::backend
    PROMPT_FALLBACK["Build LLM prompt:<br/>system only + conversation history<br/>(no KB context)"]:::backend

    CLAUDE_CALL["Stream ChatClient.call()<br/>Anthropic claude-3-5-sonnet"]:::claude

    CHK_CLAUDE{Claude API<br/>succeeded?}:::decision
    STREAM_OK["Stream SSE tokens<br/>to client"]:::client
    PERSIST_ASST["Persist ASSISTANT message<br/>Increment message_usage counter"]:::db
    DONE([End: conversation updated]):::client

    ERR_CLAUDE["Send SSE error event<br/>DO NOT persist assistant message"]:::err
    ERR_DONE([End: error returned to client]):::err

    START --> REQ
    REQ --> CHK_BOT
    CHK_BOT -- No --> ERR_404
    CHK_BOT -- Yes --> CHK_SESSION

    CHK_SESSION -- No --> NEW_CONV
    CHK_SESSION -- Yes --> RESUME
    NEW_CONV --> SAVE_USER
    RESUME --> SAVE_USER

    SAVE_USER --> EMBED
    EMBED --> SEARCH
    SEARCH --> CHK_CHUNKS

    CHK_CHUNKS -- Yes --> PROMPT_CTX
    CHK_CHUNKS -- No,\nproceed without context --> PROMPT_FALLBACK

    PROMPT_CTX --> CLAUDE_CALL
    PROMPT_FALLBACK --> CLAUDE_CALL

    CLAUDE_CALL --> CHK_CLAUDE
    CHK_CLAUDE -- Success --> STREAM_OK
    STREAM_OK --> PERSIST_ASST
    PERSIST_ASST --> DONE

    CHK_CLAUDE -- Failure --> ERR_CLAUDE
    ERR_CLAUDE --> ERR_DONE
```

## Swimlane Legend
| Color | Swimlane |
|-------|---------|
| Blue | Widget JS (Client) |
| Green | Backend — ChatService |
| Yellow | Database — PostgreSQL + PgVector |
| Pink | Claude API (External) |
| Red | Error paths |

## Key Notes
- `SAVE_USER` happens **before** the Claude call — message is persisted even if LLM fails, preserving the conversation audit trail.
- When PgVector returns 0 chunks, the flow does **not** abort — it falls back to a prompt without RAG context. LLM responds with a graceful "I don't have information on that."
- `TenantContext` is set from `chatbotId` DB lookup (not JWT) for the widget path and must be cleared in a `finally` block after the stream closes.
- The ASSISTANT message is only persisted on **successful** Claude stream completion — partial/failed streams are never written to DB.
- `message_usage` counter is incremented atomically via DB upsert after the assistant message is saved.
