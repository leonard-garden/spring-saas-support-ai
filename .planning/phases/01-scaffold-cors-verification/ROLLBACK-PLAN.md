# Rollback Plan — v0.2

## Trigger Conditions
Roll back immediately if any of the following occur within 30 minutes of deploy:
- Health check endpoint (`/actuator/health`) returns non-200 for >2 minutes
- API error rate visibly elevated in logs
- CORS errors appearing in browser for previously working flows
- Critical user-reported bug blocking core functionality

## Rollback Steps

### Step 1: Revert application (Render)
1. Go to Render dashboard → your service → Deploys tab
2. Find the previous successful deploy
3. Click "Redeploy" on that deploy
4. Wait for deploy to complete (~2–3 minutes)

### Step 2: Revert database migrations
No destructive migrations in this release — no database rollback needed.

### Step 3: Revert environment variables (if applicable)
If CORS errors occur after rollback, temporarily remove `CORS_ALLOWED_ORIGINS` from Render env vars to restore the previous CORS behavior.

### Step 4: Notify team
Send to Slack #dev:
> Rolling back v0.2 due to [REASON]. ETA: ~5 minutes. Will update when stable.

## Post-Rollback Actions
1. Open an incident report noting: what failed, when, impact
2. Identify root cause before attempting re-deploy
3. Fix → run full QA (`/gsd:verify-work 1`) → re-run `/sdlc-pre-release` → redeploy
