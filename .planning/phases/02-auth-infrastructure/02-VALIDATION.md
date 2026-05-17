---
phase: 2
slug: auth-infrastructure
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-14
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | vitest + @testing-library/react |
| **Config file** | `frontend/vite.config.ts` (vitest config inline) |
| **Quick run command** | `cd frontend && npm run test -- --run` |
| **Full suite command** | `cd frontend && npm run test -- --run --coverage` |
| **Estimated runtime** | ~10 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd frontend && npm run test -- --run`
- **After every plan wave:** Run `cd frontend && npm run test -- --run --coverage`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 2-01-01 | 01 | 1 | AUTH-03 | unit | `cd frontend && npm run test -- --run src/lib/api.test.ts` | ❌ W0 | ⬜ pending |
| 2-01-02 | 01 | 1 | AUTH-05 | unit | `cd frontend && npm run test -- --run src/lib/api.test.ts` | ❌ W0 | ⬜ pending |
| 2-02-01 | 02 | 2 | AUTH-04 | unit | `cd frontend && npm run test -- --run src/store/auth.test.ts` | ❌ W0 | ⬜ pending |
| 2-02-02 | 02 | 2 | AUTH-03 | integration | `cd frontend && npm run test -- --run src/components/ProtectedRoute.test.tsx` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `frontend/src/lib/api.test.ts` — stubs for axios interceptor + refresh lock (AUTH-03, AUTH-05)
- [ ] `frontend/src/store/auth.test.ts` — stubs for Zustand tri-state store (AUTH-04)
- [ ] `frontend/src/components/ProtectedRoute.test.tsx` — stubs for route guard behavior (AUTH-03)
- [ ] `frontend/src/test/setup.ts` — shared test setup (jsdom, @testing-library/jest-dom matchers)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Refresh lock prevents concurrent 401s | AUTH-05 | Requires network timing to reproduce race condition | Open devtools, throttle network, trigger 3 simultaneous API calls while token is expired — verify only one refresh request fires |
| Session restored after hard refresh | AUTH-04 | Requires real browser storage + network | Log in, hard refresh (`Ctrl+Shift+R`), verify you remain on dashboard without re-login prompt |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
