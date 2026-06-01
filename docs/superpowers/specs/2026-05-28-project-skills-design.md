# Design Spec — Project Skills Suite

**Date:** 2026-05-28
**Status:** Approved
**Author:** Leonard Trinh (via brainstorming session)

---

## Problem Statement

The existing `sdlc` skill is broken and insufficient. Each feature needs proper software
engineering artifacts that both developers and AI can read: formal requirements, design
diagrams, API contracts, DB schema, test plans, release notes, and architecture decisions.

---

## Design Decisions

- **7 independent skills** — each skill is a focused, lazy-loaded instruction context
- **Auto-trigger capable** — each skill has distinct trigger phrases that don't overlap
- **Feature-based file layout** — `docs/features/{slug}/` groups all artifacts per feature
- **`forge` stays unchanged** — new skills are fully independent, no integration required
- **Formal SRS (IEEE 830)** — not lightweight spec; structured for portfolio quality

---

## File Layout

```
docs/
├── features/
│   └── {slug}/
│       ├── SRS.md           ← /srs output
│       ├── api-spec.md      ← /api-spec output
│       ├── db-schema.md     ← /db-schema output
│       ├── test-plan.md     ← /test-plan output
│       ├── diagrams/
│       │   ├── sequence.md  ← /uml output
│       │   ├── class.md
│       │   ├── er.md
│       │   ├── component.md
│       │   └── flowchart.md
│       └── release.md       ← (not used — see releases/)
├── releases/
│   └── v{X.Y.Z}-release.md  ← /release output (multi-feature aggregate)
└── adr/
    └── ADR-{NNN}-{slug}.md  ← /adr output (cross-cutting decisions)
```

---

## Skills

### 1. `/srs` — Formal SRS (IEEE 830)

**Trigger phrases:** "viết SRS", "write SRS", "spec feature X", "requirements for X", "viết requirements"

**Output:** `docs/features/{slug}/SRS.md`

**Context read:**
- `CLAUDE.md`, `architecture.md`, `tech-stack.md`, `project-context.md`
- Source code in relevant package (if feature partially exists)

**Process:**
1. Ask feature name if not in args
2. Read context files
3. Check if SRS exists → update mode (never overwrite)
4. Generate IEEE 830 structure
5. Save + commit: `docs(srs): add SRS for {feature}`

**IEEE 830 structure:**
```
1. Introduction
   1.1 Purpose
   1.2 Scope
   1.3 Definitions & Acronyms
   1.4 References
2. Overall Description
   2.1 Product Perspective
   2.2 User Classes & Characteristics
   2.3 Operating Environment
   2.4 Design Constraints  ← enforce: no Lombok, Testcontainers, no H2
   2.5 Assumptions & Dependencies
3. Functional Requirements  ← FR-001, FR-002, ... numbered + testable
4. Non-Functional Requirements  ← Performance, Security, Scalability
5. Use Cases  ← text descriptions, not diagrams
6. External Interface Requirements  ← API + DB interfaces
7. Out of Scope
```

---

### 2. `/uml` — Mermaid Diagrams

**Trigger phrases:** "draw diagram", "sequence diagram", "class diagram", "vẽ sơ đồ",
"vẽ biểu đồ", "ER diagram", "component diagram", "flow diagram"

**Output:** `docs/features/{slug}/diagrams/{type}.md`

**Diagram types:**

| Type | When | File |
|------|------|------|
| `sequence` | Request flow, async pipeline, SSE stream | `diagrams/sequence.md` |
| `class` | Entity hierarchy, interface/impl | `diagrams/class.md` |
| `er` | Database schema, relationships | `diagrams/er.md` |
| `component` | System components, external integrations | `diagrams/component.md` |
| `flowchart` | Business logic, decision flows | `diagrams/flowchart.md` |

**Context read:**
- `CLAUDE.md`, `architecture.md`, `multi-tenancy.md`
- Source code relevant to diagram type (Controller+Service for sequence; Entities for ER)
- `docs/features/{slug}/SRS.md` if exists

**Process:**
1. Detect diagram type — ask if ambiguous
2. Read context + source code
3. Check existing diagram → update mode
4. Generate Mermaid
5. Save + commit: `docs(uml): add {type} diagram for {feature}`

**Output format per file:**
```markdown
# {Type} Diagram — {Feature}

## Overview
{1-2 sentences describing what this diagram shows}

## Diagram
```mermaid
{diagram code}
```

## Key Notes
- {important annotations}
```

**Project-specific conventions:**
- Sequence diagrams always show `JwtAuthFilter → TenantContext → Controller` at start
- ER diagrams always show `TenantEntity` base with `business_id`
- Async boundary must be clearly marked (`@Async("processingExecutor")`)

---

### 3. `/api-spec` — REST API Contract

**Trigger phrases:** "design API", "REST contract", "API cho X", "thiết kế API",
"endpoint for X", "API contract", "viết API spec"

