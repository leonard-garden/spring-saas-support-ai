# M2 API Contract — Knowledge Base & RAG

**Date:** 2026-05-17  
**Base URL:** `/api/v1`  
**Auth:** Bearer JWT  
**Content-Type:** `application/json`

---

## Shared Schemas

### DocumentResponse

```json
{
  "id":           "uuid",
  "filename":     "product-guide.pdf",
  "contentType":  "application/pdf",
  "status":       "PENDING | PROCESSING | READY | FAILED",
  "sizeBytes":    204800,
  "chunkCount":   47,
  "errorMessage": null,
  "createdAt":    "2026-05-17T10:00:00Z",
  "updatedAt":    "2026-05-17T10:02:31Z"
}
```

- `chunkCount` — `null` until status is `READY`
- `errorMessage` — `null` unless status is `FAILED`

### ErrorResponse (all 4xx/5xx)

```json
{
  "status":    400,
  "error":     "BAD_REQUEST",
  "message":   "File type not supported. Allowed: pdf, txt, md",
  "timestamp": "2026-05-17T10:00:00Z"
}
```

---

## Endpoints

### GET /kb

**Auth:** Any role  
Get Knowledge Base info for the current tenant. KB is created at business signup — this endpoint never creates one.

**Response 200:**
```json
{
  "id":            "uuid",
  "businessId":    "uuid",
  "documentCount": 3,
  "readyCount":    2,
  "createdAt":     "2026-05-17T09:00:00Z"
}
```

| Status | Description |
|---|---|
| 200 | KB info |
| 404 | KB not found (signup bug) |

---

### POST /kb/documents

**Auth:** ADMIN only  
Upload a document. File is stored in MinIO immediately. Processing starts async — response returns before processing completes.

**Request:** `multipart/form-data`
```
file: <binary>   // required — PDF, TXT, or MD
                 // max size: 10MB (413 if exceeded)
                 // allowed MIME: application/pdf, text/plain, text/markdown
```

**Response 202 Accepted:**
```json
{
  "id":          "uuid",
  "filename":    "product-guide.pdf",
  "contentType": "application/pdf",
  "status":      "PENDING",
  "sizeBytes":   204800,
  "chunkCount":  null,
  "createdAt":   "2026-05-17T10:00:00Z"
}
```

| Status | Description |
|---|---|
| 202 | Accepted, async processing started |
| 400 | Invalid file type |
| 403 | Not ADMIN |
| 413 | File > 10MB |

---

### GET /kb/documents

**Auth:** Any role  
List all documents in the tenant's Knowledge Base, sorted by `createdAt` descending.

**Query params:**
```
status: PENDING | PROCESSING | READY | FAILED   // optional filter
```

**Response 200:**
```json
{
  "documents": [
    { "...": "DocumentResponse" }
  ],
  "total": 3
}
```

| Status | Description |
|---|---|
| 200 | List (empty `[]` if none) |

---

### GET /kb/documents/{id}

**Auth:** Any role  
Get a single document with current status. Used by FE for status polling (every 3s while `PROCESSING`).

**Response 200:** `DocumentResponse`

| Status | Description |
|---|---|
| 200 | Document detail |
| 404 | Not found or wrong tenant |

---

### DELETE /kb/documents/{id}

**Auth:** ADMIN only  
Delete a document. Cascades: removes all `document_chunk` rows from pgvector and deletes the raw file from MinIO. Idempotent — 404 if already deleted.

**Response:** `204 No Content`

| Status | Description |
|---|---|
| 204 | Deleted |
| 403 | Not ADMIN or wrong tenant |
| 404 | Not found |

---

### POST /kb/search

**Auth:** Any role  
Hybrid RAG search — combines vector similarity (pgvector cosine) + full-text search (tsvector). Returns top-k chunks ranked by score. M3 AI Chat will call this endpoint to build context for LLM.

**Request body:**
```json
{
  "query": "refund policy",   // required, min 3 chars
  "topK":  5                  // optional, default 5, max 20
}
```

**Response 200:**
```json
{
  "query": "refund policy",
  "results": [
    {
      "chunkId":      "uuid",
      "content":      "Refunds are processed within 5 business days...",
      "score":        0.92,
      "documentId":   "uuid",
      "documentName": "faq.pdf",
      "chunkIndex":   3,
      "source":       "VECTOR | FTS | BOTH"
    }
  ],
  "totalFound": 5
}
```

- `score` — cosine similarity `[0.0–1.0]`
- `source` — which search lane found this chunk (`VECTOR`, `FTS`, or `BOTH`)

| Status | Description |
|---|---|
| 200 | Results (empty `[]` if no match) |
| 400 | Query too short (< 3 chars) |
| 422 | No READY documents to search |

---

## Notes

- All endpoints are tenant-scoped — `businessId` is extracted from JWT, never passed as a parameter.
- DELETE on a `PROCESSING` document is rejected with `409 Conflict` to avoid race conditions with the async pipeline.
- FE polls `GET /kb/documents/{id}` every 3 seconds while status is `PROCESSING` or `PENDING`. Polling stops when status transitions to `READY` or `FAILED`.
