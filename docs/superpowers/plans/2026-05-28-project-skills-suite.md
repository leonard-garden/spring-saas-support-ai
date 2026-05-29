# Project Skills Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create 7 independent Claude Code skills (srs, uml, api-spec, db-schema, test-plan, release, adr) that give this Spring Boot project proper SDLC artifact generation.

**Architecture:** Each skill is a focused SKILL.md file in `.claude/skills/{name}/`. Skills are lazy-loaded instruction context — invoked manually or auto-triggered by keyword detection. They produce Markdown artifacts under `docs/features/{slug}/` or `docs/releases/` or `docs/adr/`.

**Tech Stack:** Claude Code skill system (YAML frontmatter + Markdown body), Spring Boot 3.3 / Java 21 / PostgreSQL 16 project context.

**Spec:** `docs/superpowers/specs/2026-05-28-project-skills-design.md`

---

## File Map

| Create | Purpose |
|--------|---------|
| `.claude/skills/srs/SKILL.md` | Formal SRS (IEEE 830) generator |
| `.claude/skills/uml/SKILL.md` | Mermaid diagram generator |
| `.claude/skills/api-spec/SKILL.md` | REST API contract generator |
| `.claude/skills/db-schema/SKILL.md` | DB schema + Flyway migration designer |
| `.claude/skills/test-plan/SKILL.md` | Test strategy + test cases generator |
| `.claude/skills/release/SKILL.md` | Release notes + deploy checklist generator |
| `.claude/skills/adr/SKILL.md` | Architecture Decision Record generator |
| `docs/features/.gitkeep` | Scaffold docs/features/ directory |
| `docs/releases/.gitkeep` | Scaffold docs/releases/ directory |

---

## Task 0: Scaffold directory structure

**Files:**
- Create: `docs/features/.gitkeep`
- Create: `docs/releases/.gitkeep`

- [ ] **Step 1: Create directories**

```bash
mkdir -p docs/features docs/releases
touch docs/features/.gitkeep docs/releases/.gitkeep
```

- [ ] **Step 2: Verify**

```bash
ls docs/features/ docs/releases/
```

Expected output:
```
docs/features/:
.gitkeep

docs/releases/:
.gitkeep
```

- [ ] **Step 3: Commit**

```bash
git add docs/features/.gitkeep docs/releases/.gitkeep
git commit -m "chore(docs): scaffold features/ and releases/ directories"
```

---

## Task 1: `/srs` skill — Formal SRS (IEEE 830)

**Files:**
- Create: `.claude/skills/srs/SKILL.md`

- [ ] **Step 1: Create skill directory**

```bash
mkdir -p .claude/skills/srs
```

- [ ] **Step 2: Write SKILL.md**

Create `.claude/skills/srs/SKILL.md` with this exact content:

```markdown
---
name: srs
version: 1.0.0
description: |
  Viết Formal SRS (IEEE 830) cho một feature. Output: docs/features/{slug}/SRS.md
  Triggers: "viết SRS", "write SRS", "spec feature X", "requirements for X",
  "viết requirements", "SRS for X"
---

## Purpose

Write a Formal Software Requirements Specification (IEEE 830) for a feature.

## Invoke

`/srs {feature-name}` — or auto-triggered by trigger phrases above.

## Process

1. **Get feature name** — from args. If missing, ask: "Which feature are we writing the SRS for? (e.g., ai-chat, billing, widget-embed)"
2. **Derive slug** — kebab-case: "AI Chat" → `ai-chat`, "Widget Embed" → `widget-embed`
3. **Read context:**
   - `CLAUDE.md`
   - `.claude/memory/architecture.md`
   - `.claude/memory/tech-stack.md`
   - `.claude/memory/project-context.md`
   - `.claude/memory/constraints.md`
4. **Check for existing SRS** — if `docs/features/{slug}/SRS.md` exists, read it first and enter **update mode** (patch changed sections only, do not overwrite the whole file)
5. **Scan existing code** — if `src/main/java/com/leonardtrinh/supportsaas/` has a package matching the feature, read relevant Controller/Service/Entity files to capture current state
6. **Create directory** — `mkdir -p docs/features/{slug}`
7. **Generate SRS** — follow the IEEE 830 template below exactly
8. **Save** — write to `docs/features/{slug}/SRS.md`
9. **Commit:**
   ```bash
   git add docs/features/{slug}/SRS.md
   git commit -m "docs(srs): add SRS for {slug}"
   ```

## Output Template

Write the following structure to `docs/features/{slug}/SRS.md`:

```
# Software Requirements Specification — {Feature Name}

**Version:** 1.0
**Date:** {YYYY-MM-DD}
**Status:** Draft
**Author:** {git config user.name}

