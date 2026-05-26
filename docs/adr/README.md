# Architecture Decision Records

This directory contains Architecture Decision Records (ADRs) for **spring-saas-support-ai**.

## What is an ADR?

An ADR documents a significant architectural decision: the context, the options
considered, and the rationale behind the choice. It is a permanent record — once
accepted, an ADR is never deleted, only superseded by a newer one.

## How to create a new ADR

1. Ask Claude: `"create a new ADR for [topic]"` — it will use the template below
2. Name it `ADR-XXXX-short-title.md` (increment the number)
3. Fill in all sections — do not leave placeholders
4. Update the index table below

## ADR template

```markdown
# ADR-XXXX: [Title]

**Date:** YYYY-MM-DD
**Status:** Proposed | Accepted | Superseded by ADR-XXXX

## Context

[What problem or situation requires a decision?]

## Options considered

1. **[Option A]** — [brief description]
2. **[Option B]** — [brief description]

## Decision

[What was decided and why?]

## Consequences

**Positive:**
- [...]

**Negative / trade-offs:**
- [...]

**Constraints this creates:**
- [...]
```

## Index

| ADR | Title | Status | Date |
|-----|-------|--------|------|
| [ADR-0001](ADR-0001-initial-architecture.md) | Initial Architecture | Accepted | 2026-05-26 |
