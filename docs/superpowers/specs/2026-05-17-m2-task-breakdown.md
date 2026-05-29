# M2 Task Breakdown — Knowledge Base & RAG

**Date:** 2026-05-17  
**Milestone:** M2 — Knowledge Base & RAG Pipeline  
**Delivery model:** Vertical slices — each phase ships complete FE + BE

---

## Phase 1 — Foundation & Upload

**Goal:** DB migrations, MinIO wiring, upload API, FE document list + upload modal.  
**Done when:** ADMIN can upload a PDF/TXT and see it appear as `PENDING` in the list.

### BE

| # | Task | Notes |
|---|------|-------|
| 1.1 | Add `pgvector` dependency to `pom.xml` + enable extension via Flyway V13 | `CREATE EXTENSION IF NOT EXISTS vector` |
| 1.2 | Flyway V13 — create `knowledge_base` table | `id`, `business_id` UNIQUE, `created_at` |
| 1.3 | Flyway V14 — create `document` table | `status` enum, `minio_key`, `size_bytes`, `content_type`, `error_message` |
| 1.4 | Flyway V15 — create `document_chunk` table | `vector(1536)`, IVFFlat index, tsvector GIN index, `content_hash` UNIQUE per `business_id` |
| 1.5 | `KnowledgeBase` entity + `KnowledgeBaseRepository` | JPA entity, Hibernate tenant filter |
| 1.6 | `Document` entity + `DocumentRepository` | status enum (`PENDING/PROCESSING/READY/FAILED`), JPA |
| 1.7 | Auto-create `KnowledgeBase` on business signup | Extend `AuthServiceImpl.signup()` |
| 1.8 | `MinioService` — upload / delete / presigned URL | `io.minio:minio` client, env-configured bucket |
| 1.9 | `DocumentService.upload()` — store to MinIO → INSERT PENDING | Returns `DocumentResponse` |
| 1.10 | `DocumentController` — `POST /kb/documents`, `GET /kb/documents`, `GET /kb/documents/{id}`, `DELETE /kb/documents/{id}` | Multipart upload, ADMIN guard on write ops |
| 1.11 | `KnowledgeBaseController` — `GET /kb` | Returns KB info + counts |
| 1.12 | Unit tests: `DocumentService`, `MinioService` (mock MinIO) | |
| 1.13 | Integration test: upload flow + tenant isolation | Two tenants, assert no cross-tenant access |

### FE

| # | Task | Notes |
|---|------|-------|
| 1.14 | `useDocuments` React Query hook — `GET /kb/documents` | Auto-refetch on focus |
| 1.15 | `DocumentTable` component — shadcn `<Table>`, status badges | `PENDING / PROCESSING / READY / FAILED` colours |
| 1.16 | `UploadModal` — shadcn `<Dialog>` + `FileDropzone` | Drag & drop + browse, amber progress bar |
| 1.17 | `FileValidator` — client-side type + size ≤ 10MB | Shows error before hitting API |
| 1.18 | Wire upload: `POST /kb/documents` with `onUploadProgress` | Disabled Upload button until file selected |
| 1.19 | `KbPage` — replace M1 stub | Header + empty state + `DocumentTable` + `UploadModal` |
| 1.20 | Delete action — `DELETE /kb/documents/{id}` + inline confirm | Disabled while `PROCESSING` |

---

## Phase 2 — Async Processing Pipeline

**Goal:** Status machine + ingestion pipeline: text extraction → chunking → dedup → embed → store.  
**Done when:** Uploaded document transitions `PENDING → PROCESSING → READY`, FE polling reflects live status.

### BE

| # | Task | Notes |
|---|------|-------|
| 2.1 | Configure `@Async` thread pool (`TaskExecutor` bean, 4 threads) | Separate from web thread pool |
| 2.2 | `DocumentIngester` interface — `String read(InputStream)` | Pluggable ingestion contract |
| 2.3 | `PdfIngester` — Apache PDFBox text extraction | Handle encrypted PDF → throw with message |
| 2.4 | `TextIngester` — plain text / markdown UTF-8 read | |
| 2.5 | `IngestionRouter` — routes by `contentType` to correct ingester | `application/pdf` → `PdfIngester`, etc. |
| 2.6 | `TokenTextSplitter` — 500 token chunks, 50 token overlap | Use Spring AI `TokenTextSplitter` |
| 2.7 | `ContentHashFilter` — SHA-256 per chunk, skip existing hashes | Query `document_chunk.content_hash` |
| 2.8 | `DocumentChunk` entity + `DocumentChunkRepository` | `vector(1536)` field, `business_id` |
| 2.9 | `VectorStorage` — `PgVectorStore` adapter, stores chunks + embeddings | Spring AI `VectorStore` |
| 2.10 | `FullTextStorage` — raw INSERT with `to_tsvector` | `fts_vector` column |
| 2.11 | `DocumentProcessingService.processAsync()` — full pipeline orchestration | `@Async`, status transitions |
| 2.12 | Error handling in pipeline — catch exceptions → `UPDATE status=FAILED, error_message=...` | |
| 2.13 | OpenAI embedding config — `text-embedding-3-small`, env `OPENAI_API_KEY` | Spring AI `OpenAiEmbeddingModel` |
| 2.14 | Unit tests: `PdfIngester`, `TextIngester`, `TokenTextSplitter`, `ContentHashFilter` | |
| 2.15 | Integration test: end-to-end processing (real PG + pgvector) | Upload → assert `READY` + chunk rows |