---

## 1. Introduction

### 1.1 Purpose
{What this document describes and who it is for — dev team and AI agents implementing the feature}

### 1.2 Scope
{What this feature does, what system it is part of (spring-saas-support-ai), what it does NOT do}

### 1.3 Definitions & Acronyms
| Term | Definition |
|------|-----------|
| JWT  | JSON Web Token — used for authentication |
| RAG  | Retrieval-Augmented Generation |
| RRF  | Reciprocal Rank Fusion — used in hybrid search |
| SSE  | Server-Sent Events — used for streaming chat |

### 1.4 References
- [CLAUDE.md](../../../CLAUDE.md)
- [Architecture](.claude/memory/architecture.md)
- [Tech Stack](.claude/memory/tech-stack.md)

---

## 2. Overall Description

### 2.1 Product Perspective
{How this feature fits into the spring-saas-support-ai multi-tenant SaaS platform}

### 2.2 User Classes & Characteristics
| User Class | Description | Access Level |
|-----------|-------------|-------------|
| Business Owner | Created account, manages KB and team | ADMIN role |
| Member | Invited by owner, uses support tools | MEMBER role |
| End User | Customer using the embedded widget | PUBLIC (no JWT) |

### 2.3 Operating Environment
- Java 21 (records, sealed interfaces), Spring Boot 3.3
- PostgreSQL 16 + pgvector extension
- Spring AI 1.1+ (Anthropic Claude / OpenAI embeddings)
- Deployed on Render/Railway

### 2.4 Design Constraints
- No Lombok — use Java 21 records for DTOs
- No H2 — integration tests use Testcontainers (pgvector/pgvector:pg16)
- No JdbcTemplate — use @Modifying @Query(nativeQuery=true) on JpaRepository
- No bare @Async — always @Async("processingExecutor")
- Multi-tenancy: all business entities must extend TenantEntity with business_id
- virtual threads disabled (spring.threads.virtual.enabled=false)

### 2.5 Assumptions & Dependencies
{List assumptions about other features, external services, environment variables needed}

---

## 3. Functional Requirements

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-001 | {Specific, testable requirement} | High | {How to verify pass/fail} |
| FR-002 | {Specific, testable requirement} | Med | {How to verify pass/fail} |

*Every requirement must be independently testable (clear pass/fail criteria).*

---

## 4. Non-Functional Requirements

### 4.1 Performance
{Response time targets, throughput, concurrent user expectations}

### 4.2 Security
{Auth requirements, data isolation, rate limiting, input validation}

### 4.3 Reliability
{Error handling, retry logic, failure modes}

### 4.4 Scalability
{Multi-tenant isolation at scale, async processing requirements}

---

## 5. Use Cases

### UC-001: {Use Case Name}
**Actor:** {Business Owner | Member | End User}
**Preconditions:** {What must be true before this flow starts}
**Main Flow:**
1. {Step}
2. {Step}
**Alternate Flows:**
- A1: {Condition} → {Outcome}
**Postconditions:** {State of system after success}

---

## 6. External Interface Requirements

### 6.1 API Interfaces
See [api-spec.md](api-spec.md) *(create with /api-spec)*

### 6.2 Database Interfaces
See [db-schema.md](db-schema.md) *(create with /db-schema)*

---

## 7. Out of Scope
- {Explicit list of what this feature does NOT include}
```

## Rules

- Each FR must be numbered (FR-001, FR-002...) and have clear acceptance criteria
- Section 2.4 Design Constraints must always include the full project constraint list above
- If feature adds a new entity, note it in 6.2 Database Interfaces
- If feature adds public endpoints (no JWT), note them in 6.1 API Interfaces
- Use relative links for cross-references: `[api-spec.md](api-spec.md)` not absolute paths
```

- [ ] **Step 3: Verify file**

```bash
ls .claude/skills/srs/
grep "^name:" .claude/skills/srs/SKILL.md
grep "^version:" .claude/skills/srs/SKILL.md
```

Expected:
```
SKILL.md
name: srs
version: 1.0.0
```

- [ ] **Step 4: Commit**

```bash
git add .claude/skills/srs/SKILL.md
git commit -m "feat(skill): add /srs — Formal SRS IEEE 830 generator"
```

---

## Task 2: `/uml` skill — Mermaid Diagrams

**Files:**
- Create: `.claude/skills/uml/SKILL.md`

- [ ] **Step 1: Create skill directory**

```bash
mkdir -p .claude/skills/uml
```

- [ ] **Step 2: Write SKILL.md**

Create `.claude/skills/uml/SKILL.md`:

