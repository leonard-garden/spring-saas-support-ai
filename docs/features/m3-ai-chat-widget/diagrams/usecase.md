# Use Case Diagram — M3: AI Chat + Embeddable Widget

## Overview
All M3 actors and their interactions with the Chat Widget System. Covers the Business Admin managing chatbot configuration, the Website Visitor using the embedded widget, and the Claude API as an external AI actor.

## Diagram

```mermaid
graph LR
    %% Actors
    Admin(["👤 Business Admin\n(ADMIN role, JWT)"])
    Visitor(["👤 Website Visitor\n(anonymous, no JWT)"])
    ClaudeAPI(["⚙️ Claude API\n(external system)"])

    %% Admin use cases
    subgraph AdminUC["Admin — Dashboard"]
        UC01["UC-01\nCreate chat widget"]
        UC02["UC-02\nConfigure widget\n(name, color, welcome msg)"]
        UC03["UC-03\nSelect knowledge bases\nfor widget"]
        UC04["UC-04\nCopy embed script tag"]
        UC05["UC-05\nPreview chatbot\nin dashboard"]
        UC06["UC-06\nView conversation\nhistory (sessions list)"]
        UC07["UC-07\nView individual\nconversation thread"]
    end

    %% Visitor use cases
    subgraph VisitorUC["Website Visitor — Embedded Widget"]
        UC08["UC-08\nLoad chat widget\non external website"]
        UC09["UC-09\nSend message\nto chatbot"]
        UC10["UC-10\nReceive AI-generated\nresponse with source citation"]
        UC11["UC-11\nContinue conversation\n(session memory)"]
    end

    %% Admin → use cases
    Admin --> UC01
    Admin --> UC02
    Admin --> UC04
    Admin --> UC05
    Admin --> UC06
    Admin --> UC07

    %% includes relationship
    UC02 -. "«includes»" .-> UC03

    %% Visitor → use cases
    Visitor --> UC08
    Visitor --> UC09
    Visitor --> UC11

    %% includes relationship
    UC09 -. "«includes»" .-> UC10

    %% System actor
    UC10 --> ClaudeAPI
```

## Key Notes
- `UC-02 «includes» UC-03` — KB selection is mandatory during widget configuration; cannot configure without choosing at least one KB.
- `UC-09 «includes» UC-10` — every message send triggers the full RAG pipeline (hybrid search → RRF → Claude API call); they cannot be decoupled.
- `UC-08` (widget load) is a prerequisite for UC-09/UC-10/UC-11 but is not modelled as `«includes»` — it is a separate actor-initiated action (page load event, not a business use case choice).
- Website Visitor has no JWT — tenant context is resolved from `chatbotId` embedded in the widget script tag (`data-chatbot-id`).
- Claude API appears as a system actor, not a human actor — it is invoked by UC-10 and never interacts directly with actors.
