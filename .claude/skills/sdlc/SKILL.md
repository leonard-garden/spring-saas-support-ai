---
name: sdlc
version: 1.0.0
description: |
  Full SDLC workflow orchestrator for SaaS/SMB projects. Runs a complete
  milestone from idea to production deploy. Wraps GSD commands with 3 custom
  extension skills: sdlc-design, sdlc-qa-task, sdlc-pre-release.
  Usage: /sdlc "idea or milestone description"
  Triggers: "run workflow", "start new milestone", "sdlc <idea>", "full cycle"
---

<purpose>
Orchestrate the full SDLC workflow for a single idea/milestone from idea to deploy.
Guide the user through all phases with hard gates. Never skip a gate without explicit approval.
State machine: each phase produces a defined output before the next phase begins.
</purpose>

<state-tracking>
At the start, create a state record in `.planning/sdlc-state.md` (or update if exists):

```markdown
# SDLC Session — {idea-slug}
Started: {YYYY-MM-DD HH:MM}
Idea: {idea description}
Current phase: 1 — Kickoff
Gate statuses:
- Gate 1 (→Discovery): pending
- Gate 2 (→Design): pending
- Gate 3 (→Planning): pending
- Gate 4 (→Build): pending
- Gate 5 (→QA/Staging): pending
- Gate 6 (→Ship): pending
```

Update the "Current phase" and gate status after each phase completes.
If the user resumes mid-workflow, read `.planning/sdlc-state.md` and continue from the last incomplete phase.
</state-tracking>

<phases>

## Phase 1: Kickoff + Feasibility

**Command:** Run `gsd:new-milestone` with the idea description.

This phase covers:
- Scope definition (what is this? why now?)
- Feasibility check (technical complexity, value vs effort)
- Requirements definition (what success looks like)
- ROADMAP.md updated with new milestone phases

After gsd:new-milestone completes:
Record the milestone phase number(s) created in sdlc-state.md.
To find the phase number: read `.planning/ROADMAP.md` and identify the newly added phase — look for the highest-numbered phase entry. Extract the numeric prefix (e.g., from `05-admin-ui` extract `05`). Store as `<resolved-phase-number>` in sdlc-state.md. Use `<resolved-phase-number>` for ALL subsequent GSD commands: gsd:discuss-phase, gsd:research-phase, gsd:plan-phase, gsd:execute-phase, gsd:verify-work, gsd:ship.

**[GATE 1]**
```
Phase 1 complete — Kickoff + Feasibility
Milestone created in .planning/ROADMAP.md

Approve to proceed to Discovery? (y/n)
(Do not continue until you receive a response.)
```
→ n: "Revisit the milestone scope. Re-run /sdlc when ready." Stop.
→ y: Update Gate 1 status to "approved". Continue to Phase 2.

## Phase 2: Discovery

**Commands (run in sequence):**
1. `gsd:discuss-phase <phase-number>` — capture requirements and vision
2. Ask user: "Run domain research as well? (adds depth but takes longer) (y/n)"
   - If y: also run `gsd:research-phase <phase-number>`
   - If n: skip research

**[GATE 2]**
```
Phase 2 complete — Discovery
Requirements and vision captured in .planning/phases/<phase>/CONTEXT.md

Approve to proceed to Design? (y/n)
(Do not continue until you receive a response.)
```
→ n: "Go back and refine requirements with gsd:discuss-phase <phase-number>." Stop.
→ y: Update Gate 2 status to "approved". Continue to Phase 3.

## Phase 3: Design

**Command:** Invoke skill `sdlc-design`

This produces:
- DESIGN-SYSTEM.md (via frontend-design skill)
- WIREFRAMES.md
- API-CONTRACT.md

Note: sdlc-design has its own internal gate. It will ask for design approval.
If sdlc-design is cancelled or reports failure, ask: 'sdlc-design did not complete. Retry? (y/n)' If y: re-invoke sdlc-design. If n: stop workflow and tell user to restart from Phase 3.
After sdlc-design completes, check `.planning/sdlc-state.md` for a line containing `design_gate: approved`. If found:
Update Gate 3 (Planning) status to "approved" and continue to Phase 4.

## Phase 4: Planning

**Command:** Run `gsd:plan-phase <phase-number>`

This produces PLAN.md with Epic → Story → Task breakdown.

