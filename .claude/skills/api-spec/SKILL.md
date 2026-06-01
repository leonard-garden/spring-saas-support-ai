---
name: api-spec
version: 1.0.0
description: |
  Design REST API contract for a feature. Output: docs/features/{slug}/api-spec.md
  Triggers: "design API", "REST contract", "API for X",
  "endpoint for X", "API contract", "write API spec", "API design"
---

## Purpose

Design and document the REST API contract for a feature.

## Invoke

`/api-spec {feature-name}` — or auto-triggered.

## Process

1. **Get feature name** — from args or ask.
2. **Derive slug** — kebab-case.
3. **Read context:**
   - `CLAUDE.md` — especially the ApiResponse<T> envelope format
   - `.claude/memory/architecture.md`
   - `.claude/memory/tech-stack.md`
   - `.claude/skills/forge/references/api-patterns.md`
4. **Read SRS** — `docs/features/{slug}/SRS.md` if it exists. Map each Functional Requirement to candidate endpoints.
5. **Scan existing controllers** — check `src/main/java/com/leonardtrinh/supportsaas/` for any existing Controller for this feature. If found, reverse-engineer the spec from code (code is source of truth).
6. **Read security config** — `src/main/java/com/leonardtrinh/supportsaas/config/SecurityConfig.java` to understand which paths are public vs JWT-protected.
7. **Create directory** — `mkdir -p docs/features/{slug}`
8. **Generate API spec** — follow template below
9. **Save** — `docs/features/{slug}/api-spec.md`
10. **Commit:**
    ```bash
    git add docs/features/{slug}/api-spec.md
    git commit -m "docs(api): add API spec for {slug}"
    ```

## Output Template

```
# API Spec — {Feature Name}

**Version:** 1.0
**Date:** {YYYY-MM-DD}
**Base URL:** /api/v1/

---

## Overview

| Property | Value |
|----------|-------|
| Auth | Bearer JWT in `Authorization` header (except public endpoints) |
| Response envelope | `ApiResponse<T>` — `{success: bool, data: T, error: string}` |
| Error format | RFC 7807 ProblemDetail |
| Content-Type | application/json |

---

## Endpoints

### POST /api/v1/{resource}

**Description:** {What this endpoint does}
**Auth required:** YES / NO
**Roles:** ADMIN | MEMBER | PUBLIC

**Request body:**
​```json
{
  "field": "string — description",
  "field2": "uuid — description"
}
​```

**Response 201 Created:**
​```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "field": "value"
  },
  "error": null
}
​```

**Error responses:**
| HTTP | Condition |
|------|-----------|
| 400  | Validation failed — missing required field |
| 401  | Missing or invalid JWT |
| 403  | Insufficient role |
| 404  | Resource not found |
| 409  | Conflict — resource already exists |
| 429  | Rate limit exceeded |

---

### GET /api/v1/{resource}/{id}

{same structure as above}

---

## Common Error Format

All errors follow RFC 7807 ProblemDetail:
​```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Document abc-123 not found",
  "instance": "/api/v1/documents/abc-123"
}
​```

---

## Rate Limiting
{Describe limits if applicable, e.g., "10 req/min per tenant on POST endpoints"}

---

## Swagger Annotations Reference

Each endpoint should have these annotations on the Controller method:
​```java
@Operation(summary = "{description}")
@ApiResponse(responseCode = "201", description = "{success description}")
@ApiResponse(responseCode = "400", description = "Validation failed")
@ApiResponse(responseCode = "401", description = "Unauthorized")
​```
```

## Rules

- Every response uses `ApiResponse<T>` envelope — never return naked objects or naked lists
- Error body is always `ProblemDetail` (RFC 7807) — never a custom error JSON
- Path convention: `/api/v1/{resource}/{id}/{sub-resource}`
- Widget/public endpoints must be explicitly marked `Auth required: NO` and listed separately
- Always suggest `@Operation` + `@ApiResponse` annotations — the `api-doc-sync` agent enforces these
- Multi-tenant note: all tenant-scoped endpoints implicitly filter by the JWT's `tenant_id` — document this in Overview
