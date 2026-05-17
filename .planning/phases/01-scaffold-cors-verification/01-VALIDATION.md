---
phase: 1
slug: scaffold-cors-verification
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-13
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | None — Phase 1 is infrastructure scaffolding; validation is manual |
| **Config file** | none — Wave 0 installs tooling only |
| **Quick run command** | `npm run build` (TypeScript check + Vite build) |
| **Full suite command** | `npm run build && npm run dev` (manual browser check) |
| **Estimated runtime** | ~30 seconds (build only) |

---

## Sampling Rate

- **After every task commit:** Run `npm run build` — must exit 0 with no TypeScript errors
- **After every plan wave:** Run full manual gate (see below)
- **Before `/gsd:verify-work`:** All manual gate checks must pass
- **Max feedback latency:** 30 seconds (build time)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | infra | manual | `npm run build` | ❌ W0 | ⬜ pending |
| 01-01-02 | 01 | 1 | infra | manual | `npm run build` | ❌ W0 | ⬜ pending |
| 01-02-01 | 02 | 2 | infra | manual | Browser: `/cors-test` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `frontend/` directory created with `npm create vite@latest`
- [ ] `package.json` — all stack dependencies installed
- [ ] `npm run build` — must exit 0 before any browser testing

*Wave 0 is the scaffold itself — no pre-existing infrastructure to install into.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `npm run dev` starts | infra-gate | No test runner installed yet | Run `npm run dev`; Vite must print `Local: http://localhost:5173/` |
| `/cors-test` page loads | infra-gate | Browser UI not testable via CLI | Open http://localhost:5173/cors-test; card with email/password form must render |
| POST /api/v1/auth/login returns 200 | infra-gate | Requires live backend on :8081 | Enter valid credentials → green success alert with JSON response |
| CORS not blocked | infra-gate | Browser-enforced, not Node-testable | No "CORS BLOCKED" red alert; no network errors in DevTools console |

---

## Phase Gate Checklist (Manual)

Before marking Phase 1 complete, ALL must pass:

- [ ] `npm run build` exits 0 — no TypeScript errors
- [ ] `npm run dev` starts — Vite prints `Local: http://localhost:5173/`
- [ ] `/cors-test` route renders the Card with form inputs
- [ ] Submitting valid credentials → green success alert + JSON body displayed
- [ ] DevTools Network tab shows no CORS errors on the `/api/v1/auth/login` request

---

## Validation Sign-Off

- [ ] All tasks have manual verify instructions (see Manual-Only table above)
- [ ] Wave 0 covers: project creation + npm install + build verification
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s (build only)
- [ ] `nyquist_compliant: true` set in frontmatter once all manual gates pass

**Approval:** pending
