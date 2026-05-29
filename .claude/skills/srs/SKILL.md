---
name: srs
version: 1.0.0
description: |
  Viết Formal SRS (IEEE 830) cho một feature. Output: docs/design/{slug}-srs.md
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
4. **Check for existing SRS** — if `docs/design/{slug}-srs.md` exists, read it first and enter **update mode** (patch changed sections only, do not overwrite the whole file)
5. **Scan existing code** — if `src/main/java/com/leonardtrinh/supportsaas/` has a package matching the feature, read relevant Controller/Service/Entity files to capture current state
6. **Create directory** — `mkdir -p docs/design`
7. **Generate SRS** — follow the IEEE 830 template below exactly
8. **Save** — write to `docs/design/{slug}-srs.md`
9. **Commit:**
   ```bash
   git add docs/design/{slug}-srs.md
   git commit -m "docs(srs): add SRS for {slug}"
   ```

## Output Template

Write the following structure to `docs/design/{slug}-srs.md`:

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
- [Architecture](../../../.claude/memory/architecture.md)
- [Tech Stack](../../../.claude/memory/tech-stack.md)

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
See [{slug}-api-spec.md]({slug}-api-spec.md) *(create with /api-spec)*

### 6.2 Database Interfaces
See [{slug}-erd.md]({slug}-erd.md) *(create with /db-schema)*

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
