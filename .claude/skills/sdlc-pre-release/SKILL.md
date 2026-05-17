---
name: sdlc-pre-release
version: 1.0.0
description: |
  Pre-release documentation generator. Reads GSD phase artifacts and produces
  4 release documents: RELEASE-NOTES.md, CONFIG-GUIDE.md, NOTIFY-LIST.md,
  ROLLBACK-PLAN.md. Invoke after verify-work gate is approved, before gsd:ship.
  Triggered by /sdlc orchestrator or manually with /sdlc-pre-release.
---

<purpose>
Generate all pre-release documentation for a completed GSD phase.
Save 4 files to the active phase directory. Present a hard gate before ship.
</purpose>

<process>

**Empty section convention:** When a section has no content, always write a sentinel line (e.g., `NONE` or `No X in this release.`) — never omit the section heading or leave the section blank. This keeps document structure consistent across releases.

## Step 1: Resolve active phase

Read `.planning/STATE.md` for the current active phase if present.
If STATE.md is absent, check `.planning/sdlc-state.md` — look for a line containing `<resolved-phase-number>:` or `phase-number:` and use that value.
Otherwise, use the highest-numbered directory prefix in `.planning/phases/` by parsing the leading integer (e.g., `03-auth` → 3, `10-billing` → 10; take the maximum).
Record as `<current-phase>`.

Read all of these files before generating any output:
- `.planning/phases/<current-phase>/` — Read every file matching `.planning/phases/<current-phase>/**/*SUMMARY*.md` and `.planning/phases/<current-phase>/**/*PLAN*.md` (recursive, case-insensitive). If neither exists, output: 'No SUMMARY.md or PLAN.md found in phase directory. Cannot generate release docs.' and stop.
- `.planning/phases/<current-phase>/VERIFICATION.md` (if exists)
- `.planning/ROADMAP.md` — for version and milestone context
- `render.yaml` or `docker-compose.yml` — for infra/deploy config
- `src/main/resources/application*.yml` — for environment variable changes
- `src/main/resources/db/migration/` — for new Flyway migrations

Extract version: Look in ROADMAP.md for a line matching `## v<semver>` or `version: <semver>` nearest to the active milestone. If not found, check `pom.xml` for the `<version>` tag. Store as `<resolved-version>` and use it in place of `{version}` consistently across all 4 documents.

## Step 2: Generate RELEASE-NOTES.md

Before writing, check if the output file already exists. If it does, display: 'WARNING: RELEASE-NOTES.md already exists. Overwrite? (y/n)' and wait for confirmation before proceeding.

Analyze SUMMARY.md and PLAN.md to extract what was built.

```markdown
# Release Notes — v{version} — {milestone-phase-name}

**Release date:** {YYYY-MM-DD}
**Phase:** {current-phase}
**Type:** Feature / Bug fix / Hotfix  ← choose the most accurate

## What's New
- **[feature-name]:** [one sentence description of what it does for the user]

## Bug Fixes
- **[fix-name]:** [what was broken, what was fixed]

## Breaking Changes
- NONE  ← or list changes that require consumer action

## Known Issues
- NONE  ← or list any outstanding issues

## Upgrade Notes
[Only include if there are migration steps — otherwise omit this section]
```

Rules:
- Write from the user's perspective ("You can now..." not "We added...")
- No internal jargon (task IDs, PR numbers, file names)
- If nothing was fixed, omit Bug Fixes section
- If no breaking changes, write NONE (do not omit the line)

Save to: `.planning/phases/<current-phase>/RELEASE-NOTES.md`

## Step 3: Generate CONFIG-GUIDE.md

Before writing, check if the output file already exists. If it does, display: 'WARNING: CONFIG-GUIDE.md already exists. Overwrite? (y/n)' and wait for confirmation before proceeding.

Identify changes by running: `git diff $(git merge-base HEAD main 2>/dev/null || git merge-base HEAD develop) HEAD -- src/main/resources/ render.yaml docker-compose.yml`. If this fails (shallow clone or no remote), read the files directly and note '(full-file review — no git baseline)' in the relevant sections.

Changes to identify:
1. New or changed environment variables in `application*.yml` and `render.yaml`
2. New Flyway migration files in `src/main/resources/db/migration/`
3. Infrastructure changes in `render.yaml` or `docker-compose.yml`

