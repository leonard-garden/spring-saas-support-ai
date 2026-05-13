---
name: sdlc-design
version: 1.0.0
description: |
  Design phase for SDLC workflow. Produces design system, wireframes, and API
  contract for a feature/milestone. Invoke after Discovery gate is approved,
  before plan-phase. Triggered by /sdlc orchestrator or manually with /sdlc-design.
---

<purpose>
Run the Design phase of the SDLC workflow for the current milestone/feature.
Produce three artifacts: DESIGN-SYSTEM.md, WIREFRAMES.md, API-CONTRACT.md.
Save all files to the current GSD phase directory (.planning/phases/<phase>/).
</purpose>

<process>

## Step 1: Read context

Before doing anything, read:
- `.planning/STATE.md` — use the current active phase recorded there if present. If STATE.md is absent, check `.planning/sdlc-state.md` — look for a line containing `<resolved-phase-number>:` or `phase-number:` and use that value. Otherwise, use the highest-numbered directory prefix in `.planning/phases/` by parsing the leading integer (e.g., `03-auth` → 3, `10-billing` → 10; take the maximum integer). Record the resolved directory name as `<current-phase>` — substitute it in all subsequent file paths.
- The CONTEXT.md or latest PLAN.md inside that phase dir (from gsd:discuss-phase output)
- `.planning/ROADMAP.md` — understand the milestone scope

If no phase directory exists yet, ask: "Which phase are we designing for? (e.g., 01-admin-ui)"

## Step 2: Design System (do FIRST — foundation for all screens)

Invoke the `frontend-design` skill.

If the `frontend-design` skill is unavailable, generate the design system directly using the specification below. Write the result to `.planning/phases/<current-phase>/DESIGN-SYSTEM.md`.

Brief it with:
- The milestone/feature name
- The target screens (extract from CONTEXT.md)
- Tech stack: React + Tailwind CSS + shadcn/ui

The design system MUST include:

**Colors:**
- Primary, secondary, neutral scale (50→900)
- Semantic: success, warning, error, info (with hex values)

**Typography:**
- Font family (default: Inter or system-ui)
- Size scale: xs(12px), sm(14px), base(16px), lg(18px), xl(20px), 2xl(24px), 3xl(30px)
- Weight: regular(400), medium(500), semibold(600), bold(700)

**Spacing:** 4px base unit scale (1=4px, 2=8px, 3=12px, 4=16px, 6=24px, 8=32px, 12=48px)

**Components (document each with variants and states):**
- Button: primary/secondary/ghost/destructive variants × sm/md/lg sizes
- Input: default/focus/error/disabled states
- Select, Textarea (same states as Input)
- Card: with header, body, footer slots
- Modal: with overlay, title, body, footer, close button
- Table: with sortable headers, empty state, loading state, pagination
- Badge: default/success/warning/error/info variants
- Avatar: with image and fallback initials
- Sidebar: collapsed/expanded states, nav item active/hover states
- Topbar: with logo slot, nav slot, user menu slot
- Alert/Toast: success/warning/error/info variants

**Layout patterns:**
- App shell: sidebar (240px expanded, 64px collapsed) + topbar (64px) + main content
- Responsive breakpoints: sm(640px), md(768px), lg(1024px), xl(1280px)

Save output to: `.planning/phases/<current-phase>/DESIGN-SYSTEM.md`

## Step 3: Wireframes (uses design system as base)

Before starting wireframe writing, spawn the architect agent (Step 4) concurrently now, then proceed with wireframes below.

For each screen identified in CONTEXT.md, create a wireframe description.

Use ONLY components defined in DESIGN-SYSTEM.md. Reference them by name.

Format per screen:
```
## Screen: [Name]
Route: /[path]
Purpose: [one sentence]

Layout:
  App Shell:
    Sidebar: [active nav item]
    Topbar: [title, user menu]
  
  Main Content:
    [Structured ASCII or text description of the layout]
    [List components used and their content]

States:
  Loading: [what shows during data fetch]
  Empty: [what shows when no data]
  Error: [what shows on API error]
  
User Actions:
  - [action] → [result]
```

Save to: `.planning/phases/<current-phase>/WIREFRAMES.md`

## Step 4: API Contract (run in parallel with Step 3)

Spawn an `oh-my-claudecode:architect` agent to produce the API contract.

Give the agent this context:
- The CONTEXT.md content (requirements) — derive API endpoints from these requirements directly
- Backend stack: Spring Boot 3.3, Java 21, PostgreSQL, JWT auth
- Response envelope: `{ "success": boolean, "data": T | null, "error": string | null }`

The agent must document every API endpoint needed for this milestone:

```
# API Contract — [Milestone Name]

## Authentication
All endpoints require: `Authorization: Bearer <jwt-token>` unless noted.

## [Feature Group]

### GET /api/v1/[resource]
Purpose: [what this does]
Auth: Required / Public
Query params:
  - param (type, optional/required): description
Request body: —
Response 200:
{
  "success": true,
  "data": {
    "field": "type"  // description
  }
}
Response 401: { "success": false, "error": "Unauthorized" }
Response 404: { "success": false, "error": "Resource not found" }

### POST /api/v1/[resource]
Purpose: [what this does]
Auth: Required
Request body:
{
  "field": "type"  // description, required/optional
}
Response 201: { "success": true, "data": { ... } }
Response 400: { "success": false, "error": "Validation message" }
Response 401: { "success": false, "error": "Unauthorized" }
```

Save to: `.planning/phases/<current-phase>/API-CONTRACT.md`

## Step 5: Gate — request approval

After all 3 files are created, report:

```
Design phase complete. Created:
✓ .planning/phases/<phase>/DESIGN-SYSTEM.md
✓ .planning/phases/<phase>/WIREFRAMES.md  
✓ .planning/phases/<phase>/API-CONTRACT.md

[GATE] Review the design artifacts above.
Approve to proceed to gsd:plan-phase? (y/n)
(Do not continue until you receive a response.)
```

Wait for user response. Do NOT proceed to plan-phase automatically.
- If y: Write to `.planning/sdlc-state.md` (append if exists, create if not): `design_gate: approved — {YYYY-MM-DD}`. Then respond "Design approved. Run /sdlc or gsd:plan-phase <number> to continue."
- If n: ask "What needs to be revised?" and revise the relevant artifact. After completing revisions, repeat the gate report above and wait for approval again.

</process>
