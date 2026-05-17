---
phase: 4
slug: app-shell-dashboard-home
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-14
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 2.1.9 + React Testing Library 16 |
| **Config file** | `frontend/vitest.config.ts` |
| **Quick run command** | `cd frontend && npm test -- --run` |
| **Full suite command** | `cd frontend && npm test -- --run --coverage` |
| **Estimated runtime** | ~10 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd frontend && npm test -- --run`
- **After every plan wave:** Run `cd frontend && npm test -- --run --coverage`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~10 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 04-01-01 | 01 | 1 | SHELL-01, SHELL-02, SHELL-03 | unit | `cd frontend && npm test -- --run src/components/layout/Sidebar.test.tsx` | ❌ W0 | ⬜ pending |
| 04-01-02 | 01 | 1 | SHELL-01 | unit | `cd frontend && npm test -- --run` | ❌ W0 | ⬜ pending |
| 04-02-01 | 02 | 2 | DASH-01, DASH-02, DASH-03 | unit | `cd frontend && npm test -- --run src/pages/DashboardPage.test.tsx` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/components/layout/Sidebar.test.tsx` — stubs for SHELL-01, SHELL-02, SHELL-03
- [ ] `src/pages/DashboardPage.test.tsx` — stubs for DASH-01, DASH-02, DASH-03

*Existing infrastructure: Vitest + RTL already configured and passing. No framework install needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Active nav link visually highlighted when navigating | SHELL-03 | Browser rendering required to confirm highlight color and transition | Navigate to /dashboard, /members, /kb and verify the active link shows `bg-primary text-primary-foreground` styling |
| Sidebar persists without remount between page navigations | SHELL-01 | React remount behavior not detectable in unit tests | Open devtools, navigate between pages, confirm sidebar DOM node does not re-render (no flash) |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
