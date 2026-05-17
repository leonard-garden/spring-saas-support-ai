# Config Guide — v0.2

## Pre-Deployment Checklist
- [ ] Set all new environment variables in Render dashboard (see table below)
- [ ] Confirm `CORS_ALLOWED_ORIGINS` includes your production frontend URL
- [ ] No database migrations in this release — skip migration step
- [ ] Verify health check passes after deploy (`/actuator/health`)

## Environment Variables

### New Variables

| Variable | Required | Description | Example Value |
|----------|----------|-------------|---------------|
| CORS_ALLOWED_ORIGINS | Yes | Comma-separated list of frontend origins allowed by CORS | `https://your-frontend.onrender.com` |
| MAIL_USERNAME | Yes | SMTP username (Gmail or other provider) | `you@gmail.com` |
| MAIL_PASSWORD | Yes | SMTP password or app password | `your-app-password` |
| MAIL_SENDER | No | From address for outgoing emails (defaults to MAIL_USERNAME) | `noreply@yourdomain.com` |

### Changed Variables

| Variable | Old Behavior | New Behavior |
|----------|-------------|--------------|
| APP_BASE_URL | Already required | Now also used as CORS fallback origin if CORS_ALLOWED_ORIGINS is not set |

## Database Migrations

No database migrations in this release.

## Infrastructure Changes

`render.yaml` updated:
- `APP_BASE_URL` moved to a consistent position in the env block
- Added: `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SENDER`, `CORS_ALLOWED_ORIGINS` as secret env vars (`sync: false`)

`application-dev.yml` updated:
- Added `app.cors.allowed-origins: http://localhost:3000,http://localhost:5173` — dev CORS config now allows both common Vite ports

`application-prod.yml` updated:
- Added full SMTP mail configuration block
- Added `app.base-url` and `app.cors.allowed-origins` bound to env vars

## Deployment Steps

1. In Render dashboard, add the new environment variables listed above
2. Ensure `CORS_ALLOWED_ORIGINS` is set to your production frontend URL (e.g. `https://your-app.onrender.com`)
3. Deploy the new build
4. Verify `/actuator/health` returns `{"status":"UP"}`
5. If anything goes wrong, see ROLLBACK-PLAN.md
