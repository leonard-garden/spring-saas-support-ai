---
name: release
version: 1.0.0
description: |
  Generate release notes and deploy checklist for a version.
  Output: docs/releases/v{X.Y.Z}-release.md
  Triggers: "release note", "release notes", "changelog", "what shipped",
  "write release", "deploy checklist", "prepare release", "release v"
---

## Purpose

Generate release notes and a deploy checklist for a version, aggregating across all features in that release.

## Invoke

`/release v{X.Y.Z}` — or auto-triggered.

## Process

1. **Get version** — from args (e.g., `v0.3.0`). If missing, read `<version>` from `pom.xml`.
2. **Read context:**
   - `CLAUDE.md`
   - `.claude/memory/project-context.md` — roadmap and milestone info
3. **Find last release tag:**
   ```bash
   git tag --sort=-version:refname | head -5
   ```
4. **Get commits since last tag:**
   ```bash
   git log {last-tag}..HEAD --oneline --no-merges
   ```
5. **Identify features in this release** — from commit messages and milestone context. For each feature slug found, read `docs/features/{slug}/SRS.md`.
6. **Scan new ADRs** — read `docs/adr/` and find ADRs created after the last release date.
7. **Check dependency changes:**
   ```bash
   git diff {last-tag}..HEAD -- pom.xml | grep "^[+-].*<version>"
   ```
8. **Create directory** — `mkdir -p docs/releases`
9. **Generate release doc** — follow template below
10. **Save** — `docs/releases/v{X.Y.Z}-release.md`
11. **Commit:**
    ```bash
    git add docs/releases/v{X.Y.Z}-release.md
    git commit -m "docs(release): add release notes for v{X.Y.Z}"
    ```

## Output Template

```
# Release v{X.Y.Z} — {Milestone Name}

**Date:** {YYYY-MM-DD}
**Branch:** develop → master
**Milestone:** M{N} — {description}
**Previous release:** v{X.Y.Z-1}

---

## What's New

### Features
- **{Feature Name}**: {one-line description} — [SRS](../features/{slug}/SRS.md)

### Bug Fixes
- {fix description} (#{issue-number})

### Breaking Changes
- {list breaking changes, or "None"}

---

## Technical Changes

### New Endpoints
| Method | Path | Description |
|--------|------|-------------|

### New DB Migrations
| File | Description |
|------|-------------|

### Dependencies Updated
| Package | From | To | Reason |
|---------|------|----|--------|

---

## Deploy Checklist

### Pre-deploy
- [ ] `mvn verify` passes (unit + integration tests)
- [ ] `TenantIsolationIT` passes
- [ ] `gitleaks detect --source . --no-banner` — no secrets found
- [ ] Flyway migrations tested on a copy of staging DB
- [ ] `.env.example` updated if new environment variables added
- [ ] Swagger UI verified locally: `http://localhost:8081/swagger-ui.html`

### Deploy steps
- [ ] Build: `mvn clean package -DskipTests`
- [ ] Apply migrations: `mvn flyway:migrate` (or Flyway auto-migrate on startup)
- [ ] Deploy JAR to Render/Railway
- [ ] Smoke test: `curl https://{host}/actuator/health` → `{"status":"UP"}`

### Post-deploy
- [ ] Swagger UI accessible at `https://{host}/swagger-ui.html`
- [ ] Test 1 happy-path flow per new feature using Swagger UI or curl
- [ ] Monitor application logs for 10 minutes post-deploy
- [ ] Update GitHub milestone to Closed

---

## Architecture Decisions (this release)

- [ADR-{NNN}](../adr/ADR-{NNN}-{slug}.md): {title}

---

## Known Limitations

{List known bugs or limitations being shipped with this version}

---

## What's Next (v{X.Y.Z+1})

{Brief preview of next milestone}
```

## Rules

- Version format: `v{major}.{minor}.{patch}` — match git tag format
- Release doc lives in `docs/releases/` not `docs/features/` — it aggregates multiple features
- Always run the `git log` command to get the actual commits — don't guess what shipped
- Deploy checklist items are actionable checkboxes — not prose descriptions