```markdown
# Config Guide — v{version}

## Pre-Deployment Checklist
- [ ] Set all new environment variables (see table below)
- [ ] Run database migrations
- [ ] Verify health check passes after deploy

## Environment Variables

### New Variables
| Variable | Required | Description | Example Value |
|----------|----------|-------------|---------------|
| VARIABLE_NAME | Yes/No | What it does | example |

### Changed Variables
| Variable | Old Behavior | New Behavior |
|----------|-------------|--------------|
| VARIABLE_NAME | ... | ... |

If no new or changed variables: write `No environment variable changes in this release.`

## Database Migrations

Migrations to run (in order):
1. `V{n}__{description}.sql` — [what it does]

Run command:
```bash
mvn flyway:migrate
```

If no migrations: write `No database migrations in this release.`

## Infrastructure Changes
[Describe any render.yaml, docker-compose, or service config changes]
If none: write `No infrastructure changes in this release.`

## Deployment Steps
1. Apply environment variable changes in Render dashboard (if any)
2. Run database migrations: `mvn flyway:migrate`
3. Deploy the new build
4. Run smoke tests (see ROLLBACK-PLAN.md for rollback if needed)
```

Save to: `.planning/phases/<current-phase>/CONFIG-GUIDE.md`

## Step 4: Generate NOTIFY-LIST.md

Before writing, check if the output file already exists. If it does, display: 'WARNING: NOTIFY-LIST.md already exists. Overwrite? (y/n)' and wait for confirmation before proceeding.

```markdown
# Notify List — v{version}

## Stakeholders to Notify

| Team/Person | Channel | When | Message |
|-------------|---------|------|---------|
| Engineering | Slack #dev | Before deploy (15 min) | See template below |
| Stakeholders | Email | After deploy (confirmed) | See template below |

## Message Templates

### Pre-deploy (Engineering — Slack)
> Deploying v{version} ({phase-name}) in ~15 minutes.
> Changes: {one-line summary from RELEASE-NOTES.md}
> Downtime expected: None / ~{X} minutes
> Rollback plan: ready (see ROLLBACK-PLAN.md)

### Post-deploy (Stakeholders — Email)
Subject: v{version} deployed — {phase-name}

{phase-name} is now live.

What's new:
{bullet list from What's New section of RELEASE-NOTES.md}

Any issues, contact: [your contact]
```

Save to: `.planning/phases/<current-phase>/NOTIFY-LIST.md`

## Step 5: Generate ROLLBACK-PLAN.md

Before writing, check if the output file already exists. If it does, display: 'WARNING: ROLLBACK-PLAN.md already exists. Overwrite? (y/n)' and wait for confirmation before proceeding.

```markdown
# Rollback Plan — v{version}

## Trigger Conditions
Roll back immediately if any of the following occur within 30 minutes of deploy:
- Health check endpoint (`/actuator/health`) returns non-200 for >2 minutes
- API error rate visibly elevated (check logs)
- Critical user-reported bug blocking core functionality

## Rollback Steps

### Step 1: Revert application (Render)
1. Go to Render dashboard → your service → Deploys tab
2. Find the previous successful deploy
3. Click "Redeploy" on that deploy
4. Wait for deploy to complete (~2-3 minutes)

### Step 2: Revert database migrations (if applicable)
```
{List specific rollback SQL for each migration, OR write:}
No destructive migrations in this release — no database rollback needed.
```

### Step 3: Revert environment variables (if applicable)
{List any variables to remove or restore, OR write:}
No environment variable changes — no rollback needed.

### Step 4: Notify team
Send to Slack #dev:
> Rolling back v{version} due to [REASON]. ETA: ~5 minutes. Will update when stable.

## Post-Rollback Actions
1. Open an incident report noting: what failed, when, impact
2. Identify root cause before attempting re-deploy
3. Fix → run full QA (gsd:verify-work) → re-run /sdlc-pre-release → redeploy
```

Save to: `.planning/phases/<current-phase>/ROLLBACK-PLAN.md`

## Step 6: Hard gate — request approval

After all 4 files are created, display:

```
Pre-release documentation ready. Created:
✓ .planning/phases/<current-phase>/RELEASE-NOTES.md
✓ .planning/phases/<current-phase>/CONFIG-GUIDE.md
✓ .planning/phases/<current-phase>/NOTIFY-LIST.md
✓ .planning/phases/<current-phase>/ROLLBACK-PLAN.md

ACTION REQUIRED before proceeding:
1. Review CONFIG-GUIDE.md — apply all config changes to production NOW (env vars, etc.)
2. Share NOTIFY-LIST.md pre-deploy message with Engineering team

[GATE] Confirm config applied and team notified. Approve to proceed to gsd:ship? (y/n)
(Do not continue until you receive a response.)
```

- If y: Write to `.planning/sdlc-state.md` (append if exists, create if not): `prerelease_gate: approved — {YYYY-MM-DD}`. Then say "Pre-release approved. Run /sdlc or gsd:ship <phase-number> to deploy."
- If n: ask "What still needs to be done?" and wait. After the user responds, immediately ask: 'Reply READY when all pending items are complete.' Do not proceed or call any tools until the user sends READY, YES, or Y. Then re-display the full gate block above and wait for approval again.

</process>
