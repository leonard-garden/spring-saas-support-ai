# Release Notes — v0.2 — Phase 01: Scaffold + CORS Verification

**Release date:** 2026-05-14
**Phase:** 01-scaffold-cors-verification
**Type:** Feature

## What's New

- **Frontend project:** A React + TypeScript admin SPA is now scaffolded at `frontend/` and compiles cleanly. The foundation is in place for all subsequent admin UI phases.
- **CORS verified:** Cross-origin requests from the frontend (`localhost:5173`) to the Spring Boot backend (`localhost:8081`) work correctly — preflight passes and API responses are received without browser errors.
- **Dev toolchain ready:** `npm run dev` starts the frontend on port 5173 (strict — fails fast if occupied). `npm run build` produces a production bundle with zero TypeScript errors.

## Bug Fixes

NONE

## Breaking Changes

NONE

## Known Issues

NONE