**Output:** `docs/features/{slug}/api-spec.md`

**Context read:**
- `CLAUDE.md`, `architecture.md`, `tech-stack.md`
- `docs/features/{slug}/SRS.md` → derive endpoints from Functional Requirements
- Existing controllers for the feature (reverse from code if present)
- `.claude/skills/forge/references/api-patterns.md`

**Process:**
1. Feature name from args
2. Read context
3. Map FR list → endpoint candidates
4. Scan existing controllers if any
5. Design endpoints + schemas
6. Save + commit: `docs(api): add API spec for {feature}`

**Output structure:**
```markdown
# API Spec — {Feature}

## Overview
- Base URL: /api/v1/
- Auth: Bearer JWT (Authorization header)
- Response envelope: ApiResponse<T> {success, data, error}
- Error format: RFC 7807 ProblemDetail

## Endpoints

### POST /api/v1/{resource}
Description, Auth required, Roles, Request body, Response, Errors table

## Rate Limiting
## Swagger Annotations Reference
```

**Project-specific rules:**
- All responses wrapped in `ApiResponse<T>` — no naked objects
- Errors use `ProblemDetail` (RFC 7807)
- Path convention: `/api/v1/{resource}/{id}/{sub-resource}`
- Document public endpoints (widget API) vs JWT-required separately
- Include `@Operation`, `@ApiResponse` suggestions for `api-doc-sync` agent

---

### 4. `/db-schema` — Database Schema + Flyway Migration

**Trigger phrases:** "design table", "db schema", "migration for X", "ERD",
"thiết kế bảng", "flyway migration", "database schema", "schema for X"

**Output:** `docs/features/{slug}/db-schema.md`

**Context read:**
- `CLAUDE.md`, `architecture.md`, `multi-tenancy.md`, `tech-stack.md`
- `.claude/skills/forge/references/flyway-guide.md`
- `.claude/skills/forge/references/multi-tenancy-guide.md`
- `src/main/resources/db/migration/` → detect next version number
- Existing entity classes for the feature

**Process:**
1. Feature name from args
2. Read context + existing migrations
3. Detect next Flyway version (VN+1)
4. Design ERD + SQL + migration script + entity skeleton
5. Save + commit: `docs(db): add schema design for {feature}`

**Output structure:**
```markdown
# DB Schema — {Feature}

## ERD (Mermaid erDiagram)
## Table Definitions (column / type / nullable / default / description)
## Flyway Migration
  - File: V{NNN}__{feature_description}.sql
  - Full CREATE TABLE SQL
  - Index statements
## Entity Skeleton (Java)
## Index Strategy
## Migration Notes
```

**Hard constraints:**
- Every business table MUST have `business_id UUID NOT NULL` → extend `TenantEntity`
- `Business` is the only entity that does NOT extend `TenantEntity`
- PK: `UUID DEFAULT gen_random_uuid()` — never `BIGSERIAL`
- pgvector column: `VECTOR(1536)`
- tsvector: `TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', content)) STORED`
- Pure PostgreSQL 16 syntax — no H2 compatibility
- Flyway naming: `V{NNN}__{snake_case}.sql`
- Timestamps: `TIMESTAMPTZ` not `TIMESTAMP`
- Always index `business_id` and FK columns

---

### 5. `/test-plan` — Test Strategy + Test Cases

**Trigger phrases:** "test plan", "test strategy", "test cases for X", "what to test",
"viết test plan", "kiểm thử X", "test coverage for X"

**Output:** `docs/features/{slug}/test-plan.md`

**Context read:**
- `CLAUDE.md`, `constraints.md`
- `docs/features/{slug}/SRS.md` → map FR → test cases
- `docs/features/{slug}/api-spec.md` → map endpoints → integration tests
- `.claude/skills/forge/references/testing-patterns.md`
- `src/test/` → understand existing test structure

**Process:**
1. Feature name from args
2. Read context
3. Map each FR → at least 1 test case
4. Map each endpoint → integration test scenario
5. Identify new TenantEntity subclasses → flag for TenantIsolationIT extension
6. Save + commit: `docs(test): add test plan for {feature}`

**Output structure:**
```markdown
# Test Plan — {Feature}

## Scope
## Coverage Targets
  | Layer | Target |
  | Overall | 80%+ |
  | Service layer | 100% |
  | Tenant isolation | 100% |

## Unit Tests ({ClassName}Test — @ExtendWith(MockitoExtension.class))
  | Test class | Method | Scenario | Expected |

## Integration Tests ({ClassName}IT — @SpringBootTest @Testcontainers)
  | Test class | Endpoint | Scenario | Expected |

## Tenant Isolation Tests (extend TenantIsolationIT)
  | Scenario | Tenant A action | Tenant B should NOT see |

## E2E Scenarios
## Test Data Setup
## Out of Scope
```

