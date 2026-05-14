# SDLC Workflow Skills Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build 4 Claude Code skills (`/sdlc`, `/sdlc-design`, `/sdlc-qa-task`, `/sdlc-pre-release`) that automate the full SDLC workflow from idea to production deploy.

**Architecture:** One orchestrator skill (`/sdlc`) wraps GSD commands and calls 3 custom extension skills at the right points. Each skill is a standalone SKILL.md file that instructs Claude to perform a specific phase of the workflow.

**Tech Stack:** Claude Code skills (SKILL.md), GSD framework (`~/.claude/get-shit-done/`), oh-my-claudecode agents

---

## File Map

| File | Role |
|------|------|
| `~/.claude/skills/sdlc-design/SKILL.md` | Design phase: design system + wireframes + API contract |
| `~/.claude/skills/sdlc-qa-task/SKILL.md` | QA per task after PR merge |
| `~/.claude/skills/sdlc-pre-release/SKILL.md` | Pre-release documentation generator |
| `~/.claude/skills/sdlc/SKILL.md` | Orchestrator — calls GSD + 3 extension skills in sequence |

Build order: extensions first (1→3), orchestrator last (4). Each skill is independently usable.

---

## Task 1: `/sdlc-design` skill

**Files:**
- Create: `~/.claude/skills/sdlc-design/SKILL.md`

- [ ] **Step 1: Create the skill directory**

```bash
mkdir -p ~/.claude/skills/sdlc-design
```

- [ ] **Step 2: Write the SKILL.md**

```markdown
---
name: sdlc-design
version: 1.0.0
description: |
  Design phase for SDLC workflow. Produces design system, wireframes, and API
  contract for a feature/milestone. Invoke after Discovery gate is approved,
  before plan-phase. Triggered by /sdlc orchestrator or manually.
---

<purpose>
Run the Design phase of the SDLC workflow for the current milestone/feature.
Produce three artifacts: DESIGN-SYSTEM.md, WIREFRAMES.md, API-CONTRACT.md.
Save all files to the current GSD phase directory (.planning/phases/<phase>/).
</purpose>

<process>

## Step 1: Read context

Read these files before doing anything:
- `.planning/phases/<current-phase>/` — find the latest PLAN.md or CONTEXT.md
- Requirements from `discuss-phase` output (CONTEXT.md in phase dir)
- `docs/superpowers/specs/2026-05-13-sdlc-workflow-framework-design.md` for role definitions

## Step 2: Design System (FIRST — do before wireframes)

Invoke the `frontend-design` skill to produce the design system.

The design system MUST include:
- **Colors:** primary, secondary, neutral, success, warning, error (hex values)
- **Typography:** font family, size scale (xs/sm/base/lg/xl/2xl), weight scale
- **Spacing:** spacing scale (4px base unit)
- **Components:**
  - Button (primary, secondary, ghost, destructive variants + sm/md/lg sizes)
  - Input (default, error, disabled states)
  - Select, Textarea
  - Card (with header, body, footer slots)
  - Modal (with overlay)
  - Table (with sortable headers, pagination)
  - Badge (status variants)
  - Avatar (with fallback initials)
  - Sidebar (collapsed/expanded states)
  - Topbar/Header
- **Layout:** sidebar + topbar shell, main content area, responsive breakpoints

Save to: `.planning/phases/<current-phase>/DESIGN-SYSTEM.md`

## Step 3: Wireframes (uses design system as base)

For each screen in the milestone, describe the layout using components from DESIGN-SYSTEM.md.

Format per screen:
```
## Screen: [Name]
Route: /path
Purpose: [one sentence]

Layout:
- Topbar: [components]
- Sidebar: [active item]
- Main content:
  [ASCII or structured description of layout]
  
Components used: [list from DESIGN-SYSTEM.md]
States: [loading, empty, error, success]
```

Save to: `.planning/phases/<current-phase>/WIREFRAMES.md`

## Step 4: API Contract (run in parallel with Step 3)

Spawn an `oh-my-claudecode:architect` agent to produce the API contract.

The agent must document every endpoint needed for this milestone:
```
## [Feature Name]

