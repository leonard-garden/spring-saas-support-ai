# Notify List — v0.2

## Stakeholders to Notify

| Team/Person | Channel | When | Message |
|-------------|---------|------|---------|
| Engineering | Slack #dev | Before deploy (15 min) | See template below |
| Stakeholders | Email | After deploy confirmed | See template below |

## Message Templates

### Pre-deploy (Engineering — Slack)
> Deploying v0.2 (Scaffold + CORS Verification) in ~15 minutes.
> Changes: Frontend scaffold scaffolded + CORS config wired between frontend and backend.
> Downtime expected: None
> Rollback plan: ready (see ROLLBACK-PLAN.md)

### Post-deploy (Stakeholders — Email)
Subject: v0.2 deployed — Frontend Scaffold + CORS Verification

Phase 1 of the Admin Dashboard is now live.

What's new:
- React + TypeScript frontend project scaffolded and compiling cleanly
- CORS verified — frontend can make API calls to the backend without browser errors
- Dev toolchain ready for all upcoming admin UI phases

Any issues, contact: Leonard Trinh