**Hard constraints:**
- No H2 — use `pgvector/pgvector:pg16` Testcontainers
- Every FR in SRS must have ≥1 corresponding test case
- `TenantIsolationIT` MUST be updated if feature adds entity extending `TenantEntity`
- Async pipeline tests: use `CompletableFuture.get()` with timeout, not `Thread.sleep()`
- Tenant isolation tests must cover both read AND write paths

---

### 6. `/release` — Release Notes + Deploy Checklist

**Trigger phrases:** "release note", "release notes", "changelog", "what shipped",
"viết release", "deploy checklist", "v{X.Y.Z} release", "chuẩn bị release"

**Output:** `docs/releases/v{X.Y.Z}-release.md`

**Note:** Lives in `docs/releases/` (not `docs/features/`) because a release aggregates
multiple features. The skill reads `docs/features/*/SRS.md` of included features.

**Context read:**
- `CLAUDE.md`, `project-context.md`
- `git log {last-tag}..HEAD` → commit list
- `git tag --sort=-version:refname` → last release tag
- `docs/features/*/SRS.md` for features in this release
- `docs/adr/` → decisions made during this release

**Process:**
1. Version from args, or read from `pom.xml`
2. Read context
3. `git log` since last tag
4. Read feature SRS docs
5. Generate release notes + deploy checklist
6. Save + commit: `docs(release): add release notes for v{X.Y.Z}`

**Output structure:**
```markdown
# Release v{X.Y.Z} — {Milestone Name}

Date, Branch, Milestone

## What's New
  - Features (with SRS links)
  - Bug Fixes
  - Breaking Changes

## Technical Changes
  - New Endpoints
  - New DB Migrations
  - Dependencies Updated

## Deploy Checklist
  ### Pre-deploy
    - [ ] mvn verify passes
    - [ ] TenantIsolationIT passes
    - [ ] gitleaks detect (no secrets)
    - [ ] Flyway migrations tested on staging
    - [ ] .env.example updated
  ### Deploy steps
    - [ ] mvn clean package -DskipTests
    - [ ] mvn flyway:migrate
    - [ ] Deploy JAR
    - [ ] Smoke test: GET /actuator/health
  ### Post-deploy
    - [ ] Swagger UI accessible
    - [ ] Happy path test per feature
    - [ ] Monitor logs 10 min

## Architecture Decisions (this release)
## Known Limitations
## What's Next
```

---

### 7. `/adr` — Architecture Decision Record

**Trigger phrases:** "architecture decision", "ADR", "viết ADR", "should we use X or Y",
"technical decision", "quyết định kiến trúc", "tại sao chọn X"

**Output:** `docs/adr/ADR-{NNN}-{slug}.md`

**Note:** Cross-cutting decisions live in `docs/adr/`, not `docs/features/`.
ADR-0001 already exists — skill auto-detects next number.

**Context read:**
- `CLAUDE.md`, `architecture.md`, `tech-stack.md`
- All existing `docs/adr/ADR-*.md` → find next number

**Process:**
1. Decision topic from args
2. Read context + existing ADRs
3. If options not clear → ask: "Options nào đang được cân nhắc?"
4. Generate MADR format
5. Save + commit: `docs(adr): ADR-{NNN} — {title}`

**Output structure (MADR format):**
```markdown
# ADR-{NNN} — {Title}

Status: Proposed | Accepted | Deprecated | Superseded by ADR-{NNN}
Date: YYYY-MM-DD
Milestone: M{N}

## Context
## Decision Drivers
## Options Considered
  ### Option A — {name}: Description, Pros, Cons
  ### Option B — {name}: Description, Pros, Cons
## Decision
  Chosen: Option {X} + rationale
## Consequences
  Positive, Negative/Trade-offs, Risks
## Related
  - ADR-{NNN}, Feature links
```

**Rules:**
- Number format: `ADR-0001` (4 digits, zero-padded)
- Status `Accepted` only after decision is implemented
- `/release` skill automatically links new ADRs into release notes

---

## Summary

| Skill | Output | Key triggers |
|-------|--------|-------------|
| `/srs` | `docs/features/{slug}/SRS.md` | "viết SRS", "spec feature" |
| `/uml` | `docs/features/{slug}/diagrams/{type}.md` | "draw diagram", "vẽ sơ đồ" |
| `/api-spec` | `docs/features/{slug}/api-spec.md` | "design API", "API contract" |
| `/db-schema` | `docs/features/{slug}/db-schema.md` | "design table", "migration" |
| `/test-plan` | `docs/features/{slug}/test-plan.md` | "test plan", "kiểm thử" |
| `/release` | `docs/releases/v{X.Y.Z}-release.md` | "release note", "deploy checklist" |
| `/adr` | `docs/adr/ADR-{NNN}-{slug}.md` | "ADR", "should we use X or Y" |
