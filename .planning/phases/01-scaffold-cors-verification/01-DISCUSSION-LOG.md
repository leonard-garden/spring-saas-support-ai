# Phase 1: Scaffold + CORS Verification - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-13
**Phase:** 01-scaffold-cors-verification
**Areas discussed:** Project location + naming, Package manager, CORS verification approach

---

## Project location + naming

| Option | Description | Selected |
|--------|-------------|----------|
| admin-ui/ at repo root | Sibling to src/ and pom.xml — matches research recommendation | |
| frontend/ at repo root | Same location, more generic name | ✓ |
| src/main/webapp/ | Spring Boot convention — couples deploys | |

**User's choice:** `frontend/` at repo root
**Notes:** Preferred generic name over `admin-ui/` — may plan to add more frontend apps later

---

## Package manager

| Option | Description | Selected |
|--------|-------------|----------|
| npm | No extra setup, works everywhere | ✓ |
| pnpm | Faster, disk-efficient, better monorepo support | |
| bun | Fastest, less mature tooling | |

**User's choice:** npm
**Notes:** No extra tooling overhead needed for this project

---

## CORS verification approach

| Option | Description | Selected |
|--------|-------------|----------|
| Temporary test button in app | Button on Vite default page, removed in Phase 2 | |
| Browser DevTools manual probe | fetch() pasted in console, no code | |
| Dedicated test page (/cors-test) | Proper route with form, repeatable, stays in git | ✓ |

**User's choice:** Dedicated `/cors-test` page
**Notes:** Wanted a proper repeatable verification artifact in git, not a one-off console test

---

## Claude's Discretion

- shadcn/ui init configuration (theme, CSS variables)
- `.env.development` setup details
- Vite config (port, proxy)

## Deferred Ideas

None