### GET /api/v1/[resource]
Auth: Bearer JWT required
Request: -
Response 200:
{
  "success": true,
  "data": { ... }
}
Response 401: { "success": false, "error": "Unauthorized" }

### POST /api/v1/[resource]
Auth: Bearer JWT required  
Request body:
{
  "field": "type" // description
}
Response 201: { "success": true, "data": { ... } }
Response 400: { "success": false, "error": "validation message" }
```

Save to: `.planning/phases/<current-phase>/API-CONTRACT.md`

## Step 5: Report completion

List all 3 files created with their paths.
Ask: "Design artifacts ready. Review DESIGN-SYSTEM.md, WIREFRAMES.md, API-CONTRACT.md in .planning/phases/<phase>/. Approve to proceed to plan-phase? (y/n)"

Wait for approval before exiting.

</process>
```

- [ ] **Step 3: Verify file created**

```bash
cat ~/.claude/skills/sdlc-design/SKILL.md | head -5
```
Expected: YAML frontmatter with `name: sdlc-design`

- [ ] **Step 4: Test with a dry-run prompt**

Run: Open a new Claude Code session and type:
```
/sdlc-design
```
Expected behavior: Claude reads context, asks about current phase if ambiguous, then proceeds to produce DESIGN-SYSTEM.md.

- [ ] **Step 5: Commit**

```bash
cd ~ && git -C ~/.claude add skills/sdlc-design/SKILL.md 2>/dev/null || echo "not a git repo — file saved"
```

---

## Task 2: `/sdlc-qa-task` skill

**Files:**
- Create: `~/.claude/skills/sdlc-qa-task/SKILL.md`

- [ ] **Step 1: Create the skill directory**

```bash
mkdir -p ~/.claude/skills/sdlc-qa-task
```

- [ ] **Step 2: Write the SKILL.md**

```markdown
---
name: sdlc-qa-task
version: 1.0.0
description: |
  QA verification for a single task after its PR has been merged. Reads
  acceptance criteria from the GSD PLAN.md, checks what was implemented,
  logs PASS/FAIL to QA-LOG.md. Invoke manually after each PR merge during
  execute-phase. Usage: /sdlc-qa-task "task name or description"
---

<purpose>
Verify a specific task from the current GSD phase has been correctly implemented.
Append result to QA-LOG.md in the phase directory.
</purpose>

<process>

## Step 1: Identify the task

If the user provided a task name/description, use it.
If not, ask: "Which task should I QA? Provide the task name or description."

## Step 2: Read acceptance criteria

Read `.planning/phases/<current-phase>/01-01-PLAN.md` (or latest PLAN.md).
Find the task. Extract:
- Task description
- Acceptance criteria / definition of done
- Files that should be modified

## Step 3: Verify implementation

Check the codebase to verify each acceptance criterion:
- Read the relevant files listed in the task
- For each criterion: PASS or FAIL with specific evidence

Format:
```
Criterion: [criterion text]
Status: PASS / FAIL
Evidence: [specific line/file/behavior that confirms or denies]
```

## Step 4: Check for regressions

Identify files adjacent to the task's changes.
Check that their core behaviors are not broken.
Flag any concerns.

## Step 5: Log result

Append to `.planning/phases/<current-phase>/QA-LOG.md`:

```
## [PASS/FAIL] Task: {task-name}
Date: {YYYY-MM-DD}
Reviewer: QA Agent

### Criteria Results
| Criterion | Status | Evidence |
|-----------|--------|----------|
| ... | PASS/FAIL | ... |

### Regression Check
[any concerns or CLEAR]

### Notes
[optional notes]

---
```

If QA-LOG.md does not exist, create it with header:
```
# QA Log — {phase-name}
Generated by /sdlc-qa-task
```

## Step 6: Report

Tell the user: overall PASS or FAIL, number of criteria checked, any regression concerns.
If FAIL: list which criteria failed and what needs fixing.

</process>
```

- [ ] **Step 3: Verify file created**

```bash
cat ~/.claude/skills/sdlc-qa-task/SKILL.md | head -5
```
Expected: YAML frontmatter with `name: sdlc-qa-task`

