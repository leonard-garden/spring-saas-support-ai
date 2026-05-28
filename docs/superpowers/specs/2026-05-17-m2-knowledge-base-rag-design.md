# M2 Design — Knowledge Base + RAG Pipeline

**Date:** 2026-05-17
**Version:** v0.3
**Status:** Approved

---

## Milestone Goal

A business owner can upload company documents to a Knowledge Base, monitor processing status, and search through them semantically. The RAG pipeline is live end-to-end and ready for M3 AI Chat to consume.

---

## Scope

| In scope | Out of scope |
|----------|-------------|
| PDF + TXT/MD ingestion | Video, audio, CSV, URL scraping |
| Async processing pipeline (PENDING → PROCESSING → READY/FAILED) | Synchronous processing |
| Vector search + full-text search (hybrid retrieval) | BM25 weighting, re-ranking |
| Single KB per tenant (1:1 with Business) | Multiple KBs per tenant |
| MinIO (dev) + Cloudflare R2 (prod) file storage | AWS S3, local filesystem |
| Ollama (dev) + OpenAI (prod) embeddings | Other embedding providers |
| Content hash dedup, short-chunk filter | ML-based chunk quality scoring |
| POST /api/v1/kb/search for M3 consumption | Chat interface (M3) |
| Tenant isolation via Hibernate filter + metadata | — |

---

## Architecture

### Component Map

```
Frontend (React)
  KBPage → DocumentList + UploadButton + StatusPolling + SearchPanel
     ↕ REST API
Backend (Spring Boot)
  KnowledgeBaseController → KnowledgeBaseService
  DocumentController → DocumentService
    ├── IngestionRouter → DocumentIngester (PdfIngester / TextIngester)
    │     └── @Async DocumentProcessingService
    │           ├── MinioService (download raw file)
    │           ├── TokenTextSplitter (500 tokens, 50 overlap)
    │           ├── ContentHashFilter (SHA-256 dedup + short-chunk filter <50 tokens)
    │           └── EmbeddingModel → PgVectorStore (with tenantId metadata)
    └── SearchService → HybridSearchService
          ├── VectorStorage: PgVectorStore.similaritySearch(query, k=5)
          └── FullTextStorage: PostgreSQL tsvector query
               → merge + dedup → SearchResponse
Storage
  PostgreSQL: knowledge_base, document, document_chunk tables + pgvector extension
  MinIO (dev) / Cloudflare R2 (prod): raw uploaded files in bucket kb-{businessId}
```

### Tech Stack Additions

**Backend dependencies:**
- `spring-ai-openai-spring-boot-starter` — EmbeddingModel + VectorStore abstractions
- `spring-ai-pgvector-store-spring-boot-starter` — PgVector integration
- `pdfbox 3.x` — PDF text extraction (Apache, open-source)
- `aws-java-sdk-s3` or MinIO Java SDK — S3-compatible file storage

**Infrastructure (Docker Compose additions):**
- **MinIO** — S3-compatible object storage (port 9000/9001)
- **Ollama** — local embedding model: `nomic-embed-text` (dev only)
- **PostgreSQL**: enable `pgvector` extension via Flyway migration

**Embedding strategy:**
- Both dev and prod: `OpenAiEmbeddingModel` + `text-embedding-3-small` (1536 dim)
- Dev uses a separate OpenAI API key with a spending limit ($5 cap sufficient for dev)
- Ollama (`nomic-embed-text`) is available as an alternative but outputs 768 dim — incompatible with the `vector(1536)` schema without a separate migration; not the default
- Spring AI `EmbeddingModel` abstraction — swap via single config property, zero code change

### Multi-tenancy

Every `document_chunk` carries `business_id` in both the DB column and Spring AI metadata. The existing Hibernate filter from M1 scopes all queries automatically. MinIO uses per-tenant buckets: `kb-{businessId}`. Vector similarity search adds `business_id` as a metadata filter to prevent cross-tenant leakage.

