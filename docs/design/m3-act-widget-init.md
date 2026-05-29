# Activity Diagram — Widget Initialization & Config Load

## Overview
Shows the widget.js bootstrap sequence from page load to the first greeting message, covering visitor/session identity resolution, config fetch, and all fallback paths.

## Diagram

```mermaid
flowchart TD
    subgraph BROWSER["Browser — widget.js"]
        A([Page Load])
        B["Parse script tag\nextract data-chatbot-id"]
        C{"visitor_id in\nlocalStorage?"}
        D1["Use existing\nvisitor_id"]
        D2["Generate UUID\nvisitor_id"]
        E{"session_id in\nsessionStorage?"}
        F1["Resume existing\nsession"]
        F2["Generate new\nsession_id"]
        RESP{"Config fetch\nsuccessful?"}
        CFG["Apply config\nbrandColor · botName · greeting"]
        DEF["Apply hardcoded\ndefaults"]
        ACT{"widget\n.isActive?"}
        NOP([Silent no-op — exit])
        CSS["Set --brand-color\nCSS variable"]
        DOM["Mount Shadow DOM"]
        BUB["Render chat bubble"]
        TMR["Start 3s\ngreeting timer"]
        RDY([Show welcome message])
    end

    subgraph STORAGE["LocalStorage / SessionStorage"]
        LS[(localStorage\nvisitor_id)]
        SS[(sessionStorage\nsession_id)]
    end

    subgraph API["Backend API"]
        REQ["GET /api/v1/widget\n/{chatbotId}/config"]
    end

    A --> B --> C
    C -->|Yes| D1
    C -->|No| D2
    D2 -->|write| LS
    LS -->|read| D1
    D1 --> E
    D2 --> E
    E -->|Yes| F1
    E -->|No| F2
    F2 -->|write| SS
    SS -->|read| F1
    F1 --> REQ
    F2 --> REQ
    REQ --> RESP
    RESP -->|Yes| CFG
    RESP -->|No| DEF
    CFG --> ACT
    DEF --> ACT
    ACT -->|true| CSS
    ACT -->|false| NOP
    CSS --> DOM --> BUB --> TMR --> RDY
```

## Key Notes
- `visitor_id` persists across browser sessions (localStorage); `session_id` resets on tab close (sessionStorage).
- Config fetch failure (4xx, 5xx, network timeout) falls back to hardcoded defaults so the widget still renders — `isActive` defaults to `true` in this path.
- The `widget.isActive = false` path produces a silent no-op: no DOM mutation, no console output — invisible to the host page.
- Shadow DOM mount isolates widget CSS from the host page's stylesheet; `--brand-color` is the only variable injected into the host document.
- The 3s greeting timer fires only after the bubble is mounted; it is cancelled if the visitor opens the chat before it fires.
- Public endpoint `/api/v1/widget/{chatbotId}/config` is unauthenticated and rate-limited — `chatbotId` must be a valid UUID, not guessable by enumeration.