- [ ] **Step 4: Test with dry-run**

Run: `/sdlc-qa-task "login form validation"`
Expected: Claude reads PLAN.md, finds the task, checks implementation, writes to QA-LOG.md.

- [ ] **Step 5: Commit**

```bash
echo "sdlc-qa-task skill saved to ~/.claude/skills/sdlc-qa-task/SKILL.md"
```

---

## Task 3: `/sdlc-pre-release` skill

**Files:**
- Create: `~/.claude/skills/sdlc-pre-release/SKILL.md`

- [ ] **Step 1: Create the skill directory**

```bash
mkdir -p ~/.claude/skills/sdlc-pre-release
```

- [ ] **Step 2: Write the SKILL.md**

```markdown
---
name: sdlc-pre-release
version: 1.0.0
description: |
  Pre-release documentation generator. Reads GSD phase artifacts and produces
  4 release documents: RELEASE-NOTES.md, CONFIG-GUIDE.md, NOTIFY-LIST.md,
  ROLLBACK-PLAN.md. Invoke after verify-work is approved, before gsd:ship.
---

<purpose>
Generate all pre-release documentation for a completed GSD phase.
Save 4 files to the current phase directory.
</purpose>

<process>

## Step 1: Read phase artifacts

Read all of:
- `.planning/phases/<current-phase>/01-01-SUMMARY.md` (what was built)
- `.planning/phases/<current-phase>/VERIFICATION.md` (what was verified)
- `.planning/phases/<current-phase>/01-01-PLAN.md` (original scope)
- `.planning/ROADMAP.md` (version/milestone context)
- `render.yaml` or deployment config (for infra changes)
- `src/main/resources/application*.yml` (for config changes)

## Step 2: Generate RELEASE-NOTES.md

```markdown
# Release Notes — v{version} — {milestone-name}

**Release date:** {YYYY-MM-DD}
**Type:** Feature / Bug fix / Hotfix

## What's New
- [feature]: [description]

## Bug Fixes
- [fix]: [description]

## Breaking Changes
- [NONE] or list changes that require action from consumers

## Known Issues
- [NONE] or list
```

Save to: `.planning/phases/<current-phase>/RELEASE-NOTES.md`

## Step 3: Generate CONFIG-GUIDE.md

Check git diff against main for:
- New environment variables (application*.yml, render.yaml)
- Database migrations (src/main/resources/db/migration/)
- Infrastructure changes (render.yaml, docker-compose.yml)

```markdown
# Config Guide — v{version}

## Environment Variables
| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| NEW_VAR | Yes/No | What it does | value |

## Database Migrations
Run before deploying:
```bash
mvn flyway:migrate
```
Migrations included: V{n}__description.sql

## Infrastructure Changes
[NONE] or describe changes

## Deployment Steps
1. Set new env vars in Render dashboard
2. Run migrations
3. Deploy
```

Save to: `.planning/phases/<current-phase>/CONFIG-GUIDE.md`

## Step 4: Generate NOTIFY-LIST.md

```markdown
# Notify List — v{version}

## Teams to Notify
| Team | Channel | When | Message |
|------|---------|------|---------|
| Engineering | #dev | Before deploy | "Deploying v{version} at {time}..." |
| Stakeholders | Email | After deploy | See template below |

## Announcement Template
Subject: v{version} deployed — {milestone-name}

{milestone-name} is now live.

What's new:
{bullet list from RELEASE-NOTES}

Any issues: [contact]
```

Save to: `.planning/phases/<current-phase>/NOTIFY-LIST.md`

## Step 5: Generate ROLLBACK-PLAN.md

```markdown
# Rollback Plan — v{version}

## Trigger Conditions
Roll back if any of these occur within 30 minutes of deploy:
- API error rate > 5%
- Health check endpoint returns non-200
- Critical user-reported bug

## Rollback Steps

### 1. Revert deployment (Render)
Go to Render dashboard → service → Deploys → select previous deploy → Redeploy

### 2. Revert database (if migrations ran)
```sql
-- Only if migration was destructive
-- List specific rollback SQL here or state: no rollback needed
```

