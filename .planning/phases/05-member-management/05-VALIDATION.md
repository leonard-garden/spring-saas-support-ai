---
phase: 5
slug: member-management
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-15
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 2.1.9 + @testing-library/react 16.3.2 |
| **Config file** | `frontend/vitest.config.ts` |
| **Quick run command** | `cd frontend && npm test -- --run` |
| **Full suite command** | `cd frontend && npm test -- --run --coverage` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd frontend && npm test -- --run`
- **After every plan wave:** Run `cd frontend && npm test -- --run --coverage`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 1 | MBR-01, MBR-06 | unit | `cd frontend && npm test -- --run src/pages/MembersPage.test.tsx` | ❌ W0 | ⬜ pending |
| 05-01-02 | 01 | 1 | MBR-01 | unit | `cd frontend && npm test -- --run src/components/members/MembersTable.test.tsx` | ❌ W0 | ⬜ pending |
| 05-02-01 | 02 | 2 | MBR-02 | unit | `cd frontend && npm test -- --run src/components/members/InviteModal.test.tsx` | ❌ W0 | ⬜ pending |
| 05-02-02 | 02 | 2 | MBR-04 | unit | `cd frontend && npm test -- --run src/components/members/MembersTable.test.tsx` | ❌ W0 | ⬜ pending |
| 05-03-01 | 03 | 3 | MBR-03, MBR-05 | unit | `cd frontend && npm test -- --run src/components/members/RemoveConfirmDialog.test.tsx` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `frontend/src/pages/MembersPage.test.tsx` — covers MBR-01, MBR-02, MBR-03, MBR-05, MBR-06
- [ ] `frontend/src/components/members/InviteModal.test.tsx` — covers MBR-02 form submit
- [ ] `frontend/src/components/members/RemoveConfirmDialog.test.tsx` — covers MBR-03 confirm
- [ ] `frontend/src/components/members/MembersTable.test.tsx` — covers MBR-01, MBR-04

*Existing infrastructure: Vitest + RTL already configured and passing. shadcn components must be installed before test files are created.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Pagination renders correct page after clicking next/prev | MBR-01 | Requires multiple API call simulation with state transitions | Load page, mock 15 members, click next, verify page 2 renders |
| Role dropdown updates immediately after API success | MBR-04 | Optimistic UI behavior tricky to assert in unit tests | Click role dropdown, select new role, verify row updates without page reload |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
