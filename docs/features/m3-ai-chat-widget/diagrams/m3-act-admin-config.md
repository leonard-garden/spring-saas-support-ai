# Activity Diagram — Admin Widget Configuration Flow

## Overview
Models the full admin workflow for configuring the embeddable widget: initial page load branching on whether a chatbot already exists, inline form validation, and the conditional save path (POST vs PUT, with or without KB reassignment).

## Diagram

```mermaid
flowchart TD
    START([Admin navigates to Widget Config page])

    %% ── Phase 1: Load ───────────────────────────────────────────────
    START --> FETCH["GET /api/v1/chatbots\nGET /api/v1/knowledge-bases"]
    FETCH --> EXISTS{"Chatbot exists\nfor tenant?"}

    EXISTS -- "No — empty list" --> CREATE_CTA["Render 'Create Widget' CTA\n+ blank config form"]
    EXISTS -- "Yes" --> PREFILL["Render Configure Form\npre-filled: name · welcome msg\nprimary color · selected KB"]

    %% ── Phase 2: Form interaction ────────────────────────────────────
    CREATE_CTA --> INTERACT["Admin fills / edits fields"]
    PREFILL    --> INTERACT

    INTERACT --> VALIDATE{"Form valid?\n• name non-empty\n• primaryColor valid hex\n• KB selected"}

    VALIDATE -- "Invalid" --> ERRORS["Show inline validation errors\nDisable Save button"]
    ERRORS --> INTERACT

    VALIDATE -- "Valid" --> ENABLE["Enable Save button"]
    ENABLE --> CLICK_SAVE["Admin clicks Save"]

    %% ── Phase 3: Save ────────────────────────────────────────────────
    CLICK_SAVE --> IS_NEW{"First-time\ncreate?"}

    IS_NEW -- "Yes — no chatbot yet" --> POST["POST /api/v1/chatbots\n{ name, welcomeMessage,\n  primaryColor, kbId }"]

    IS_NEW -- "No — updating existing" --> KB_CHANGED{"KB selection\nchanged?"}

    KB_CHANGED -- "Yes" --> PUT_KB["PUT /api/v1/chatbots/{id}\n{ name, welcomeMessage,\n  primaryColor, kbId }"]
    KB_CHANGED -- "No — skip KB field" --> PUT_NO_KB["PUT /api/v1/chatbots/{id}\n{ name, welcomeMessage,\n  primaryColor }"]

    POST       --> RESP{"HTTP response"}
    PUT_KB     --> RESP
    PUT_NO_KB  --> RESP

    RESP -- "201 / 200 OK" --> SUCCESS["Show success toast\nDisplay embed snippet"]
    RESP -- "400 Validation error" --> BANNER["Show error banner\nHighlight invalid fields"]
    RESP -- "404 KB not found" --> BANNER
    RESP -- "403 Forbidden" --> BANNER

    BANNER --> INTERACT

    SUCCESS --> DONE(["Config saved\nEmbed snippet ready to paste"])
```

## Key Notes
- **Load phase** fires two requests in parallel (`/chatbots` + `/knowledge-bases`) so the KB dropdown is populated before the form renders.
- **Branching on exists:** an empty chatbot list shows the Create CTA; any existing chatbot goes straight to the pre-filled Configure form. Only one chatbot per tenant is assumed for M3.
- **Client-side validation** runs on every field change (controlled form); the Save button stays disabled until all rules pass — preventing a round-trip on obviously bad input.
- **KB-changed guard** avoids an unnecessary `kbId` field in the PUT body when the admin only updates cosmetic fields (name/color/welcome message).
- **Error banner → re-edit loop**: backend validation errors (400/404) surface inline and return the user to the form rather than clearing their input.
- **Swimlane mapping** (implicit): `FETCH` / `POST` / `PUT` calls originate in **React Dashboard**; routing and response handling happen in **ChatbotController + ChatbotService**; `EXISTS` / `KB_CHANGED` decisions reflect state held in **React Dashboard** component state after the initial GET.