### 3. Notify teams
Send to #dev: "Rolling back v{version} due to [reason]. ETA: 5 minutes."

## Post-Rollback
- Open incident report
- Identify root cause
- Fix → re-run full QA → redeploy
```

Save to: `.planning/phases/<current-phase>/ROLLBACK-PLAN.md`

## Step 6: Report

List all 4 files created.
Ask: "Pre-release docs ready. Review and approve to proceed to gsd:ship? (y/n)"
Wait for approval.

</process>
```

- [ ] **Step 3: Verify file created**

```bash
cat ~/.claude/skills/sdlc-pre-release/SKILL.md | head -5
```
Expected: YAML frontmatter with `name: sdlc-pre-release`

- [ ] **Step 4: Test with dry-run**

Run: `/sdlc-pre-release`
Expected: Claude reads SUMMARY.md + VERIFICATION.md, generates 4 release docs.

- [ ] **Step 5: Commit**

```bash
echo "sdlc-pre-release skill saved to ~/.claude/skills/sdlc-pre-release/SKILL.md"
```

---

## Task 4: `/sdlc` orchestrator skill

**Files:**
- Create: `~/.claude/skills/sdlc/SKILL.md`

- [ ] **Step 1: Create the skill directory**

```bash
mkdir -p ~/.claude/skills/sdlc
```

- [ ] **Step 2: Write the SKILL.md**

```markdown
---
name: sdlc
version: 1.0.0
description: |
  Full SDLC workflow orchestrator for SaaS/SMB projects. Runs a complete
  milestone from idea to production deploy. Wraps GSD commands with 3 custom
  extensions: sdlc-design, sdlc-qa-task, sdlc-pre-release. 
  Usage: /sdlc "idea or milestone description"
  Trigger: when user says "run workflow", "start new milestone", "sdlc <idea>"
---

<purpose>
Orchestrate the full SDLC workflow for a single idea/milestone.
Guide the user through all phases with gates. Never skip a gate without explicit approval.
</purpose>

<phases>

## Phase 1: Kickoff + Feasibility (gsd:new-milestone)

Run: `gsd:new-milestone` with the provided idea description.

This covers:
- Kickoff (scope, owner, timeline)
- Feasibility (embedded in questioning — is this worth building?)
- Requirements definition

[GATE 1] After new-milestone completes:
"Kickoff + Feasibility done. Milestone created in .planning/.
Approve to proceed to Discovery? (y/n)"
→ If n: stop. User will revisit scope.
→ If y: continue.

## Phase 2: Discovery (gsd:discuss-phase + gsd:research-phase)

Run: `gsd:discuss-phase <phase-number>` for the first phase of the new milestone.
Ask user: "Run domain research as well? This spawns research agents. (y/n)"
→ If y: also run `gsd:research-phase <phase-number>`

[GATE 2] After Discovery:
"Discovery done. Requirements and vision captured.
Approve to proceed to Design? (y/n)"
→ If n: stop.
→ If y: continue.

## Phase 3: Design (/sdlc-design)

Invoke skill: `sdlc-design`

This produces:
- DESIGN-SYSTEM.md (via frontend-design skill)
- WIREFRAMES.md
- API-CONTRACT.md

[GATE 3] After sdlc-design reports completion:
"Design artifacts ready. Review .planning/phases/<phase>/ for design files.
Approve design to proceed to Planning? (y/n)"
→ If n: ask what to revise, re-run sdlc-design.
→ If y: continue.

## Phase 4: Planning (gsd:plan-phase)

Run: `gsd:plan-phase <phase-number>`

This produces PLAN.md with Epic → Story → Task breakdown.

[GATE 4] After plan-phase:
"Planning done. PLAN.md created.
Approve to start Build? (y/n)"
→ If n: adjust scope or re-plan.
→ If y: continue.

## Phase 5: Build (gsd:execute-phase + /sdlc-qa-task per PR)

Run: `gsd:execute-phase <phase-number>`

IMPORTANT — instruct the user during Build:
"After each PR is merged, run /sdlc-qa-task '<task-name>' to verify that task.
I will track the overall build but QA per task is manually triggered."

Monitor execute-phase completion.

[GATE 5] After execute-phase completes AND user confirms all tasks QA-passed:
"Build complete. Check QA-LOG.md for task results.
Confirm all tasks passed QA before proceeding? (y/n)"
→ If n: identify which tasks failed, loop back to Build.
→ If y: continue.

## Phase 6: QA / Staging (gsd:verify-work)

Run: `gsd:verify-work <phase-number>`

This runs regression + E2E verification.

[GATE 6] After verify-work:
"QA/Staging complete.
Approve to generate pre-release docs? (y/n)"
→ If n: fix issues, re-run verify-work.
→ If y: continue.

## Phase 7: Pre-release (/sdlc-pre-release)

Invoke skill: `sdlc-pre-release`

This produces:
- RELEASE-NOTES.md
- CONFIG-GUIDE.md
- NOTIFY-LIST.md
- ROLLBACK-PLAN.md

[GATE 7] After sdlc-pre-release reports completion:
"Pre-release docs ready.
Review CONFIG-GUIDE.md — apply all config changes to production environment.
Confirm config applied and approve to deploy? (y/n)"
→ If n: wait for user to apply config.
→ If y: continue.

## Phase 8: Ship (gsd:ship)

Run: `gsd:ship <phase-number>`

This creates the PR and pushes to remote.

After ship:
"Shipped! PR created. After merging and deploying:
- Run smoke tests on production
- Send notifications per NOTIFY-LIST.md
- Monitor for 30 minutes

If issues arise:
- Critical bug → /sdlc-qa-task + hotfix branch (fast-track, skip to Build)
- Minor bug → /gsd:add-todo for next milestone
- New feature idea → /sdlc '<new idea>' (full cycle)"

</phases>

<feedback-loop>
Post-release routing:
- Critical bug: "Run /sdlc-qa-task to identify the bug, create hotfix branch, skip to Phase 5"
- Minor bug: "Run /gsd:add-todo to capture for next milestone"
- New feature: "Run /sdlc '<feature description>' to start a new cycle"
</feedback-loop>
```

