# SDLC Automation Design — GSD + Custom Skills

**Date:** 2026-05-13
**Topic:** Automating the SDLC workflow framework using GSD + custom orchestrator skill
**Depends on:** `2026-05-13-sdlc-workflow-framework-design.md`
**Status:** Approved — pending implementation plan

---

## Goal

Convert the SDLC workflow framework into a single reusable command:

```
/sdlc "Admin UI for M1"
```

No prompt rewriting per milestone. One command runs the full workflow.

---

## Approach: GSD + 3 Custom Extension Skills

GSD covers ~65% of the workflow. 3 custom skills extend the gaps.

---

## Section 1: Architecture

### File Structure

```
skills/
├── sdlc/
│   └── SKILL.md          ← orchestrator — entry point duy nhất
├── sdlc-design/
│   └── SKILL.md          ← Design phase (design system, wireframes, API contract)
├── sdlc-qa-task/
│   └── SKILL.md          ← QA per task sau mỗi PR merge
└── sdlc-pre-release/
    └── SKILL.md          ← Release notes, config guide, notify, rollback plan
```

### Orchestration Flow

```
/sdlc [idea]
  ├── 1. Kickoff          → gsd:new-milestone
  ├── 2. Feasibility      → embedded trong new-milestone questioning
  ├── 3. Discovery        → gsd:discuss-phase + gsd:research-phase
  ├── 4. Design           → /sdlc-design           ← custom extension
  ├── 5. Planning         → gsd:plan-phase
  ├── 6. Build loop       → gsd:execute-phase
  │     └── QA per task   → /sdlc-qa-task          ← custom extension
  ├── 7. QA/Staging       → gsd:verify-work
  ├── 8. Pre-release      → /sdlc-pre-release      ← custom extension
  └── 9. Ship             → gsd:ship
```

---

## Section 2: Gate Mechanism

### Hard Gates (blocking — must approve to continue)

```
After Discovery:
  [GATE] Approve requirements trước khi design? (y/n)

After Design:
  [GATE] Approve design + API contract trước khi plan? (y/n)

After QA/Staging:
  [GATE] Approve để tạo pre-release docs? (y/n)
```

### Soft Gates (non-blocking — log only)

```
After each task QA:
  → Log pass/fail vào QA-LOG.md
  → Fail: flag lên nhưng không block engineer
```

### Feedback Loop Routing (post-release)

| Scenario | Action |
|----------|--------|
| Bug critical | Skip đến Build, tạo hotfix branch |
| Bug minor | Add vào todo backlog |
| Feature mới | Full cycle từ đầu `/sdlc [new-idea]` |

---

## Section 3: Extension Skills

### `/sdlc-design` — Sau Discovery, trước Planning

**Flow:**

```
Step 1: Design System (FIRST — foundation for all screens)
  → Trigger: frontend-design skill
  → Output: DESIGN-SYSTEM.md
    - Color palette, typography, spacing scale
    - Component library:
        Button, Input, Select, Textarea,
        Card, Modal, Table, Badge, Avatar,
        Sidebar, Topbar, Layout grid
    - Interaction patterns (hover, focus, error states)

Step 2: Wireframes (uses design system as base)
  → Each screen references components từ DESIGN-SYSTEM.md
  → Output: WIREFRAMES.md (text description per screen)

Step 3: API Contract (Architect agent — runs parallel with Step 2)
  → Output: API-CONTRACT.md
    - Endpoints (method, path, auth required)
    - Request/response schema
    - Error codes
```

**Why design system first:** Đảm bảo tất cả màn hình dùng cùng components → nhất quán tuyệt đối, không cần nhớ lại color hay font size khi làm từng screen.

---

### `/sdlc-qa-task` — Sau mỗi PR merge trong execute-phase

**Input:** Task description + acceptance criteria từ PLAN.md + PR diff

**Flow:**
```
QA agent reads:
  - Task acceptance criteria từ PLAN.md
  - PR diff (what actually changed)

Verify:
  - Criteria met? (y/n per criterion)
  - Edge cases covered?
  - No regression on adjacent features?

Output: Append to QA-LOG.md
  [PASS/FAIL] Task: {task-name} | Date: {date} | Notes: {notes}
```

---

### `/sdlc-pre-release` — Sau verify-work, trước ship

**Input:** SUMMARY.md + VERIFICATION.md + PLAN.md của phase

**Output:**

| File | Content |
|------|---------|
| `RELEASE-NOTES.md` | Thay đổi so với version trước — features, fixes, breaking changes |
| `CONFIG-GUIDE.md` | Env vars cần set, DB migrations cần chạy, infra changes |
| `NOTIFY-LIST.md` | Teams cần thông báo + message template sẵn |
| `ROLLBACK-PLAN.md` | Steps để revert nếu deploy fail |

---

## Section 4: Artifact Structure

Tất cả artifacts lưu trong GSD's `.planning/` — không tạo thêm folder riêng.

```
.planning/
├── phases/
│   └── 01-admin-ui/
│       ├── 01-01-PLAN.md           ← gsd:plan-phase
│       ├── 01-01-SUMMARY.md        ← gsd:execute-phase
│       ├── DESIGN-SYSTEM.md        ← sdlc-design (frontend-design skill)
│       ├── WIREFRAMES.md           ← sdlc-design
│       ├── API-CONTRACT.md         ← sdlc-design (architect agent)
│       ├── QA-LOG.md               ← sdlc-qa-task (append per task)
│       ├── RELEASE-NOTES.md        ← sdlc-pre-release
│       ├── CONFIG-GUIDE.md         ← sdlc-pre-release
│       ├── NOTIFY-LIST.md          ← sdlc-pre-release
│       └── ROLLBACK-PLAN.md        ← sdlc-pre-release
├── STATE.md                        ← gsd (gate status, progress)
└── ROADMAP.md                      ← gsd
```

Mỗi phase là 1 folder riêng → artifacts không bị mix, dễ archive sau mỗi milestone.

---

## First Application

**Milestone:** Admin UI cho M1 (spring-saas-support-ai)
**Command:** `/sdlc "Admin UI for M1 — React + Tailwind + shadcn/ui, 4 screens: Auth, Dashboard, Members, Settings"`
**Timeline:** ~2-3 ngày build sau khi workflow setup xong