---

## Data Model

### Flyway Migrations

**V13 — pgvector extension + knowledge_base table**
```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_base (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(business_id)  -- 1:1 with business, auto-created on first access
);
```

**V14 — document table**
```sql
CREATE TABLE document (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  knowledge_base_id UUID NOT NULL REFERENCES knowledge_base(id) ON DELETE CASCADE,
  business_id      UUID NOT NULL,      -- denormalized for Hibernate filter
  filename         VARCHAR(255) NOT NULL,
  content_type     VARCHAR(50) NOT NULL, -- application/pdf | text/plain | text/markdown
  minio_key        VARCHAR(500) NOT NULL, -- kb-{businessId}/{docId}/{filename}
  status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  size_bytes       BIGINT,
  chunk_count      INT DEFAULT 0,
  error_message    TEXT,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**DocumentStatus enum:** `PENDING → PROCESSING → READY | FAILED`

**V15 — document_chunk table**
```sql
CREATE TABLE document_chunk (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id   UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
  business_id   UUID NOT NULL,
  content       TEXT NOT NULL,
  chunk_index   INT NOT NULL,
  token_count   INT,
  content_hash  VARCHAR(64) NOT NULL,  -- SHA-256 for dedup
  embedding     vector(1536),          -- fixed at 1536 to match text-embedding-3-small output
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(content_hash, business_id)    -- dedup per tenant at DB level
);

-- Vector similarity index
CREATE INDEX ON document_chunk USING ivfflat (embedding vector_cosine_ops);
-- Full-text search index
CREATE INDEX ON document_chunk USING gin (to_tsvector('english', content));
```

---

## API Design

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/kb` | Get KB info for tenant (KB created at business signup, never lazy) | Any role |
| POST | `/api/v1/kb/documents` | Upload document (multipart/form-data) → returns doc ID + status PENDING | ADMIN only |
| GET | `/api/v1/kb/documents` | List all documents for tenant with status | Any role |
| GET | `/api/v1/kb/documents/{id}` | Get document detail + status (used for polling) | Any role |
| DELETE | `/api/v1/kb/documents/{id}` | Delete document + chunks from PgVector + file from MinIO | ADMIN only |
| POST | `/api/v1/kb/search` | RAG hybrid search, returns top-k chunks. Consumed by M3. | Any role |

**POST /api/v1/kb/documents response:**
```json
{
  "id": "uuid",
  "filename": "product-guide.pdf",
  "status": "PENDING",
  "sizeBytes": 204800,
  "createdAt": "2026-05-17T10:00:00Z"
}
```

**POST /api/v1/kb/search:**
```json
// Request
{ "query": "refund policy", "topK": 5 }

// Response
{
  "results": [
    {
      "content": "Refunds are processed within 5 business days...",
      "score": 0.92,
      "documentName": "faq.pdf",
      "chunkIndex": 3
    }
  ]
}
```

---

## Processing Pipeline (Ingestion)

Triggered async after upload. Steps in `DocumentProcessingService`:

1. Download file from MinIO using `minio_key`
2. `IngestionRouter.route(contentType)` → selects `PdfIngester` or `TextIngester`
3. `DocumentIngester.read(inputStream)` → raw text string
4. `TokenTextSplitter.split(text, chunkSize=500, overlap=50)` → `List<String>`
5. Filter out chunks with `tokenCount < 50` (headers, page numbers, footers)
6. For each chunk: compute `SHA-256(content)` — skip if `UNIQUE(hash, business_id)` already exists
7. Inject `business_id` + `document_id` into Spring AI `Document` metadata
8. `EmbeddingModel.embed(chunks)` → `float[]` vectors (batch call)
9. `PgVectorStore.add(documents)` → persists chunks + embeddings
10. Update document `status = READY`, set `chunk_count`
11. On any exception: update `status = FAILED`, set `error_message`

