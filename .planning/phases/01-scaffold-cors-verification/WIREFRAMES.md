# Wireframes — Phase 1: Scaffold + CORS Verification

*Design tokens from DESIGN-SYSTEM.md apply to all components below.*

---

## Screen: CORS Test

Route: `/cors-test`
Purpose: Verify that cross-origin `POST /api/v1/auth/login` succeeds from the Vite dev server to the Spring Boot backend on port 8081.

### Layout

```
┌─────────────────────────────────────────────────────────┐
│  bg-zinc-50  min-h-screen  flex items-center            │
│              justify-center  px-4                        │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Card  max-w-md  w-full                          │   │
│  │  ┌────────────────────────────────────────────┐  │   │
│  │  │  CardHeader  (border-b border-zinc-100)    │  │   │
│  │  │                                            │  │   │
│  │  │  CardTitle: "CORS Verification"            │  │   │
│  │  │  CardDescription:                          │  │   │
│  │  │    "Test cross-origin call to              │  │   │
│  │  │     POST /api/v1/auth/login"               │  │   │
│  │  └────────────────────────────────────────────┘  │   │
│  │                                                   │   │
│  │  CardContent  (px-6 py-4)                         │   │
│  │  ┌────────────────────────────────────────────┐  │   │
│  │  │  <form>  space-y-4                         │  │   │
│  │  │                                            │  │   │
│  │  │  [label] Email                             │  │   │
│  │  │  [Input]  type=email  placeholder=         │  │   │
│  │  │           owner@acme.com                   │  │   │
│  │  │                                            │  │   │
│  │  │  [label] Password                          │  │   │
│  │  │  [Input]  type=password                    │  │   │
│  │  │           placeholder=password             │  │   │
│  │  │                                            │  │   │
│  │  │  [Button]  Primary  full-width             │  │   │
│  │  │            "Send Request"                  │  │   │
│  │  │            loading: "Sending..."           │  │   │
│  │  └────────────────────────────────────────────┘  │   │
│  │                                                   │   │
│  │  ─ ─ ─ ─ ─ (shown after request) ─ ─ ─ ─ ─       │   │
│  │                                                   │   │
│  │  CardContent  (result area)                       │   │
│  │  ┌────────────────────────────────────────────┐  │   │
│  │  │  [Result Panel]  — see states below        │  │   │
│  │  └────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  text-xs text-zinc-400  mt-4  text-center               │
│  "Backend: http://localhost:8081"                        │
└─────────────────────────────────────────────────────────┘
```

### Components Used

| Component | Token / Source |
|-----------|---------------|
| Card, CardHeader, CardTitle, CardDescription, CardContent | shadcn/ui Card |
| Input (email, password) | shadcn/ui Input — default state; focus ring violet |
| Button (primary, full-width) | Design System Button — primary variant, md size |
| Result panel | Custom pre/code block or Alert component |
| Labels | `text-sm font-medium text-zinc-700` |

### States

**Idle (initial)**
- Form visible, empty fields, "Send Request" button enabled
- No result panel shown

**Loading**
- Button: disabled, text changes to "Sending..." with spinner (Loader2 icon, animate-spin)
- Form fields: disabled

**Success (HTTP 200)**
```
┌─────────────────────────────────────────────────────┐
│  Alert — success variant                            │
│  border-l-4 border-green-500 bg-green-50            │
│                                                     │
│  ✓ CORS OK — 200 OK                                 │
│                                                     │
│  <pre className="mt-2 text-xs overflow-auto">       │
│    {                                                │
│      "success": true,                               │
│      "data": {                                      │
│        "accessToken": "eyJ...",                     │
│        "refreshToken": "...",                       │
│        "businessId": "uuid",                        │
│        "memberId": "uuid"                           │
│      },                                             │
│      "error": null                                  │
│    }                                                │
│  </pre>                                             │
└─────────────────────────────────────────────────────┘
```

**CORS Error (network-level failure)**
```
┌─────────────────────────────────────────────────────┐
│  Alert — error variant                              │
│  border-l-4 border-red-500 bg-red-50                │
│                                                     │
│  ✗ CORS BLOCKED                                     │
│                                                     │
│  text-xs text-red-700:                              │
│  Network Error — request was blocked before         │
│  reaching the server. Check:                        │
│  • Backend running on http://localhost:8081?        │
│  • CORS origins include http://localhost:5173?      │
│  • Spring profile is 'dev'?                         │
└─────────────────────────────────────────────────────┘
```

**API Error (HTTP 4xx — CORS passed, server responded)**
```
┌─────────────────────────────────────────────────────┐
│  Alert — warning variant                            │
│  border-l-4 border-amber-500 bg-amber-50            │
│                                                     │
│  ⚠ CORS OK — but {status} returned                 │
│  (This means CORS is working!)                      │
│                                                     │
│  <pre className="mt-2 text-xs overflow-auto">       │
│    { raw response body }                            │
│  </pre>                                             │
└─────────────────────────────────────────────────────┘
```

### User Actions

- Fill email + password → click "Send Request" → see result panel
- Any HTTP response (200, 401, 400) → CORS pass (warn variant shows CORS OK)
- Network error → CORS blocked (error variant with checklist)
- Result visible → can re-submit with new credentials

### Notes for Implementation

- No routing guards — page is accessible without auth
- `VITE_API_URL` from `.env.development` must equal `http://localhost:8081/api/v1`
- Axios call: `axios.post('/auth/login', { email, password })` with baseURL from env var
- `withCredentials` should be `false` (Bearer token flow, not cookies)
- Response body always rendered as formatted JSON in `<pre>` tag
- CORS failure detected by `error.code === 'ERR_NETWORK'` or missing `error.response`