- [ ] **Step 3: Verify file created**

```bash
cat ~/.claude/skills/sdlc/SKILL.md | head -5
```
Expected: YAML frontmatter with `name: sdlc`

- [ ] **Step 4: Full integration test**

Run: `/sdlc "Admin UI for M1 — React + Tailwind + shadcn/ui, 4 screens: Auth, Dashboard, Members, Settings"`

Expected flow:
1. Claude invokes `gsd:new-milestone` with the description
2. Shows GATE 1 prompt
3. On approval → invokes `gsd:discuss-phase`
4. Shows GATE 2 prompt
5. On approval → invokes `sdlc-design` skill
... and so on

- [ ] **Step 5: Commit all 4 skills**

```bash
git -C ~/.claude add skills/sdlc/ skills/sdlc-design/ skills/sdlc-qa-task/ skills/sdlc-pre-release/ 2>/dev/null
git -C ~/.claude commit -m "feat: add SDLC workflow orchestrator skills

Add /sdlc orchestrator + 3 extensions (sdlc-design, sdlc-qa-task,
sdlc-pre-release) implementing the SDLC framework from
spring-saas-support-ai/docs/superpowers/specs/." 2>/dev/null || echo "Saved to ~/.claude/skills/ (not a git repo)"
```

---

## Self-Review

**Spec coverage:**
- ✅ Orchestrator skill → Task 4
- ✅ Design phase + frontend-design trigger → Task 1
- ✅ QA per task → Task 2
- ✅ Pre-release docs (4 files) → Task 3
- ✅ Hard gates at critical points → Task 4 (GATE 1-7)
- ✅ Feedback loop routing → Task 4 (feedback-loop section)
- ✅ Artifacts in .planning/phases/<phase>/ → Tasks 1, 2, 3

**Placeholder scan:** No TBD/TODO in any skill content.

**Type consistency:** Phase numbers referenced as `<phase-number>` placeholder consistently across orchestrator — executor must substitute with actual number when running.
