---
name: adr
version: 1.0.0
description: |
  Write an Architecture Decision Record (MADR format) for a technical decision.
  Output: docs/adr/ADR-{NNN}-{slug}.md
  Triggers: "architecture decision", "ADR", "should we use X or Y",
  "technical decision", "why did we choose", "document this decision"
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
**Milestone:** M{N} — {milestone name}

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

{Explain why this option was selected given the decision drivers. Be specific about trade-offs accepted.}

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

- {ADR-{NNN}: [title](ADR-{NNN}-{slug}.md)} — if this supersedes or relates to another ADR
- {Feature: [docs/features/{slug}/SRS.md](../features/{slug}/SRS.md)} — if tied to a feature
```

## Rules

- Number format: `ADR-0001` (4 digits, zero-padded) — auto-detect next number from existing files
- Status starts as `Proposed`, changes to `Accepted` only after the decision is implemented in code
- If this supersedes an older ADR, update the older ADR's Status line to `Superseded by ADR-{NNN}`
- ADRs live in `docs/adr/` — they are cross-cutting decisions, not feature-specific
- `/release` skill reads `docs/adr/` to link relevant ADRs in release notes
- Keep Context section honest: explain the actual constraints, not just the rationale for the chosen option