**[GATE 3]**
```
Phase 4 complete — Planning
PLAN.md created in .planning/phases/<phase>/

Review the plan before build begins.
Approve to start Build? (y/n)
(Do not continue until you receive a response.)
```
→ n: "Adjust scope in PLAN.md then re-run gsd:plan-phase <phase-number>." Stop.
→ y: Update Gate 4 (Build) status to "approved". Continue to Phase 5.

## Phase 5: Build

**Command:** Run `gsd:execute-phase <phase-number>`

GSD will execute all plans in the phase.

**IMPORTANT — instruct the user during Build:**
```
Build started. During development:
- After each PR is merged, run: /sdlc-qa-task "<task-name>"
- QA results are logged to .planning/phases/<phase>/QA-LOG.md
- Continue until all tasks are complete and QA-passed

When all tasks are done, reply DONE to proceed.
(Do not continue until you receive DONE.)
```

Wait for user to respond with DONE, done, Done, YES, yes, or Y before proceeding. Do not proceed on any other response.

**[GATE 4]**
```
Phase 5 complete — Build
Check .planning/phases/<phase>/QA-LOG.md — all tasks should be PASS.

Confirm all tasks passed QA? (y/n)
(Do not continue until you receive a response.)
```
→ n: "Identify failing tasks in QA-LOG.md and fix them. Re-run /sdlc-qa-task for each." Stay in Build loop.
→ y: Update Gate 5 (QA/Staging) status to "approved". Continue to Phase 6.

## Phase 6: QA / Staging

**Command:** Run `gsd:verify-work <phase-number>`

This runs regression and E2E verification on the complete milestone.

**[GATE 5]**
```
Phase 6 complete — QA/Staging
Verification results in .planning/phases/<phase>/VERIFICATION.md

Approve to generate pre-release docs? (y/n)
(Do not continue until you receive a response.)
```
→ n: "Fix issues identified in VERIFICATION.md. Re-run gsd:verify-work <phase-number>." Loop back.
→ y: Update Gate 6 (Ship) status to "approved". Continue to Phase 7.

## Phase 7: Pre-release

**Command:** Invoke skill `sdlc-pre-release`

This produces:
- RELEASE-NOTES.md
- CONFIG-GUIDE.md
- NOTIFY-LIST.md
- ROLLBACK-PLAN.md

Note: sdlc-pre-release has its own internal gate. It will confirm config applied + team notified.
If sdlc-pre-release is cancelled or reports failure, ask: 'sdlc-pre-release did not complete. Retry? (y/n)' If y: re-invoke sdlc-pre-release. If n: stop workflow.
After sdlc-pre-release completes, check `.planning/sdlc-state.md` for a line containing `prerelease_gate: approved`. If found:
Gate 6 was already approved in Phase 6. Continue to Phase 8.

## Phase 8: Ship

**Command:** Run `gsd:ship <phase-number>`

This creates the PR and pushes to remote.

After ship completes, display:
```
Shipped! PR created.

Post-deploy checklist:
- [ ] Merge PR and confirm deploy on Render
- [ ] Run smoke tests on production
- [ ] Send notifications (see NOTIFY-LIST.md)
- [ ] Monitor for 30 minutes (watch /actuator/health)

SDLC cycle complete for: {idea description}
```

Update sdlc-state.md: Current phase → "Complete"

</phases>

<feedback-loop>
## If issues arise after deploy

**Critical bug (blocks users):**
Say: "For critical bugs: create a hotfix branch from main, run /sdlc-qa-task '<bug description>' to verify the fix scope, implement fix, then run gsd:verify-work and /sdlc-pre-release before deploying. Skip Phases 1-4."

**Minor bug (non-blocking):**
Say: "For minor bugs: run /gsd:add-todo '<bug description>' to capture for next milestone."

**New feature request:**
Say: "For new features: run /sdlc '<feature description>' to start a full cycle."
</feedback-loop>

<resume>
## Resuming a paused workflow

If invoked with no argument or with "resume":
If `.planning/sdlc-state.md` does not exist, tell the user: 'No active SDLC workflow found. Start one with /sdlc "<idea description>"' and stop.
1. Read `.planning/sdlc-state.md`
2. Find the last incomplete phase (first gate still "pending")
3. Resume from that phase
4. Tell the user: "Resuming SDLC for '{idea}' — continuing from Phase {N}: {name}"
</resume>