```markdown
---
name: uml
version: 1.0.0
description: |
  Vẽ Mermaid diagrams cho feature: sequence, class, ER, component, flowchart.
  Output: docs/features/{slug}/diagrams/{type}.md
  Triggers: "draw diagram", "sequence diagram", "class diagram", "vẽ sơ đồ",
  "vẽ biểu đồ", "ER diagram", "component diagram", "flow diagram", "vẽ sequence"
---

## Purpose

Generate Mermaid diagrams for a feature and save to `docs/features/{slug}/diagrams/`.

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
5. **Read SRS if exists** — `docs/features/{slug}/SRS.md` for requirements context
6. **Check existing diagram** — if target file exists, read it and enter update mode
7. **Create directory** — `mkdir -p docs/features/{slug}/diagrams`
8. **Generate Mermaid diagram** — follow conventions below
9. **Save** — `docs/features/{slug}/diagrams/{type}.md`
10. **Commit:**
    ```bash
    git add docs/features/{slug}/diagrams/{type}.md
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
- {Important annotation, e.g., "TenantContext is set in JwtAuthFilter before Controller"}
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
```

- [ ] **Step 3: Verify**

```bash
ls .claude/skills/uml/
grep "^name:" .claude/skills/uml/SKILL.md
```

Expected:
```
SKILL.md
name: uml
```

- [ ] **Step 4: Commit**

```bash
git add .claude/skills/uml/SKILL.md
git commit -m "feat(skill): add /uml — Mermaid diagram generator"
```

---

## Task 3: `/api-spec` skill — REST API Contract

**Files:**
- Create: `.claude/skills/api-spec/SKILL.md`

- [ ] **Step 1: Create skill directory**

```bash
mkdir -p .claude/skills/api-spec
```

- [ ] **Step 2: Write SKILL.md**

Create `.claude/skills/api-spec/SKILL.md`:

```markdown
---
name: api-spec
version: 1.0.0
description: |
  Thiết kế REST API contract cho feature. Output: docs/features/{slug}/api-spec.md
  Triggers: "design API", "REST contract", "API cho X", "thiết kế API",
  "endpoint for X", "API contract", "viết API spec", "API design"
---

## Purpose

Design and document the REST API contract for a feature.

## Invoke

`/api-spec {feature-name}` — or auto-triggered.

## Process

1. **Get feature name** — from args or ask.
2. **Derive slug** — kebab-case.
3. **Read context:**
   - `CLAUDE.md` — especially the ApiResponse<T> envelope format
   - `.claude/memory/architecture.md`
   - `.claude/memory/tech-stack.md`
   - `.claude/skills/forge/references/api-patterns.md`
4. **Read SRS** — `docs/features/{slug}/SRS.md` if it exists. Map each Functional Requirement to candidate endpoints.
5. **Scan existing controllers** — check `src/main/java/com/leonardtrinh/supportsaas/` for any existing Controller for this feature. If found, reverse-engineer the spec from code (code is source of truth).
6. **Read security config** — `src/main/java/com/leonardtrinh/supportsaas/config/SecurityConfig.java` to understand which paths are public vs JWT-protected.
7. **Create directory** — `mkdir -p docs/features/{slug}`
8. **Generate API spec** — follow template below
9. **Save** — `docs/features/{slug}/api-spec.md`
10. **Commit:**
    ```bash
    git add docs/features/{slug}/api-spec.md
    git commit -m "docs(api): add API spec for {slug}"
    ```

## Output Template

```
# API Spec — {Feature Name}

**Version:** 1.0
**Date:** {YYYY-MM-DD}
**Base URL:** /api/v1/

---

## Overview

| Property | Value |
|----------|-------|
| Auth | Bearer JWT in `Authorization` header (except public endpoints) |
| Response envelope | `ApiResponse<T>` — `{success: bool, data: T, error: string}` |
| Error format | RFC 7807 ProblemDetail |
| Content-Type | application/json |

---

## Endpoints

### POST /api/v1/{resource}

**Description:** {What this endpoint does}
**Auth required:** YES / NO
**Roles:** ADMIN \| MEMBER \| PUBLIC

**Request body:**
​```json
{
  "field": "string — description",
  "field2": "uuid — description"
}
​```

**Response 201 Created:**
​```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "field": "value"
  },
  "error": null
}
​```

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 400  | Validation failed — missing required field |
| 401  | Missing or invalid JWT |
| 403  | Insufficient role |
| 404  | Resource not found |
| 409  | Conflict — resource already exists |
| 429  | Rate limit exceeded |

---

### GET /api/v1/{resource}/{id}

{same structure}

---

## Common Error Format

All errors follow RFC 7807 ProblemDetail:
​```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Document abc-123 not found",
  "instance": "/api/v1/documents/abc-123"
}
​```

---

## Rate Limiting
{Describe limits if applicable, e.g., "10 req/min per tenant on POST endpoints"}

---

## Swagger Annotations Reference

Each endpoint should have these annotations on the Controller method:
​```java
@Operation(summary = "{description}")
@ApiResponse(responseCode = "201", description = "{success description}")
@ApiResponse(responseCode = "400", description = "Validation failed")
@ApiResponse(responseCode = "401", description = "Unauthorized")
​```
```