### Key Abstractions

```java
// Layer 1 — content extraction
interface DocumentIngester {
    List<String> read(InputStream stream, String filename);
}
// Implementations: PdfIngester (PDFBox), TextIngester

// Layer 2 — storage + retrieval
interface StorageStrategy {
    List<SearchResult> search(String query, int topK, UUID businessId);
}
// Implementations: VectorStorage (PgVector), FullTextStorage (tsvector)
```

Adding `VideoIngester` or `CsvIngester` in M4 requires only a new class — no changes to existing code.

### Hybrid Search

`HybridSearchService` calls `VectorStorage` and `FullTextStorage` in parallel, merges results by deduplicating on chunk ID, and sorts by score descending.

---

## Phase Breakdown (7-day cap)

### Phase 1: Foundation + Document Upload (Day 1–2)

**Goal:** User can upload a file and see it in the document list with status PENDING.

**BE:**
- Flyway V13/V14/V15 migrations
- Docker Compose: add MinIO + Ollama services
- pom.xml: Spring AI, PDFBox, MinIO deps
- `KnowledgeBase` entity + created during business signup (extend existing AuthServiceImpl.signup())
- `Document` entity + `DocumentStatus` enum
- `MinioService` (upload, delete, presigned URL)
- `POST /api/v1/kb/documents` (multipart → MinIO storage)
- `GET /api/v1/kb/documents` (list)
- `DELETE /api/v1/kb/documents/{id}` (doc + MinIO file)

**FE:**
- KB page (replaces M1 stub)
- Document list table: filename, size, status badge, delete action
- Upload button + file picker (PDF/TXT filter, 10MB max)
- Empty state when no documents

### Phase 2: Processing Pipeline (Day 3–4)

**Goal:** Uploaded document automatically processes through to READY status without user action.

**BE:**
- `DocumentIngester` interface + `PdfIngester` (PDFBox) + `TextIngester`
- `IngestionRouter` (routes by `content_type`)
- `@Async DocumentProcessingService` (full pipeline steps 1–11)
- `TokenTextSplitter` (500 tokens, 50 overlap)
- `ContentHashFilter` (SHA-256 dedup + short-chunk filter)
- `EmbeddingModel` config: `OllamaEmbeddingModel` (dev) / `OpenAiEmbeddingModel` (prod)
- `PgVectorStore.add()` with `business_id` metadata

**FE:**
- Status polling: `GET /kb/documents/{id}` every 3s while PROCESSING
- Spinner + "Processing..." badge during processing
- READY → show chunk count ("47 chunks indexed")
- FAILED → show error message + "Delete and re-upload" guidance (no automatic retry)

### Phase 3: RAG Search API + Test UI (Day 5)

**Goal:** Search endpoint working with hybrid retrieval; FE has a test panel; M3 can consume the API immediately.

**BE:**
- `StorageStrategy` interface + `VectorStorage` + `FullTextStorage`
- `HybridSearchService`: parallel search + merge + dedup + sort by score
- `POST /api/v1/kb/search` — `SearchRequest` / `SearchResponse`
- Tenant isolation filter in metadata on vector search

**FE:**
- Search panel in KB page (visible only when ≥1 READY document exists)
- Query input + Search button
- Results list: chunk content + score + source document name
- Empty state when no results

### Phase 4: Deploy + Polish (Day 6–7)

**Goal:** Full pipeline running on Render; end-to-end demo verified.

**Infra:**
- Cloudflare R2 (free 10GB/month) replacing MinIO for prod
- Render env vars: `OPENAI_API_KEY`, R2 credentials (`R2_ENDPOINT`, `R2_ACCESS_KEY`, `R2_SECRET_KEY`, `R2_BUCKET`)
- `render.yaml` update for backend service
- Enable `pgvector` extension on Render Postgres

