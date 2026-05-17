---
name: forge
description: Use when given a GitHub issue number to implement — branching, coding, verifying with architecture checks, committing with trailers, and opening a PR.
---

# Forge

## Overview

Issue → PR workflow cho project này. Prevents skipping architecture checks, verification evidence, commit trailers.

**Invoke:** `/forge #N`

---

## Step 1 — Read & Judge

```bash
gh issue view #N
```
Read: `CLAUDE.md`, `.claude/memory/architecture.md`, `.claude/memory/multi-tenancy.md`

**Judge mode, then announce:**

| Signal | Mode |
|--------|------|
| Config-only, 1–2 files, no business logic | QUICK |
| New entity / service / repository layer | FULL |
| Security, auth, multi-tenancy involved | FULL |
| Design tradeoffs cần document | FULL |

`"Choosing FULL/QUICK because [reason]. Proceed?"`

---

## Step 2 — Branch

Read `.planning/sdlc-state.md` to get `phase_branch`. If present, branch off it:

```bash
git checkout <phase_branch>
git pull origin <phase_branch>
git checkout -b feature/issue-{N}-{slug}
```

If `phase_branch` is not set, branch off `develop` as default.

---

## Step 3 — Design `[FULL only — CHECKPOINT]`

Dispatch `oh-my-claudecode:planner` (model: opus).
Provide: issue text + relevant code context.

Present to user:
- Files to create / modify
- Design decisions + rejected alternatives
- Scope-risk: narrow / moderate / broad

**Stop. Wait for confirm before coding.**

---

## Step 4 — Implement

**FULL:** 3-agent pipeline per task:

```
Executor (fresh, oh-my-claudecode:executor)
  └─ implement + write tests + self-review
  └─ status: DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED

Spec Compliance Review (fresh, oh-my-claudecode:code-reviewer, opus)
  └─ Does code match issue spec? Anything extra?
  └─ FAIL → executor fixes → re-review [must pass before next]

Code Quality Review (fresh, oh-my-claudecode:code-reviewer, opus)
  └─ Java 21 idioms, architecture, naming
  └─ FAIL → executor fixes → re-review
```

Load `rules/coding-style.md` as executor context.
Load `references/slice-anatomy.md` when creating new package.
Load `rules/exceptions.md` when adding new exceptions.
Load `references/testing-patterns.md` when writing tests.
Load `references/flyway-guide.md` when task requires new DB columns/tables.
Load `references/security-patterns.md` when task touches SecurityConfig or endpoint auth.

**QUICK:** Implement inline. Same verification below.

---

## Step 5 — Verification Loop

**Iron Law: Run command → read output → then claim. No "should pass".**

Loop until all 6 pass (max 3 iterations, then escalate with evidence):

**Runtime** (run fresh, read actual output):
```bash
mvn test        # must exit 0, 0 failures
mvn verify      # includes *IT classes
```

**Coverage** (if jacoco configured):
```bash
mvn jacoco:report   # service layer ≥ 60%
```

**Static** — load `rules/architecture.md`, run each check with Grep/AST/LSP:
- Import compliance
- Architecture invariants (7 rules)

Fix failures: re-dispatch fresh executor sub-agent. Never inline fix.

---

## Step 6 — Commit

Load `assets/commit-template.txt`. Fill all trailers. Do not leave any blank.

---

## Step 7 — PR

Load `assets/pr-template.md`. Fill Summary + Test plan with actual numbers from Step 5.

Target branch = `phase_branch` from `.planning/sdlc-state.md`. If not set, target `develop`.

```bash
gh pr create --title "..." --base <phase_branch> --body "$(cat <<'EOF'
[filled template]
EOF
)"
```

---

## Red Flags — STOP

- Committing before running `mvn test` this turn
- Writing "should pass" instead of showing output
- Skipping spec compliance → jumping to code quality
- Inline fix instead of fresh executor sub-agent
- Skipping static checks because "task is small"
