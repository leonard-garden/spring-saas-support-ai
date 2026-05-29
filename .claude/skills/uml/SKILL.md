---
name: uml
version: 1.0.0
description: |
  Vẽ Mermaid diagrams cho feature: sequence, class, ER, component, flowchart.
  Output: docs/design/{slug}-{type}.md
  Triggers: "draw diagram", "sequence diagram", "class diagram", "vẽ sơ đồ",
  "vẽ biểu đồ", "ER diagram", "component diagram", "flow diagram", "vẽ sequence"
---

## Purpose

Generate Mermaid diagrams for a feature and save to `docs/design/`.

## Diagram Types

| Type | Use when | Output file |
|------|----------|------------|
| `sequence` | Request/response flows, async pipeline, SSE streaming | `diagrams/sequence.md` |
| `class` | Entity hierarchy, interface + implementation, dependencies | `diagrams/class.md` |
| `er` | Database tables, columns, FK relationships | `diagrams/er.md` |
| `component` | System components, external integrations (Stripe, MinIO, Claude API) | `diagrams/component.md` |
| `flowchart` | Business logic, decision trees, conditional flows | `diagrams/flowchart.md` |

## Invoke

`/uml {feature-name} {type}` — or auto-triggered. If type is ambiguous from context, ask:
"Which diagram type? sequence / class / er / component / flowchart"

## Process

1. **Get feature name and diagram type** — from args or context. Ask only if both are missing.
2. **Derive slug** — kebab-case feature name.
3. **Read context:**
   - `CLAUDE.md`
   - `.claude/memory/architecture.md`
   - `.claude/memory/multi-tenancy.md`
4. **Read source code** based on diagram type:
   - `sequence` → read Controller + Service + any @Async beans for the feature
   - `class` → read Entity, interface, Impl classes for the feature
   - `er` → read Entity classes + Flyway migrations in `src/main/resources/db/migration/`
   - `component` → read `config/` package + external integration classes
   - `flowchart` → read Service implementation for the business logic flow
5. **Read SRS if exists** — `docs/design/{slug}-srs.md` for requirements context
6. **Check existing diagram** — if target file exists, read it and enter update mode
7. **Create directory** — `mkdir -p docs/design`
8. **Generate Mermaid diagram** — follow conventions below
9. **Save** — `docs/design/{slug}-{type}.md`
10. **Commit:**
    ```bash
    git add docs/design/{slug}-{type}.md
    git commit -m "docs(uml): add {type} diagram for {slug}"
    ```

## Output Format

Each diagram file follows this structure:

```
# {Type} Diagram — {Feature Name}

## Overview
{1–2 sentences: what this diagram shows and why it matters}

## Diagram

​```mermaid
{mermaid code}
​```

## Key Notes
- {Important annotation}
- {Async boundary note if applicable}
```

## Project-Specific Conventions

**Sequence diagrams — always start with auth + tenant setup:**
```
participant Client
participant JwtAuthFilter
participant TenantContext
participant Controller
participant Service
participant Repository
participant DB as PostgreSQL

Client->>JwtAuthFilter: HTTP Request + Authorization header
JwtAuthFilter->>TenantContext: setTenantId(claims.tenantId())
JwtAuthFilter->>Controller: filterChain.doFilter()
```

**ER diagrams — always show TenantEntity base:**
```
TenantEntity {
    uuid business_id FK
}
Business {
    uuid id PK
    string name
}
YourEntity {
    uuid id PK
    uuid business_id FK
    timestamptz created_at
    timestamptz updated_at
}
TenantEntity ||--o{ YourEntity : "business_id"
Business ||--o{ YourEntity : "owns"
```

**Async boundaries — mark clearly:**
```
Note over Service,Repository: @Async("processingExecutor") boundary
Note over Service,Repository: New thread — TenantContext propagated by TenantContextCopyingDecorator
```