## Rules

- Every response uses `ApiResponse<T>` envelope — never return naked objects or naked lists
- Error body is always `ProblemDetail` (RFC 7807) — never a custom error JSON
- Path convention: `/api/v1/{resource}/{id}/{sub-resource}`
- Widget/public endpoints must be explicitly marked `Auth required: NO` and listed separately
- Always suggest `@Operation` + `@ApiResponse` annotations — the `api-doc-sync` agent enforces these
- Multi-tenant note: all tenant-scoped endpoints implicitly filter by the JWT's `tenant_id` — document this in Overview
```

- [ ] **Step 3: Verify**

```bash
ls .claude/skills/api-spec/
grep "^name:" .claude/skills/api-spec/SKILL.md
```

Expected:
```
SKILL.md
name: api-spec
```

- [ ] **Step 4: Commit**

```bash
git add .claude/skills/api-spec/SKILL.md
git commit -m "feat(skill): add /api-spec — REST API contract generator"
```

---

## Task 4: `/db-schema` skill — Database Schema + Flyway Migration

**Files:**
- Create: `.claude/skills/db-schema/SKILL.md`

- [ ] **Step 1: Create skill directory**

```bash
mkdir -p .claude/skills/db-schema
```

- [ ] **Step 2: Write SKILL.md**

Create `.claude/skills/db-schema/SKILL.md`:

```markdown
---
name: db-schema
version: 1.0.0
description: |
  Thiết kế database schema + Flyway migration cho feature.
  Output: docs/features/{slug}/db-schema.md
  Triggers: "design table", "db schema", "migration for X", "ERD", "thiết kế bảng",
  "flyway migration", "database schema", "schema for X", "tạo bảng"
---

## Purpose

Design database tables, produce Flyway migration SQL, and draft JPA entity skeletons for a feature.

## Invoke

`/db-schema {feature-name}` — or auto-triggered.

## Process

1. **Get feature name** — from args or ask.
2. **Derive slug** — kebab-case.
3. **Read context:**
   - `CLAUDE.md`
   - `.claude/memory/architecture.md`
   - `.claude/memory/multi-tenancy.md`
   - `.claude/memory/tech-stack.md`
   - `.claude/skills/forge/references/flyway-guide.md`
   - `.claude/skills/forge/references/multi-tenancy-guide.md`
4. **Read SRS** — `docs/features/{slug}/SRS.md` for data requirements.
5. **Scan existing migrations** — list files in `src/main/resources/db/migration/`, find the highest V-number. New migration = that number + 1 (zero-padded to 3 digits, e.g., V005).
6. **Read existing entity classes** — check `src/main/java/com/leonardtrinh/supportsaas/` for entities related to this feature.
7. **Create directory** — `mkdir -p docs/features/{slug}`
8. **Generate schema design** — follow template below
9. **Save** — `docs/features/{slug}/db-schema.md`
10. **Commit:**
    ```bash
    git add docs/features/{slug}/db-schema.md
    git commit -m "docs(db): add schema design for {slug}"
    ```

## Output Template

