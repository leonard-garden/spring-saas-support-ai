# Phân tích Thiết kế Hệ thống — spring-saas-support-ai

> **Ngày phân tích:** 2026-05-06  
> **Scope:** PROJECT_SPEC.md — toàn bộ thiết kế hệ thống  
> **Mục tiêu:** Đánh giá chất lượng thiết kế, phát hiện rủi ro, và đưa ra khuyến nghị ưu tiên trước khi bắt đầu code

---

## Mục lục

1. [Kiến trúc tổng thể & Multi-tenancy](#1-kiến-trúc-tổng-thể--multi-tenancy)
2. [Domain Model & Entity Relationships](#2-domain-model--entity-relationships)
3. [API Design](#3-api-design)
4. [Tech Stack & Dependencies](#4-tech-stack--dependencies)
5. [RAG Pipeline & AI Integration](#5-rag-pipeline--ai-integration)
6. [Security & Billing Design](#6-security--billing-design)
7. [Tổng hợp: Điểm mạnh, Rủi ro & Khuyến nghị](#7-tổng-hợp-điểm-mạnh-rủi-ro--khuyến-nghị)
8. [Open Questions — Cần quyết định trước khi code](#8-open-questions--cần-quyết-định-trước-khi-code)
9. [Missing Acceptance Criteria](#9-missing-acceptance-criteria)

---

## 1. Kiến trúc tổng thể & Multi-tenancy

### Điểm mạnh

- Row-level isolation là lựa chọn đúng cho scale và budget MVP. Schema-per-tenant hay DB-per-tenant adds operational complexity không cần thiết ở giai đoạn này.
- Hibernate filter tập trung tenant predicate thay vì scatter `WHERE tenant_id = ?` khắp nơi — dễ maintain, khó bỏ sót.
- `TenantContextCopyingDecorator` cho `@Async` là pattern chuẩn, xử lý đúng propagation.
- Monolith-first là lựa chọn phù hợp với timeline 28 ngày và team 1 người.
- Package-per-feature structure giữ door open cho later extraction nếu cần.

### Rủi ro / Issues

| Severity | Issue |
|----------|-------|
| **HIGH** | **ThreadLocal + Virtual Threads incompatibility.** Java 21 có virtual threads. Nếu `spring.threads.virtual.enabled=true`, ThreadLocal vẫn hoạt động per-virtual-thread (values được preserve qua yield/resume), nhưng: (a) memory pressure khi hàng nghìn virtual threads tồn tại đồng thời, mỗi cái giữ ThreadLocal map riêng; (b) ThreadLocal không tự propagate sang child virtual threads — async code dùng `CompletableFuture.supplyAsync()` mà không qua `TenantContextCopyingDecorator` sẽ mất context silently. `ScopedValue` (JEP 446, preview Java 21) là replacement đúng đắn cho VTs nhưng chưa stable. Spec không clarify virtual threads có được bật không. |
| **HIGH** | **Hibernate filter bypass vectors.** Filter có thể bị bypass bởi: (a) `@Query(nativeQuery=true)` không include `tenant_id`; (b) `EntityManager.createNativeQuery()`; (c) Flyway migrations; (d) Session được mở mà không enable filter. Không có compile-time mechanism để detect violations. |
| **MEDIUM** | **Super-admin bypass mechanism undefined.** `GET /api/v1/admin/businesses` cần cross-tenant query nhưng Hibernate filter block tất cả. Cơ chế bypass chưa được define: separate admin repository layer? Disable filter per session? Native queries? Mỗi cách có security implications khác nhau. |
| **MEDIUM** | **TenantContextCopyingDecorator không clear context sau khi task hoàn thành.** Nếu async error handler không clear, ThreadLocal leak sang task tiếp theo trên cùng thread. |
| **LOW** | **HikariCP pool sizing không được mention.** Với async document processing giữ connection trong thời gian dài, pool có thể bị exhaust bởi async tasks, starving synchronous API requests. |

### Khuyến nghị

- **Ngay lập tức:** Explicitly set `spring.threads.virtual.enabled=false` cho M1. Ghi TODO để evaluate `ScopedValue` (JEP 446) khi cần virtual threads.
- Thêm `ArchitectureTest` với ArchUnit: mọi `@Entity` có `tenant_id` phải có `@Filter` annotation; mọi `@Query(nativeQuery=true)` phải reference `tenant_id`.
- `TenantContextCopyingDecorator` phải wrap task trong try/finally và clear context sau khi chạy.
- Define super-admin context mechanism: khuyến nghị dùng dedicated `AdminRepository` layer disable filter explicitly + audit log mọi call.
- Set HikariCP `maximumPoolSize` = executor `maxPoolSize` + core size + buffer (ví dụ: 22+).

---

## 2. Domain Model & Entity Relationships

### Điểm mạnh

- Model well-normalized, cover đầy đủ business domain mà không over-engineer.
- `session_id` trên Conversation xử lý anonymous widget users sạch sẽ.
- Document status state machine (PENDING→PROCESSING→INDEXED→FAILED) explicit và trackable.
- `sources` JSON trên Message với cited chunk_ids + relevance scores hỗ trợ explainability — strong differentiator so với competitor.
- `UsageRecord` partitioned by `period (YYYY-MM)` clean cho billing queries.

### Rủi ro / Issues

| Severity | Issue |
|----------|-------|
| **HIGH** | **N+1 query risks.** Spec không define fetch strategies. JPA default LAZY loading sẽ gây N+1 trên: `Chatbot.conversations`, `Document.chunks`, `Business.members`, `Conversation.messages`. Conversation history endpoint (called từ public widget) sẽ bị ảnh hưởng đặc biệt nặng. |
| **HIGH** | **Vector index strategy unspecified.** `Chunk.embedding vector(1536)` nhưng không nói index type (HNSW vs IVFFlat), parameters (`m`, `ef_construction`, `ef_search`). Default PgVector settings có thể insufficient cho production quality. HNSW là đúng cho growing multi-tenant data. |
| **HIGH** | **UsageRecord race condition.** Multiple concurrent requests cùng increment counter: read count=999, cả hai pass check, increment → count=1001. Cần atomic SQL operation. |
| **MEDIUM** | **Cascade delete risk.** Xóa KB cascades xuống Documents → Chunks (thousands of rows). Long-running transaction, potential timeout, blocks other operations. Không define sync vs async, soft-delete vs hard-delete. |
| **MEDIUM** | **Anonymous conversation cleanup undefined.** `session_id` conversations tích lũy vô hạn. Không có TTL, cleanup mechanism, hay storage impact estimation. |
| **MEDIUM** | **Missing composite indexes.** Cần thêm: `(tenant_id, status)` trên Documents; `(chatbot_id, session_id)` trên Conversations; `(business_id, period, metric)` trên UsageRecord; `(business_id, created_at)` trên AuditLog. |
| **MEDIUM** | **Chunk metadata schema không đủ.** `metadata` field generic. Cần structured: `source_document_id`, `page_number`, `section_heading`, `position`, `chunk_type (paragraph/table/code/list)`. |
| **LOW** | **ApiKey thiếu scope/permission model.** API key hiện tại = full tenant access. Cần scope field (READ_ONLY, CHAT_ONLY, FULL). |
| **LOW** | **Plan.limits dạng JSON mất type safety.** Malformed limits JSON silently break quota enforcement. Nên dùng explicit columns. |

### Khuyến nghị

- Dùng `@EntityGraph` hoặc DTO projections (Java records) cho tất cả collection relationships trong API responses. Không return full entity graphs.
- HNSW index: `CREATE INDEX ON chunks USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 100)` trong Flyway migration.
- UsageRecord atomic: `UPDATE usage_records SET value = value + 1 WHERE business_id = ? AND period = ? AND metric = ?`. Nếu row không tồn tại, dùng upsert (`INSERT ... ON CONFLICT DO UPDATE`).
- KB deletion: mark status `DELETING`, return 202 Accepted, cascade delete trong background job.
- Thêm scheduled job cleanup anonymous conversations > 7 ngày. TTL configurable per chatbot.
- Add tất cả composite indexes trong initial Flyway migrations.

---

## 3. API Design

### Điểm mạnh

- Clean REST resource naming theo standard conventions.
- Separation of public endpoints (`/chat/{chatbotId}`) từ authenticated endpoints rõ ràng.
- SSE cho chat streaming là lựa chọn đúng: simpler than WebSocket, Spring AI native support, `EventSource` browser API.
- Feedback endpoint (`/chat/{chatbotId}/feedback`) cho phép data collection cho future improvement.

### Rủi ro / Issues

| Severity | Issue |
|----------|-------|
| **HIGH** | **JWT revocation gap.** Access tokens (15min) không revocable — chỉ có signature validation. Khi suspend business hoặc remove member, user vẫn có access thêm 15 phút. Logout chỉ revoke refresh token. |
| **HIGH** | **Public chat endpoint không enforce `allowed_domains`.** `Widget.allowed_domains` field tồn tại nhưng không có middleware nào check `Origin`/`Referer` header. CORS alone không đủ (bypass được từ server-to-server). |
| **HIGH** | **Admin impersonate endpoint thiếu safeguards.** Không define: impersonation token format, timeout, read-only vs full access, audit trail, nesting prevention. Đây là high-risk endpoint. |
| **MEDIUM** | **Rate limiting granularity undefined.** Bucket4j được mention nhưng không define: endpoints nào bị limit, key strategy (per tenant/IP/chatbotId), giá trị per plan tier, distributed vs in-memory. |
| **MEDIUM** | **No document processing status streaming.** Async pipeline cần polling. Không có SSE/webhook cho document status updates. |
| **LOW** | **API versioning migration strategy undefined.** `/api/v1/` prefix tạo implicit promise nhưng không có migration strategy. |
| **LOW** | **Health endpoint detail undefined.** Actuator `/health` cần define: which indicators (DB, Redis, external APIs), security (public vs secured), readiness probe cho Render. |

### Khuyến nghị

- JWT revocation: implement lightweight blocklist với Redis. Store revoked `jti` với TTL = remaining access token lifetime (max 15min). Check blocklist mỗi request (1 Redis lookup).
- Origin validation: so sánh `Origin` header với `Widget.allowed_domains` trong dedicated filter. Return 403 nếu không match.
- Impersonation rules: (a) `impersonator_id` claim trong JWT; (b) read-only by default; (c) audit log cả admin_id và impersonated tenant_id; (d) max 1 giờ TTL; (e) không cho nest.
- Stripe webhook: tạo `stripe_events` table với `event_id` unique constraint. Insert before process — duplicate key exception = skip (idempotent). Always verify `Stripe-Signature`.
- Rate limiting: dùng composite key `chatbotId + clientIP`, limits: Free=10/min, Starter=30/min, Pro=60/min, Business=120/min. Return 429 với `Retry-After`.

---

## 4. Tech Stack & Dependencies

### Điểm mạnh

- Java 21 + Spring Boot 3.3: LTS support đến 2031, records/sealed interfaces giúp loại bỏ Lombok, virtual threads capability.
- PostgreSQL cho "everything" (relational + vector + full-text) giảm operational complexity đáng kể cho MVP.
- Testcontainers thay H2: test behavior sat với production — quyết định đúng.
- Không dùng Kafka/RabbitMQ/ElasticSearch: discipline tốt, tránh over-engineering.

### Rủi ro / Issues

| Severity | Issue |
|----------|-------|
| **HIGH** | **Spring AI 1.1+ production maturity.** Spring AI vẫn trong rapid evolution. Breaking changes giữa minor versions là có thực (Advisors, ChatClient, VectorStore interfaces đã thay đổi). Không có long-term stability guarantee như Spring Boot core. |
| **HIGH** | **Render/Railway free tier constraints.** Cold start 30-60 giây (spin down sau 15 phút idle). RAM limit 512MB. PostgreSQL free tier: 1GB storage, 97 ngày expiry (Render). Spring Boot + PgVector + JPA footprint ~350MB. PostgreSQL connection limit ~20 với HikariCP default pool = 10. |
| **MEDIUM** | **@Async document processing không có persistence.** Nếu app restart giữa lúc processing, in-flight jobs mất hoàn toàn — không recovery mechanism. |
| **MEDIUM** | **Testcontainers CI startup time.** Mỗi IT class start PostgreSQL container ~5-10 giây. 20+ IT classes = 2-3 phút chỉ cho container startup nếu không có singleton pattern. |
| **LOW** | **Document file storage không được mention.** Upload PDF rồi lưu ở đâu? Local filesystem fail khi redeploy trên Render. |

### Khuyến nghị

- Pin Spring AI version chính xác trong `pom.xml` (ví dụ `1.1.0`). Viết integration test cho Spring AI interactions để detect breaking changes sớm.
- Render: set HikariCP `maximumPoolSize=5`, `spring.jpa.open-in-view=false`. Có backup plan cho PostgreSQL 97-ngày expiry.
- **Trước M2:** Chọn document file storage: khuyến nghị Cloudflare R2 (free tier 10GB) hoặc AWS S3. Tuyệt đối không lưu local filesystem.
- Thêm `ProcessingJob` table để persist async job state. Khi app restart, re-queue PROCESSING jobs. Tenant-level concurrency limit (max 3 jobs/tenant).
- Testcontainers: dùng abstract base class với static `@Container` chia sẻ 1 PostgreSQL instance across all IT tests.

---

## 5. RAG Pipeline & AI Integration

### Điểm mạnh

- Hybrid search (vector + keyword + RRF) là state-of-the-art, tốt hơn vector-only — keyword search bắt được exact matches mà semantic search miss.
- `text-embedding-3-small` (1536 dims): $0.02/1M tokens, cost-effective, quality gần bằng ada-002 nhưng rẻ hơn 5x.
- Paragraph-aware chunking với overlap: đúng approach để giữ context coherence.
- Skip re-ranking trong MVP: đúng quyết định — complexity cao, benefit marginal khi corpus nhỏ.
- Source citation trong responses (cited chunk_ids + relevance scores): strong feature cho trust và transparency.

### Rủi ro / Issues

| Severity | Issue |
|----------|-------|
| **HIGH** | **Chunking không xử lý structured content.** "1000 tokens, paragraph-aware" không define cách xử lý: tables (bị split giữa rows), code blocks (bị cắt giữa function), nested lists (mất hierarchy), headers (mất section context). Đây là nguồn lỗi #1 của RAG systems — câu trả lời sai về pricing/features khi chunk cắt giữa bảng dữ liệu. |
| **HIGH** | **Token budget management chưa được define.** Top-5 × 1000 tokens = 5000 tokens context. Cộng system prompt + conversation history (tăng dần). Không có truncation/summarization strategy. Sau 10-15 messages, context vượt reasonable limit và tăng cost đáng kể. |
| **HIGH** | **Document processing failure strategy cụ thể còn thiếu.** Retry bao nhiêu lần? Backoff strategy? Khi nào vĩnh viễn FAILED? OpenAI embedding API rate limit (3500 RPM free tier) có thể gây cascade failures. Không có notification khi document FAILED. |
| **MEDIUM** | **RRF implementation không có Spring AI built-in support.** Phải tự implement: 2 queries riêng, merge bằng formula `1/(k + rank)`, de-duplicate, sort. Cần test kỹ — sai parameter `k` ảnh hưởng lớn đến kết quả. |
| **MEDIUM** | **Apache Tika limitations với complex PDFs.** Scanned PDFs cần OCR (chậm, không chính xác). PDF tables bị flatten. Multi-column layouts bị garbled. Không có file size limit — PDF 500 trang sẽ cực kỳ chậm. |
| **MEDIUM** | **Cost tracking accuracy undefined.** Cần clarify: dùng provider-reported token counts hay tự đếm? Approximation có thể sai 10-20%. |
| **LOW** | **Single embedding provider dependency.** Nếu OpenAI thay đổi pricing hay deprecate model, phải re-embed toàn bộ corpus. Không có abstraction layer để swap. |

### Khuyến nghị

- **Structure-aware chunking:** Detect tables (giữ nguyên bảng), code blocks (giữ nguyên block), headers (prepend header text vào chunk con). Thêm metadata `chunk_type: paragraph|table|code|list`.
- **Token budget:** Hard limit 6000 tokens cho retrieved context. Summarize conversation sau 8 messages (keep recent 4 verbatim). Log actual token usage per request.
- **Document processing:** Max 3 retries với exponential backoff (1s, 4s, 16s). File size limit: 10MB per file, 100 pages per PDF. Email/in-app notification khi document FAILED. Thêm `retry_count` và `last_error` fields vào Document entity.
- **RRF:** Hardcode `k=60` (standard value), viết unit tests với known inputs/outputs. Fallback: nếu keyword search empty, return vector-only results.
- **Cost tracking:** Luôn dùng provider-reported `usage` từ API response. Store raw token counts riêng, tính cost dựa trên config table (dễ update khi giá thay đổi).

---

## 6. Security & Billing Design

### Điểm mạnh

- Refresh token stored as hash in DB cho phép revocation — pattern đúng.
- Audit logging cho sensitive actions từ đầu — tốt.
- Stripe Checkout + Customer Portal giảm code phải viết, giảm PCI scope.
- 14-day trial không cần credit card: conversion-friendly.
- Rate limiting với Bucket4j: lightweight, tích hợp tốt với Spring.

### Rủi ro / Issues

| Severity | Issue |
|----------|-------|
| **HIGH** | **HS256 cho API access không phù hợp.** HS256 là symmetric — ai có key đều có thể tạo và verify token. Business plan có "API access" nhưng nếu dùng HS256, phải share secret key với external consumers. RS256 (asymmetric) là đúng cho API access — publish public key, consumer tự verify. Spec nói "HS256 or RS256" nhưng không quyết định. |
| **HIGH** | **Stripe webhook thiếu idempotency.** Stripe retry khi không nhận 2xx. `invoice.payment_succeeded` xử lý 2 lần → extend subscription 2 lần. Cần `stripe_events` table với `event_id` unique constraint. |
| **HIGH** | **Quota enforcement race condition.** 2 requests concurrent cho tenant gần limit (999/1000) đều pass check → count = 1001. Cần atomic SQL `UPDATE ... WHERE value < :limit`. |
| **HIGH** | **Plan downgrade behavior undefined.** Tenant downgrade từ Pro (10 KBs) xuống Starter (3 KBs) — 7 KBs extra ở đâu? Auto-delete? Read-only? Grace period? Critical UX decision ảnh hưởng database schema. |
| **MEDIUM** | **Refresh token hash algorithm không specified.** BCrypt quá chậm cho token lookup (150ms+). SHA-256 phù hợp hơn (nhanh, deterministic, có thể index). |
| **MEDIUM** | **Trial-to-paid notification flow incomplete.** Stripe có `customer.subscription.trial_will_end` event (3 ngày trước) nhưng chỉ fire 1 lần — nếu miss webhook, user mất access không báo trước. |
| **MEDIUM** | **CORS enforcement cho widget chỉ là browser-level.** `allowed_domains` cần server-side Origin validation — CORS chỉ protect browser requests, server-to-server bypass được. |
| **MEDIUM** | **Rate limiting distributed không được plan.** Bucket4j in-memory chỉ work với 1 instance. Scale lên 2+ instances, rate limit bị chia đôi. |
| **LOW** | **Admin impersonate session isolation.** Actions trong impersonated session cần tagged với cả admin_id và tenant_id trong audit log. |

### Khuyến nghị

- **JWT + API access:** Dùng HS256 cho web app JWT (internal). Tạo separate `ApiKey` system cho external API access (random key, hashed in DB). Không cần RS256.
- **Stripe webhook:** `stripe_events` table với `event_id` unique constraint. INSERT before process — duplicate key = skip. Always verify `Stripe-Signature` với `Webhook.constructEvent()`.
- **Quota enforcement atomic:** `UPDATE ... WHERE business_id = ? AND period = ? AND metric = ? AND value < :limit`. 0 rows updated = reject request. Không cần Redis.
- **Plan downgrade: "Soft freeze"** — excess resources chuyển sang `FROZEN` status (read-only, không nhận messages, không index docs mới). Hiển thị banner "Upgrade to access frozen resources". KHÔNG auto-delete user data.
- **Refresh token:** Dùng SHA-256 (`MessageDigest.isEqual()` để constant-time comparison). Store indexed trong DB.
- **Trial notification:** Handle `customer.subscription.trial_will_end` webhook + daily cron job backup check `trial_ends_at` trong DB.
- **Impersonation:** `impersonator_id` claim trong JWT, TTL 15 phút max, block billing/delete operations, log category `IMPERSONATION`.

---

## 7. Tổng hợp: Điểm mạnh, Rủi ro & Khuyến nghị

### Top 5 Điểm mạnh của Thiết kế

1. **Scope discipline xuất sắc.** Tech stack decisions "không thêm Kafka, không microservices, không ElasticSearch" là engineering maturity hiếm có. Mọi rejection đều có reason cụ thể.

2. **Domain model phong phú và đúng.** Entity relationships well-thought-out, cover đầy đủ business domain. `sources` JSON với cited chunks là differentiator quan trọng.

3. **Hybrid RAG search (vector + keyword + RRF).** Không phải vector-only (yếu), không phải keyword-only (cũ). State-of-the-art approach đúng cho customer support use case.

4. **Multi-tenancy row-level isolation với Hibernate filter.** Pragmatic, cost-effective, và đúng cho scale MVP. Hibernate filter approach centralize tenant predicate tốt hơn scattered WHERE clauses.

5. **4-milestone incremental delivery strategy.** Mỗi milestone deployable và marketable independently. Tạo portfolio entries, feedback loops, và motivation checkpoints đúng lúc.

### Top 5 Rủi ro Cần Xử lý

1. **[CRITICAL] ThreadLocal + Virtual Threads.** Cần quyết định ngay trước M1: explicitly disable virtual threads.

2. **[CRITICAL] Document file storage chưa được define.** Local filesystem sẽ fail khi redeploy trên Render. Cần clarify trước M2.

3. **[HIGH] Chunking cho structured content (tables, code).** Nguồn lỗi #1 của RAG quality. Cần design trước khi implement M2.

4. **[HIGH] Stripe webhook idempotency.** Missing idempotency có thể cause double-billing. Cần design `stripe_events` table trước M4.

5. **[HIGH] Plan downgrade behavior.** Undefined behavior cho "excess resources after downgrade" ảnh hưởng cả schema design lẫn billing UX. Cần decide trước M4.

### Khuyến nghị Ưu tiên theo Milestone

#### Trước M1 (Ngay bây giờ)

- [ ] **Quyết định virtual threads:** Set `spring.threads.virtual.enabled=false`. Document lý do.
- [ ] **Define super-admin context mechanism:** Separate `AdminRepository` layer với filter disabled.
- [ ] **ArchUnit test cho tenant isolation:** Mọi native query phải reference `tenant_id`.
- [ ] **Add composite indexes** vào initial Flyway migrations (không để sau).
- [ ] **Clarify email uniqueness:** Global unique hay chỉ unique per business?

#### Trước M2

- [ ] **Chọn document file storage:** Cloudflare R2 (free 10GB) hoặc AWS S3.
- [ ] **Design structure-aware chunking:** Spec out handling cho tables, code blocks, lists.
- [ ] **Thêm `ProcessingJob` table** để persist async job state.
- [ ] **Define file size limits:** 10MB per file, 100 pages per PDF.

#### Trước M3

- [ ] **Design conversation token budget management:** Hard limit context, summarize strategy.
- [ ] **Define anonymous session TTL:** Khuyến nghị 7 ngày.
- [ ] **Implement origin validation** cho public chat endpoint.

#### Trước M4

- [ ] **Decide plan downgrade behavior:** Khuyến nghị "soft freeze".
- [ ] **Design Stripe webhook idempotency:** `stripe_events` table.
- [ ] **Implement JWT revocation** cho suspend business use case (Redis blocklist).
- [ ] **Rate limiting per tier:** Define giá trị cụ thể per plan.

### Đánh giá Tính khả thi của 28-ngày Roadmap

**Verdict: Ambitious nhưng feasible với scope discipline nghiêm ngặt.**

| Milestone | Risk | Điều cần lưu ý |
|-----------|------|----------------|
| M1 (7 ngày) | **Thấp** | Core auth + multi-tenancy — well-understood domain, clear spec |
| M2 (7 ngày) | **Cao** | RAG pipeline phức tạp hơn expected. Apache Tika + PgVector setup có thể mất 2-3 ngày. Hybrid search RRF cần test kỹ. |
| M3 (7 ngày) | **Trung bình** | SSE streaming + embeddable widget vanilla JS — phần widget CSS isolation có thể tricky |
| M4 (7 ngày) | **Cao** | Stripe integration + quota enforcement + rate limiting — nhiều moving parts, cần test end-to-end |

**Recommendation:** Nếu M2 bị chậm (rất có khả năng), prioritize hybrid search over perfect chunking. Ship working vector-only search trước, add keyword/RRF sau.

---

## 8. Open Questions — Cần Quyết định Trước khi Code

| # | Question | Milestone Cần Quyết định | Khuyến nghị |
|---|----------|--------------------------|-------------|
| 1 | Virtual threads: enabled hay disabled? | Trước M1 | Disabled — dùng `spring.threads.virtual.enabled=false` |
| 2 | Super-admin bypass Hibernate filter như thế nào? | Trước M1 | Separate AdminRepository layer, disable filter per session |
| 3 | Email address globally unique hay only per business? | Trước M1 | Global unique — một người có thể own nhiều business nhưng cùng một email, cần UI support |
| 4 | Document file storage: local/S3/R2? | Trước M2 | Cloudflare R2 free tier (10GB) hoặc AWS S3 |
| 5 | Chunking strategy cho tables và code blocks? | Trước M2 | Structure-aware splitter, giữ nguyên tables, prefix headers vào chunks |
| 6 | Plan downgrade behavior: freeze/delete/grace period? | Trước M4 | Soft freeze — read-only excess resources, không auto-delete |
| 7 | HS256 hay RS256 hay separate ApiKey cho API access? | Trước M4 | Separate ApiKey system — không cần RS256 |
| 8 | Anonymous session_id: client-generated hay server-generated? | Trước M3 | Server-generated token với expiry — tránh spoof |
| 9 | Conversation TTL cho anonymous sessions? | Trước M3 | 7 ngày, configurable per chatbot |
| 10 | PgVector extension có available trên Render free tier? | Trước M2 | **Cần verify ngay** — nếu không có, switch sang Supabase/Neon |

---

## 9. Missing Acceptance Criteria

Các test cases quan trọng chưa được mention trong spec:

1. **Tenant isolation exhaustive test:** Member của Business A không thể access dữ liệu của Business B qua BẤT KỲ endpoint nào — parameterized test covering every repository type.

2. **Concurrent usage tracking:** 50 concurrent chat requests cùng tenant cùng billing period → `UsageRecord.value` = exactly 50 (không ít hơn vì race condition).

3. **Widget origin enforcement:** Request từ domain không trong `allowed_domains` → 403. Request từ allowed domain → 200.

4. **Document processing failure:** Upload corrupted PDF → status = FAILED với human-readable `error_message`. Failure không block processing của documents khác.

5. **SSE stream interruption:** Client disconnect mid-stream → server detect và stop LLM token generation (tránh waste API credits).

6. **Plan quota enforcement:** Free tier tenant với 100 messages gửi message #101 → 403 với clear error message và upgrade path.

7. **Cascade delete completeness:** Xóa KB với 100 docs và 10,000 chunks → zero orphan rows, complete trong 30 giây.

8. **Trial expiry:** 14 ngày sau signup không upgrade → subscription = FREE, excess KBs frozen, email notification gửi ngày 11 và ngày 14.

9. **Chat với empty KB:** Chatbot có KB không có documents INDEXED → trả về "Knowledge base is being prepared" không hallucinate.

10. **Owner tự remove:** Business owner cố xóa chính mình khỏi tenant → 403 với clear error.

---

*Phân tích được thực hiện dựa trên PROJECT_SPEC.md ngày 2026-05-06.*  
*Không có source code hiện có — đây là pre-implementation design review.*
