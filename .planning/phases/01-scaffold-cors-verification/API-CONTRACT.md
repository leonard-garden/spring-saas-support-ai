# API Contract — Phase 1: Scaffold + CORS Verification

*Source of truth: verified against Java source code in `src/main/java/`.*

---

## Phase 1 Callout

Only one endpoint is called in Phase 1:

> **`POST /api/v1/auth/login`** — used by the `/cors-test` page to verify CORS is working.

All other endpoints below are documented for downstream phases (2–6). Phase 1 implementers only need to wire the login call.

---

## Conventions

### Authentication
All protected endpoints require:
```
Authorization: Bearer <access_token>
```
Unauthenticated access returns `401` with a `ProblemDetail` body (not the `ApiResponse` envelope — see Error Format).

### Success Response Envelope
```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

### Error Format
Errors are returned as **RFC 7807 ProblemDetail** by `GlobalExceptionHandler` — NOT the `ApiResponse` envelope:
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed for object='loginRequest'...",
  "instance": "/api/v1/auth/login"
}
```

### Role Enum
Valid roles: `OWNER`, `ADMIN`, `MEMBER`
> **Doc bug:** Some Swagger `@Schema` descriptions in the source say "AGENT" — the actual `Role.java` enum has `OWNER`, `ADMIN`, `MEMBER`. Use these three values.

---

## Auth Endpoints (`/api/v1/auth`)

### POST /api/v1/auth/signup
Purpose: Register a new business account. Creates a Business entity + Owner member.
Auth: Public

Request body:
```json
{
  "businessName": "string",  // required — company/workspace name
  "email": "string",         // required, must be valid email
  "password": "string"       // required, min 8 characters
}
```

Response 201:
```json
{
  "success": true,
  "data": {
    "accessToken": "string",   // JWT, 15-minute TTL
    "refreshToken": "string",  // opaque token, 7-day TTL
    "businessId": "uuid",
    "memberId": "uuid"
  },
  "error": null
}
```

Errors:
- `400` — validation failure (ProblemDetail)
- `409` — email already registered (ProblemDetail)

---

### POST /api/v1/auth/login
Purpose: Authenticate with email + password, receive JWT tokens.
Auth: Public

> **Phase 1 — this is the endpoint called by the `/cors-test` page.**

Request body:
```json
{
  "email": "string",     // required
  "password": "string"   // required
}
```

Response 200:
```json
{
  "success": true,
  "data": {
    "accessToken": "string",   // JWT, 15-minute TTL
    "refreshToken": "string",  // opaque token, 7-day TTL
    "businessId": "uuid",
    "memberId": "uuid"
  },
  "error": null
}
```

Errors:
- `400` — blank fields (ProblemDetail)
- `401` — invalid credentials (ProblemDetail)

---

### POST /api/v1/auth/refresh
Purpose: Exchange a valid refresh token for new access + refresh tokens.
Auth: Public

Request body:
```json
{
  "refreshToken": "string"  // required
}
```

Response 200:
```json
{
  "success": true,
  "data": {
    "accessToken": "string",
    "refreshToken": "string",
    "businessId": "uuid",
    "memberId": "uuid"
  },
  "error": null
}
```

Errors:
- `400` — blank or expired/invalid refresh token (ProblemDetail)

---

### POST /api/v1/auth/logout
Purpose: Revoke the refresh token (server-side invalidation).
Auth: Public (token passed in body)

Request body:
```json
{
  "refreshToken": "string"  // required
}
```

Response 204: (no body)

Errors:
- `400` — blank token (ProblemDetail)

---

### GET /api/v1/auth/me
Purpose: Return the currently authenticated member's profile.
Auth: Required

Request body: —

Response 200:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "email": "string",
    "role": "OWNER | ADMIN | MEMBER",
    "businessId": "uuid",
    "businessName": "string",
    "emailVerified": true
  },
  "error": null
}
```

Errors:
- `401` — not authenticated (ProblemDetail)

---

### POST /api/v1/auth/forgot-password
Purpose: Send a password reset email if the account exists. Always returns 200 to prevent email enumeration.
Auth: Public

Request body:
```json
{
  "email": "string"  // required, valid email
}
```

Response 200:
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

Errors:
- `400` — invalid email format (ProblemDetail)

---

### POST /api/v1/auth/reset-password
Purpose: Set a new password using the reset token from email.
Auth: Public

Request body:
```json
{
  "token": "string",     // required — from reset email link
  "password": "string"   // required, min 8 characters
}
```

Response 204: (no body)

Errors:
- `400` — invalid/expired token or weak password (ProblemDetail)

---

### POST /api/v1/auth/verify-email
Purpose: Confirm email ownership using the verification token from email.
Auth: Public

Request body:
```json
{
  "token": "string"  // required — from verification email link
}
```

Response 204: (no body)

Errors:
- `400` — invalid/expired token (ProblemDetail)

---

## Member Endpoints (`/api/v1/members`)

All member endpoints require `ADMIN` or `OWNER` role (except where noted).

### GET /api/v1/members
Purpose: List all members in the current tenant.
Auth: Required (ADMIN or OWNER)

Query params: —
Request body: —

Response 200:
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "email": "string",
      "role": "OWNER | ADMIN | MEMBER",
      "createdAt": "2024-01-01T00:00:00Z"
    }
  ],
  "error": null
}
```