```
# DB Schema — {Feature Name}

**Version:** 1.0
**Date:** {YYYY-MM-DD}
**Flyway migration:** V{NNN}__{feature_description}.sql

---

## ERD

​```mermaid
erDiagram
  TenantEntity {
    uuid business_id FK
  }
  {TableName} {
    uuid id PK
    uuid business_id FK
    varchar field_name
    timestamptz created_at
    timestamptz updated_at
  }
  businesses ||--o{ {TableName} : "owns"
​```

---

## Table Definitions

### {table_name}

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | UUID | NOT NULL | gen_random_uuid() | Primary key |
| business_id | UUID | NOT NULL | — | Tenant FK → businesses(id) |
| {field} | {type} | {nullable} | {default} | {description} |
| created_at | TIMESTAMPTZ | NOT NULL | now() | |
| updated_at | TIMESTAMPTZ | NOT NULL | now() | |

---

## Flyway Migration

**File:** `src/main/resources/db/migration/V{NNN}__{feature_description}.sql`

​```sql
CREATE TABLE {table_name} (
  id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id  UUID        NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  {column}     {TYPE}      {NOT NULL | NULL} {DEFAULT},
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Always index business_id for tenant filtering performance
CREATE INDEX idx_{table_name}_business_id ON {table_name}(business_id);

-- Add additional indexes for frequently queried columns
-- CREATE INDEX idx_{table_name}_{column} ON {table_name}({column});
​```

---

## Entity Skeleton (Java)

​```java
@Entity
@Table(name = "{table_name}")
public class {ClassName} extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // {field}: {description}
    @Column(name = "{column}", nullable = {true|false})
    private {Type} {field};

    // Getters, setters, or use record pattern for DTOs
}
​```

---

## Index Strategy

| Index | Columns | Reason |
|-------|---------|--------|
| idx_{table}_business_id | business_id | Tenant filter on every query |
| {idx name} | {columns} | {why} |

---

## Migration Notes

- Run `mvn flyway:info` before applying to verify version sequence
- Test migration on local docker-compose postgres before committing
- If column uses `vector` type: `{column} VECTOR(1536)` (OpenAI text-embedding-3-small)
- If column uses full-text search: `{column}_tsv TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', {column})) STORED`
```

## Hard Constraints

- Every business table MUST have `business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE` — no exceptions except `businesses` table itself
- JPA entity MUST extend `TenantEntity` — this wires the Hibernate tenant filter
- PK: `UUID PRIMARY KEY DEFAULT gen_random_uuid()` — never `BIGSERIAL` or `SERIAL`
- Timestamps: `TIMESTAMPTZ` not `TIMESTAMP` — timezone-aware
- Pure PostgreSQL 16 syntax — no H2-compatible constructs
- Flyway file: `V{NNN}__{snake_case_description}.sql` — zero-pad version to 3 digits
- Always create index on `business_id` — the Hibernate filter runs this on every query
```

- [ ] **Step 3: Verify**

```bash
ls .claude/skills/db-schema/
grep "^name:" .claude/skills/db-schema/SKILL.md
```

Expected:
```
SKILL.md
name: db-schema
```

- [ ] **Step 4: Commit**

```bash
git add .claude/skills/db-schema/SKILL.md
git commit -m "feat(skill): add /db-schema — DB schema + Flyway migration designer"
```

---

## Task 5: `/test-plan` skill — Test Strategy

**Files:**
- Create: `.claude/skills/test-plan/SKILL.md`

- [ ] **Step 1: Create skill directory**

```bash
mkdir -p .claude/skills/test-plan
```

- [ ] **Step 2: Write SKILL.md**

Create `.claude/skills/test-plan/SKILL.md`:

```markdown
---
name: test-plan
version: 1.0.0
description: |
  Viết test plan đầy đủ cho feature: unit, integration, tenant isolation, e2e.
  Output: docs/features/{slug}/test-plan.md
  Triggers: "test plan", "test strategy", "test cases for X", "what to test",
  "viết test plan", "kiểm thử X", "test coverage for X", "how to test X"
---

## Purpose

Write a comprehensive test plan covering unit, integration, tenant isolation, and E2E scenarios.

## Invoke

`/test-plan {feature-name}` — or auto-triggered.

## Process

1. **Get feature name** — from args or ask.
2. **Derive slug** — kebab-case.
3. **Read context:**
   - `CLAUDE.md` — testing rules and coverage targets
   - `.claude/memory/constraints.md` — coverage minimums
   - `.claude/skills/forge/references/testing-patterns.md`
4. **Read SRS** — `docs/features/{slug}/SRS.md`. Map each FR (FR-001, FR-002...) to ≥1 test case.
5. **Read API spec** — `docs/features/{slug}/api-spec.md`. Map each endpoint to ≥1 integration test.
6. **Scan existing tests** — `src/test/java/com/leonardtrinh/supportsaas/` to understand patterns and find `TenantIsolationIT`.
7. **Identify new entities** — if feature adds entity extending `TenantEntity`, flag it for `TenantIsolationIT` extension.
8. **Create directory** — `mkdir -p docs/features/{slug}`
9. **Generate test plan** — follow template below
10. **Save** — `docs/features/{slug}/test-plan.md`
11. **Commit:**
    ```bash
    git add docs/features/{slug}/test-plan.md
    git commit -m "docs(test): add test plan for {slug}"
    ```

## Output Template

```
# Test Plan — {Feature Name}

**Version:** 1.0
**Date:** {YYYY-MM-DD}
**SRS reference:** [SRS.md](SRS.md)

---

## Scope

{What is being tested: which packages, which endpoints, which entities}

---

## Coverage Targets

| Layer | Target | Notes |
|-------|--------|-------|
| Overall line coverage | 80%+ | `mvn verify` jacoco report |
| Service layer | 100% | All service methods |
| Tenant isolation paths | 100% | TenantIsolationIT |

---

## Unit Tests

**Convention:** `{ClassName}Test.java` + `@ExtendWith(MockitoExtension.class)`
**Location:** `src/test/java/com/leonardtrinh/supportsaas/{feature-package}/`

| Test class | Method under test | Scenario | Input | Expected |
|-----------|------------------|----------|-------|---------|
| {ServiceImpl}Test | {methodName} | Happy path — {FR-001} | {input} | {output} |
| {ServiceImpl}Test | {methodName} | Error case — {condition} | {input} | throws {ExceptionType} |

**Example structure:**
​```java
@ExtendWith(MockitoExtension.class)
class {ServiceImpl}Test {

    @Mock private {Repository} repository;
    @InjectMocks private {ServiceImpl} service;

    @Test
    void {methodName}_shouldReturn{X}_when{Condition}() {
        // arrange
        when(repository.findById(any())).thenReturn(Optional.of(new {Entity}()));
        // act
        var result = service.{method}({input});
        // assert
        assertThat(result).isNotNull();
    }
}
​```

---

## Integration Tests

**Convention:** `{ClassName}IT.java` + `@SpringBootTest` + `@Testcontainers`
**DB image:** `pgvector/pgvector:pg16`

| Test class | Endpoint | Method | Scenario | Expected HTTP |
|-----------|----------|--------|----------|--------------|
| {Controller}IT | /api/v1/{resource} | POST | Happy path | 201 + ApiResponse.success=true |
| {Controller}IT | /api/v1/{resource} | POST | Missing field | 400 + ProblemDetail |
| {Controller}IT | /api/v1/{resource}/{id} | GET | Not found | 404 + ProblemDetail |
| {Controller}IT | /api/v1/{resource} | GET | No JWT | 401 |

---

## Tenant Isolation Tests

**Extend:** `TenantIsolationIT.java`

{If feature adds new entity extending TenantEntity, add these scenarios:}

| Scenario | Tenant A action | Tenant B assertion |
|----------|----------------|-------------------|
| Read isolation | Create {entity} as Tenant A | GET /api/v1/{resource} as Tenant B → empty list |
| Write isolation | POST /api/v1/{resource} as Tenant B | Cannot see or modify Tenant A's {entity} |

---

## E2E Scenarios

| Flow | Actor | Steps | Expected outcome |
|------|-------|-------|-----------------|
| {Happy path flow} | {User class} | 1. {step} 2. {step} | {outcome} |
| {Error path flow} | {User class} | 1. {step} | {error outcome} |

---

## Test Data Setup

{Describe any fixtures, seed data, or TestEntityManager setup needed}

---

## Out of Scope

- {What is NOT being tested and why}
```

## Hard Constraints

- No H2 — always `pgvector/pgvector:pg16` in `@Container` for integration tests
- Every FR in SRS must appear in at least one test case row
- `TenantIsolationIT` MUST be updated if feature adds any entity extending `TenantEntity`
- Async pipeline tests: use `CompletableFuture.get(5, TimeUnit.SECONDS)` not `Thread.sleep()`
- Tenant isolation must cover both read (GET) AND write (POST/PUT/DELETE) paths
- Unit test class naming: `{ClassName}Test`, integration test: `{ClassName}IT`
```

- [ ] **Step 3: Verify**

```bash
ls .claude/skills/test-plan/
grep "^name:" .claude/skills/test-plan/SKILL.md
```

Expected:
```
SKILL.md
name: test-plan
```

- [ ] **Step 4: Commit**

```bash
git add .claude/skills/test-plan/SKILL.md
git commit -m "feat(skill): add /test-plan — test strategy and test cases generator"
```

---

## Task 6: `/release` skill — Release Notes + Deploy Checklist

**Files:**
- Create: `.claude/skills/release/SKILL.md`

- [ ] **Step 1: Create skill directory**

```bash
mkdir -p .claude/skills/release
```

- [ ] **Step 2: Write SKILL.md**

Create `.claude/skills/release/SKILL.md`:

```markdown
---
name: release
version: 1.0.0
description: |
  Tạo release notes + deploy checklist cho một version.
  Output: docs/releases/v{X.Y.Z}-release.md
  Triggers: "release note", "release notes", "changelog", "what shipped",
  "viết release", "deploy checklist", "chuẩn bị release", "release v"
---

## Purpose

Generate release notes and a deploy checklist for a version, aggregating across all features in that release.

## Invoke

`/release v{X.Y.Z}` — or auto-triggered.

## Process

1. **Get version** — from args (e.g., `v0.3.0`). If missing, read `<version>` from `pom.xml`.
2. **Read context:**
   - `CLAUDE.md`
   - `.claude/memory/project-context.md` — roadmap and milestone info
3. **Find last release tag:**
   ```bash
   git tag --sort=-version:refname | head -5
   ```
4. **Get commits since last tag:**
   ```bash
   git log {last-tag}..HEAD --oneline --no-merges
   ```
5. **Identify features in this release** — from commit messages and milestone context. For each feature slug found, read `docs/features/{slug}/SRS.md`.
6. **Scan new ADRs** — read `docs/adr/` and find ADRs created after the last release date.
7. **Check pom.xml** for dependency version changes since last tag:
   ```bash
   git diff {last-tag}..HEAD -- pom.xml | grep "^[+-].*<version>"
   ```
8. **Create directory** — `mkdir -p docs/releases`
9. **Generate release doc** — follow template below
10. **Save** — `docs/releases/v{X.Y.Z}-release.md`
11. **Commit:**
    ```bash
    git add docs/releases/v{X.Y.Z}-release.md
    git commit -m "docs(release): add release notes for v{X.Y.Z}"
    ```

## Output Template

```
# Release v{X.Y.Z} — {Milestone Name}

**Date:** {YYYY-MM-DD}
**Branch:** develop → master
**Milestone:** M{N} — {description}
**Previous release:** v{X.Y.Z-1}

---

## What's New

### Features
- **{Feature Name}**: {one-line description} — [SRS](../features/{slug}/SRS.md)

### Bug Fixes
- {fix description} (#{issue-number})

### Breaking Changes
- {list breaking changes, or "None"}

---

## Technical Changes

### New Endpoints
| Method | Path | Description |
|--------|------|-------------|

### New DB Migrations
| File | Description |
|------|-------------|

### Dependencies Updated
| Package | From | To | Reason |
|---------|------|----|--------|

---

## Deploy Checklist

### Pre-deploy
- [ ] `mvn verify` passes (unit + integration tests)
- [ ] `TenantIsolationIT` passes
- [ ] `gitleaks detect --source . --no-banner` — no secrets found
- [ ] Flyway migrations tested on a copy of staging DB
- [ ] `.env.example` updated if new environment variables added
- [ ] Swagger UI verified locally: `http://localhost:8081/swagger-ui.html`

### Deploy steps
- [ ] Build: `mvn clean package -DskipTests`
- [ ] Apply migrations: `mvn flyway:migrate` (or Flyway auto-migrate on startup)
- [ ] Deploy JAR to Render/Railway
- [ ] Smoke test: `curl https://{host}/actuator/health` → `{"status":"UP"}`

### Post-deploy
- [ ] Swagger UI accessible at `https://{host}/swagger-ui.html`
- [ ] Test 1 happy-path flow per new feature using Swagger UI or curl
- [ ] Monitor application logs for 10 minutes post-deploy
- [ ] Update GitHub milestone to Closed

---

## Architecture Decisions (this release)

{List ADRs created during this milestone, or "None"}
- [ADR-{NNN}](../adr/ADR-{NNN}-{slug}.md): {title}

---

## Known Limitations

{List known bugs or limitations being shipped with this version}

---

## What's Next (v{X.Y.Z+1})

{Brief preview of next milestone}
```

## Rules

- Version format: `v{major}.{minor}.{patch}` — match git tag format
- Release doc lives in `docs/releases/` not `docs/features/` — it aggregates multiple features
- Always run the `git log` command to get the actual commits — don't guess what shipped
- Deploy checklist items are actionable checkboxes — not prose descriptions
```

- [ ] **Step 3: Verify**

```bash
ls .claude/skills/release/
grep "^name:" .claude/skills/release/SKILL.md
```

Expected:
```
SKILL.md
name: release
```

- [ ] **Step 4: Commit**

```bash
git add .claude/skills/release/SKILL.md
git commit -m "feat(skill): add /release — release notes and deploy checklist generator"
```

---

## Task 7: `/adr` skill — Architecture Decision Record

**Files:**
- Create: `.claude/skills/adr/SKILL.md`

- [ ] **Step 1: Create skill directory**

```bash
mkdir -p .claude/skills/adr
```

- [ ] **Step 2: Write SKILL.md**

Create `.claude/skills/adr/SKILL.md`:

```markdown
---
name: adr
version: 1.0.0
description: |
  Viết Architecture Decision Record (MADR format) cho technical decision.
  Output: docs/adr/ADR-{NNN}-{slug}.md
  Triggers: "architecture decision", "ADR", "viết ADR", "should we use X or Y",
  "technical decision", "quyết định kiến trúc", "tại sao chọn X", "why did we choose"
---

## Purpose

Document an architecture decision in MADR (Markdown Architectural Decision Records) format.

## Invoke

`/adr {decision-topic}` — or auto-triggered.

## Process

1. **Get decision topic** — from args or ask: "What is the technical decision we're documenting?"
2. **Read context:**
   - `CLAUDE.md`
   - `.claude/memory/architecture.md`
   - `.claude/memory/tech-stack.md`
3. **Find next ADR number** — list `docs/adr/ADR-*.md`, find the highest 4-digit number, increment by 1. If no ADRs exist, start at 0001.
   ```bash
   ls docs/adr/ADR-*.md 2>/dev/null | sort | tail -1
   ```
4. **Derive slug** — kebab-case of decision topic (e.g., "SSE vs WebSocket" → `sse-vs-websocket`)
5. **Gather options** — if the user hasn't listed the options being considered, ask: "Which options were considered? (e.g., Option A: SSE, Option B: WebSocket)"
6. **Generate ADR** — follow MADR template below
7. **Save** — `docs/adr/ADR-{NNN}-{slug}.md`
8. **Commit:**
   ```bash
   git add docs/adr/ADR-{NNN}-{slug}.md
   git commit -m "docs(adr): ADR-{NNN} — {title}"
   ```

## Output Template (MADR format)

```
# ADR-{NNN} — {Title}

**Status:** Proposed
**Date:** {YYYY-MM-DD}
**Milestone:** M{N} — {milestone name} *(context when this decision was made)*

---

## Context

{Describe the problem or situation requiring a decision. Include:
- What constraint or requirement triggered this?
- What happens if we don't decide now?
- Any relevant prior decisions that constrain the options?}

---

## Decision Drivers

- {Driver 1: e.g., "ThreadLocal-based TenantContext is incompatible with virtual thread pinning"}
- {Driver 2: e.g., "Must not introduce new infrastructure before M4"}
- {Driver 3: e.g., "Must support browser-native EventSource API for widget"}

---

## Options Considered

### Option A — {Name}

**Description:** {What this option is}

**Pros:**
- {advantage}

**Cons:**
- {disadvantage}

---

### Option B — {Name}

**Description:** {What this option is}

**Pros:**
- {advantage}

**Cons:**
- {disadvantage}

---

## Decision

**Chosen: Option {X} — {Name}**

{Explain why this option was selected given the decision drivers. Be specific about the trade-offs accepted.}

---

## Consequences

### Positive
- {benefit that results from this decision}

### Negative / Trade-offs
- {cost or limitation accepted}

### Risks
- {risk introduced by this decision and how it will be mitigated}

---

## Related

- {ADR-{NNN}: [title](ADR-{NNN}-{slug}.md)} *(if this supersedes or relates to another ADR)*
- {Feature: [docs/features/{slug}/SRS.md](../features/{slug}/SRS.md)} *(if tied to a feature)*
```

## Rules

- Number format: `ADR-0001` (4 digits, zero-padded) — auto-detect next number from existing files
- Status starts as `Proposed`, changes to `Accepted` only after the decision is implemented in code
- If this supersedes an older ADR, update the older ADR's Status to `Superseded by ADR-{NNN}`
- ADRs live in `docs/adr/` — they are cross-cutting decisions, not feature-specific
- `/release` skill reads `docs/adr/` to link relevant ADRs in release notes
- Keep Context section honest: explain the actual constraints, not just the chosen option
```

- [ ] **Step 3: Verify**

```bash
ls .claude/skills/adr/
grep "^name:" .claude/skills/adr/SKILL.md
```

Expected:
```
SKILL.md
name: adr
```

- [ ] **Step 4: Commit**

```bash
git add .claude/skills/adr/SKILL.md
git commit -m "feat(skill): add /adr — Architecture Decision Record generator"
```

---

## Final Verification

- [ ] **Verify all 7 skills are present:**

```bash
ls .claude/skills/
```

Expected output includes: `adr  api-spec  db-schema  forge  release  sdlc  sdlc-design  sdlc-pre-release  sdlc-qa-task  srs  test-plan  uml`

- [ ] **Verify all SKILL.md files have valid frontmatter:**

```bash
for skill in srs uml api-spec db-schema test-plan release adr; do
  echo "=== $skill ===" && grep -E "^(name|version):" .claude/skills/$skill/SKILL.md
done
```

Expected — each skill shows its name and version 1.0.0.

- [ ] **Verify docs directory structure:**

```bash
ls docs/features/ docs/releases/ docs/adr/
```

Expected: `.gitkeep` in features/ and releases/, `ADR-0001-*` in adr/.