### FE

| # | Task | Notes |
|---|------|-------|
| 2.16 | `useStatusPoller(docId)` hook — polls `GET /kb/documents/{id}` every 3s | Stops when `READY` or `FAILED`, invalidates list query |
| 2.17 | Wire poller into `DocumentRow` for `PROCESSING`/`PENDING` docs | Spinning ↻ icon on badge |
| 2.18 | Show error message row for `FAILED` docs (`bg-red-50`, truncated + tooltip) | |

---

## Phase 3 — RAG Search

**Goal:** Hybrid search (vector + FTS) API + FE search panel.  
**Done when:** User can type a query and see ranked results with content, score, and source document.

### BE

| # | Task | Notes |
|---|------|-------|
| 3.1 | `SearchResult` DTO — `chunkId`, `content`, `score`, `documentId`, `documentName`, `chunkIndex`, `source` | `source` enum: `VECTOR / FTS / BOTH` |
| 3.2 | `VectorStorage.search(query, topK, businessId)` — pgvector cosine similarity | `<=>` operator, filter by `business_id` |
| 3.3 | `FullTextStorage.search(query, businessId)` — `plainto_tsquery` | Filter by `business_id` |
| 3.4 | `HybridSearchService.search()` — run both in parallel, merge + dedup by `chunkId`, sort by score | Use `CompletableFuture.allOf` |
| 3.5 | `SearchController` — `POST /kb/search` | Validates `query` ≥ 3 chars, `topK` max 20, 422 if no READY docs |
| 3.6 | Unit tests: `HybridSearchService` merge/dedup logic | Mock both storage impls |
| 3.7 | Integration test: search returns correct chunks, tenant isolation | |

### FE

| # | Task | Notes |
|---|------|-------|
| 3.8 | `useSearch` hook — `POST /kb/search`, debounced | Only fires when `readyCount ≥ 1` |
| 3.9 | `SearchSection` — input + Button, hidden if no READY docs | Amber focus ring on input |
| 3.10 | `SearchResultItem` — content + score badge + source doc name | Score green if ≥ 0.7 |
| 3.11 | `EmptySearchState` — "No results for this query" | |
| 3.12 | Wire `SearchSection` into `KbPage` below document table | |

---

## Phase 4 — Deploy

**Goal:** Docker Compose updates + Render deploy + smoke test.  
**Done when:** M2 features work on the staging/prod URL with real MinIO (R2) and OpenAI embeddings.

| # | Task | Notes |
|---|------|-------|
| 4.1 | Add MinIO service to `docker-compose.yml` (dev) | Port 9000/9001, bucket auto-create |
| 4.2 | Add `MINIO_*` + `OPENAI_API_KEY` env vars to `.env.example` | Document required vars |
| 4.3 | Configure Cloudflare R2 as prod MinIO-compatible storage | Endpoint override via env |
| 4.4 | Render deploy config — add env vars to service | `OPENAI_API_KEY`, `MINIO_*` |
| 4.5 | pgvector extension — verify enabled on Render PostgreSQL | Run `CREATE EXTENSION` in migration, confirm |
| 4.6 | Smoke test on staging: upload PDF → wait READY → search → verify results | Manual + log check |

---

## Summary

| Phase | BE tasks | FE tasks | Total |
|---|---|---|---|
| 1 — Foundation & Upload | 13 | 7 | 20 |
| 2 — Async Processing | 15 | 3 | 18 |
| 3 — RAG Search | 7 | 5 | 12 |
| 4 — Deploy | 6 | — | 6 |
| **Total** | **41** | **15** | **56** |

**Estimated effort:** ~7 days (within M2 cap)  
**Delivery order:** Phase 1 → 2 → 3 → 4 (each phase is independently demoable)