Errors:
- `401` — not authenticated
- `403` — insufficient role (MEMBER trying to access)

---

### GET /api/v1/members/{id}
Purpose: Get a single member by ID.
Auth: Required (ADMIN or OWNER)

Path params:
- `id` (UUID, required)

Response 200:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "email": "string",
    "role": "OWNER | ADMIN | MEMBER",
    "createdAt": "2024-01-01T00:00:00Z"
  },
  "error": null
}
```

Errors:
- `401` — not authenticated
- `403` — insufficient role
- `404` — member not found

---

### DELETE /api/v1/members/{id}
Purpose: Remove a member from the tenant. Only OWNER can delete.
Auth: Required (OWNER only)

Path params:
- `id` (UUID, required)

Response 204: (no body)

Errors:
- `401` — not authenticated
- `403` — caller is not OWNER
- `404` — member not found

---

### PATCH /api/v1/members/{id}/role
Purpose: Change a member's role.
Auth: Required (OWNER only)

Path params:
- `id` (UUID, required)

Request body:
```json
{
  "role": "ADMIN | MEMBER"  // required — cannot assign OWNER via this endpoint
}
```

Response 200:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "email": "string",
    "role": "ADMIN | MEMBER",
    "createdAt": "2024-01-01T00:00:00Z"
  },
  "error": null
}
```

Errors:
- `400` — null role (ProblemDetail)
- `401` — not authenticated
- `403` — caller is not OWNER
- `404` — member not found

---

## Invitation Endpoints

### POST /api/v1/members/invite
Purpose: Send an invitation email to a new member. Returns 202 (accepted, async email send).
Auth: Required (OWNER or ADMIN)
Controller: `InvitationController` (not `MemberController`)

Request body:
```json
{
  "email": "string",      // required, valid email
  "role": "ADMIN | MEMBER"  // required
}
```

Response 202:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "email": "string",
    "role": "ADMIN | MEMBER",
    "expiresAt": "2024-01-08T00:00:00Z",
    "createdAt": "2024-01-01T00:00:00Z"
  },
  "error": null
}
```

Errors:
- `400` — invalid email or null role (ProblemDetail)
- `401` — not authenticated
- `409` — email already a member (ProblemDetail)

---

### POST /api/v1/invitations/accept
Purpose: Accept an invitation by setting a password. Returns JWT tokens on success.
Auth: Public

Request body:
```json
{
  "token": "string",     // required — from invitation email link
  "password": "string"   // required, min 8 characters
}
```

Response 200:
```json
{
  "success": true,
  "data": {
    "accessToken": "string",
    "refreshToken": "string",
    "businessId": "uuid",
    "memberId": "uuid"
  },
  "error": null
}
```

Errors:
- `400` — invalid/expired token or weak password (ProblemDetail)

---

## Knowledge Base Endpoints

*Documented for Phase 5 (KB Management). Backend endpoints not yet implemented.*

Placeholder for:
- `GET /api/v1/knowledge-bases` — list all KBs for tenant
- `POST /api/v1/knowledge-bases` — create a KB
- `GET /api/v1/knowledge-bases/{id}` — get KB by ID
- `PUT /api/v1/knowledge-bases/{id}` — update KB
- `DELETE /api/v1/knowledge-bases/{id}` — delete KB

*These will be defined in Phase 5's API-CONTRACT.md when the backend controllers exist.*

---

## CORS Configuration Reference

For Phase 1 verification context:

| Setting | Value |
|---------|-------|
| Backend port (dev) | `8081` |
| `VITE_API_URL` | `http://localhost:8081/api/v1` |
| Allowed origin (dev) | `http://localhost:5173` (pre-configured in `application-dev.yml`) |
| `allowCredentials` | `true` (Spring Security config) |
| Frontend axios | `withCredentials: false` (Bearer token flow, not cookies) |

The dev profile CORS origin `http://localhost:5173` is already in `application-dev.yml` — no backend changes needed for Phase 1.