**BE:**
- `application-prod.yml`: MinIO → R2, Ollama → OpenAI
- File size limit: 10MB max (configurable via `app.kb.max-file-size`)

**FE + Smoke Test:**
- Loading skeletons + toast notifications for upload/delete/error
- Client-side file size validation before upload
- Demo flow: sign up → upload PDF → wait READY → search → verify results match

---

## Testing

### Unit Tests

| Component | Cases |
|-----------|-------|
| `PdfIngester` | Parse PDF → non-empty text |
| `TextIngester` | Parse TXT/MD → content preserved |
| `IngestionRouter` | `application/pdf` → `PdfIngester`; unknown type → `UnsupportedDocumentTypeException` |
| `TokenTextSplitter` | 1000-token text → ≥2 chunks with overlap |
| `ContentHashFilter` | Chunk <50 tokens → filtered; duplicate hash → skipped; new hash → passes |

### Integration Tests

| Component | Cases |
|-----------|-------|
| `DocumentProcessingService` | Upload TXT → status READY; `chunk_count > 0`; corrupt file → status FAILED |
| `DocumentProcessingService` | Controller returns PENDING immediately (@Async not blocking) |
| `HybridSearchService` | Relevant query → ≥1 result; irrelevant query → empty list, no exception |
| `MinioService` | Upload → object exists; delete → object removed |

### Tenant Isolation Integration Tests (mandatory, 100% coverage)

- 2 tenants upload same filename → no conflict
- `GET /kb/documents` for tenant A returns only tenant A's docs
- Search for tenant A returns only tenant A's chunks
- `DELETE /kb/documents/{id}` by tenant A on tenant B's doc → 403

### Coverage Targets

- Service layer: 60%+ (consistent with M1 project constraints)
- Tenant isolation IT: 100%
- E2E: manual smoke test on Render (no Playwright for M2)

---

## Success Criteria

M2 is done when all of the following are true:

- [ ] **DOC-01**: ADMIN user uploads PDF/TXT → document appears in list with status PENDING
- [ ] **DOC-02**: Document transitions PENDING → PROCESSING → READY automatically (no page refresh needed)
- [ ] **DOC-03**: READY document shows correct `chunk_count`
- [ ] **DOC-04**: Corrupt or unparseable file → status FAILED with readable error message
- [ ] **DOC-05**: ADMIN user deletes document → removed from list, chunks deleted from PgVector, file deleted from MinIO/R2
- [ ] **DOC-06**: Uploading same file twice → `chunk_count` does not double (dedup working)
- [ ] **SRCH-01**: Search with relevant query → returns results with score > 0.7
- [ ] **SRCH-02**: Search with irrelevant query → returns empty list, no error
- [ ] **ISO-01**: Tenant A's search results contain no content belonging to Tenant B
- [ ] **DEP-01**: Demo flow on Render: upload PDF → READY → search → correct results

---

## Key Decisions

| Decision | Rationale |
|----------|-----------|
| Single KB per tenant | Simpler model, natural for support chatbot use case, avoids KB selector in M3 |
| One combined milestone (FE+BE per phase) | Vertical slice delivery — each phase is demoable |
| Async processing with polling | Better UX for large PDFs; aligns with existing @Async pattern from email service |
| MinIO (dev) + Cloudflare R2 (prod) | Open-source local dev, free production tier, S3-compatible (zero code change) |
| Ollama (dev) + OpenAI (prod) | Free local embeddings for dev, Spring AI abstraction makes swap trivial |
| `DocumentIngester` + `StorageStrategy` interfaces | Open/Closed: adding Video/CSV support in M4 requires only new classes |
| Content hash dedup at DB level | UNIQUE constraint catches duplicates even under concurrent uploads |
| Hybrid retrieval (vector + FTS) | Better recall, lower vector API cost — keyword search is free |

---

*Spec written: 2026-05-17*
*Brainstormed from: /superpowers:brainstorming*
