# Product Requirements Document (PRD / SRS)
## spring-saas-support-ai — AI Customer Support Platform

**Author:** Leonard Trinh  
**Date:** 2026-05-06  
**Status:** Draft — Pre-implementation  
**Version:** 1.0  
**Repository:** `spring-saas-support-ai` (public, MIT)

---

## Mục lục

1. [Executive Summary](#1-executive-summary)
2. [Background & Context](#2-background--context)
3. [Objectives & Success Metrics](#3-objectives--success-metrics)
4. [Target Users & Segments](#4-target-users--segments)
5. [Domain Model](#5-domain-model)
6. [Functional Requirements](#6-functional-requirements)
   - 6.1 [M1 — Multi-tenant Auth & Member Management](#61-m1--multi-tenant-auth--member-management)
   - 6.2 [M2 — Knowledge Base & Document Processing](#62-m2--knowledge-base--document-processing)
   - 6.3 [M3 — AI Chat & Embeddable Widget](#63-m3--ai-chat--embeddable-widget)
   - 6.4 [M4 — Billing, Quotas & Production Hardening](#64-m4--billing-quotas--production-hardening)
7. [Non-Functional Requirements](#7-non-functional-requirements)
8. [API Specification](#8-api-specification)
9. [System Constraints & Hard Rules](#9-system-constraints--hard-rules)
10. [Open Questions](#10-open-questions)
11. [Timeline & Milestones](#11-timeline--milestones)

---

## 1. Executive Summary

**spring-saas-support-ai** là một nền tảng AI customer support mã nguồn mở, self-hostable, dành cho các doanh nghiệp vừa và nhỏ (SMBs). Platform cho phép bất kỳ doanh nghiệp nào tạo chatbot hỗ trợ khách hàng được huấn luyện trên tài liệu nội bộ của họ và nhúng vào website chỉ với một dòng script trong vòng 5 phút.

Dự án phục vụ hai mục tiêu song song: (1) xây dựng portfolio kỹ thuật chứng minh năng lực Senior Java/Spring Boot + AI Integration cho mục tiêu tìm việc, và (2) tạo ra một sản phẩm mã nguồn mở có thể monetize sau này thông qua hosted SaaS, template sales, hoặc implementation services.

**Positioning:** Open-source alternative to Intercom AI — white-label, self-hostable, $0 license fee.

---

## 2. Background & Context

### Vấn đề thị trường

Customer support là nỗi đau lớn nhất của SMBs:
- Các giải pháp SaaS hàng đầu (Intercom, Zendesk, Crisp) có giá từ $300-2000+/tháng — quá đắt với đội nhóm 5-50 người.
- Các giải pháp này là "closed box" — dữ liệu conversation của khách hàng cuối bị lock trong hệ thống của vendor.
- Không có giải pháp open-source production-grade nào tồn tại ở phân khúc này.

### Tại sao dự án này khác biệt

| Giải pháp | Mô hình | Gap |
|-----------|---------|-----|
| Intercom, Zendesk AI | SaaS đóng, $$$, vendor lock-in | Đắt, không self-hostable |
| Generic Spring Boot starters | Không có business domain | Cần tùy chỉnh nhiều, không deployable |
| Spring AI examples | Demo toys | Không phải production multi-tenant SaaS |
| Generic boilerplates | Cần heavy customization | Không phải complete vertical solution |

### Chiến lược kỹ thuật

**Stack rationale:** Java 21 + Spring Boot 3.3 được chọn vì: (1) LTS support đến 2031, (2) ít cạnh tranh hơn Python AI roles trong thị trường tuyển dụng, (3) phù hợp với enterprise tech stack của employer targets, (4) Spring AI là official Spring project có backing mạnh.

**Monolith-first:** Không split microservices cho đến khi có signal rõ ràng từ production data (throughput, deploy frequency). 4-week timeline không cho phép distributed systems overhead.

**One database:** PostgreSQL 16 với PgVector extension cho cả relational, vector search, và full-text search — giảm operational complexity đáng kể.

---

## 3. Objectives & Success Metrics

### Mục tiêu chính

| # | Mục tiêu | Ưu tiên | Thời gian |
|---|----------|---------|-----------|
| 1 | Nhận job offer (AI Application Engineer + Java) | **Cao** | 8-12 tuần |
| 2 | Kiếm freelance income đầu tiên | Trung bình | 6-8 tuần |
| 3 | Xây dựng open-source authority | Thấp | 3 tháng |

### Success Metrics

| Metric | Hiện tại | Target sau 4 tuần | Cách đo |
|--------|----------|-------------------|---------|
| GitHub stars | 0 | 50-200 | GitHub insights |
| Upwork applications gửi | 0 | 30-40 | Upwork tracker |
| Job interviews nhận được | 0 | 1-3 | Calendar |
| Live demo URL hoạt động | Không | Có (mỗi milestone) | Render/Railway URL |
| Blog posts published | 0 | 4 (1/milestone) | Dev.to / Medium |
| Freelance conversations | 0 | 2-5 | Upwork messages |

### Non-Goals (explicitly out of scope)

- ❌ Mobile app / native iOS / Android SDK
- ❌ Voice interface
- ❌ Multi-language UI (English only)
- ❌ Advanced analytics dashboard
- ❌ Third-party integrations ngoài Stripe (Slack, Jira, etc.)
- ❌ Multiple LLM providers (Claude only cho đến v1.0)
- ❌ Self-hosted LLM support (cloud APIs only)
- ❌ Real-time collaboration features
- ❌ Custom domain cho admin dashboard

---

## 4. Target Users & Segments

### Segment 1: SMB Founders / Support Teams (End users của platform)

**Profile:** Founder hoặc support lead tại công ty 5-50 người  
**Pain:** Trả lời customer support emails lặp đi lặp lại tốn 2-4 giờ/ngày; không đủ budget thuê dedicated support staff  
**Want:** AI chatbot trả lời câu hỏi thường gặp, escalate câu hỏi phức tạp cho human  
**Trigger mua:** Intercom quote >$200/tháng; vừa raise funding và cần scale support nhanh  
**Value proposition:** Self-hostable, data ownership, $0 license, 5 phút để deploy

**Persona chính — "Startup Sarah":**
- CTO / Co-founder startup B2B SaaS 15 người
- Tech savvy, có thể tự deploy Docker
- Budget: $50-100/tháng cho tools
- Pain: 30% câu hỏi support là "how do I...?" — hoàn toàn có thể automate

### Segment 2: Developers / Technical Users

**Profile:** Backend developer đang build support feature cho company hoặc client  
**Pain:** Muốn implement AI chatbot nhưng mất 2-4 tuần build từ đầu  
**Want:** Production-ready code để study hoặc fork  
**Value proposition:** Save weeks of architecture work; battle-tested patterns để học

### Segment 3: Agencies

**Profile:** Digital agency có 10-50 SMB clients  
**Pain:** Build custom support tools cho mỗi client không profitable  
**Want:** White-label solution có thể resell  
**Value proposition:** Fork once, customize per client, charge implementation fee

### Stakeholders cho Portfolio

**Recruiters / Hiring managers:**
- Muốn thấy: production patterns, architecture decisions, code quality, test coverage
- Sẽ xem: GitHub repo structure, README, commit history, live demo, blog posts

**Upwork clients:**
- Muốn thấy: working examples giống với nhu cầu của họ
- Sẽ xem: portfolio entries, demo video, case studies

---

## 5. Domain Model

### Core Entities

```
Business (Tenant)
  id: UUID (PK)
  name: String
  slug: String (unique)
  plan_id: UUID (FK → Plan)
  stripe_customer_id: String (nullable)
  suspended_at: Timestamp (nullable)
  created_at: Timestamp

Member (User trong tenant)
  id: UUID (PK)
  business_id: UUID (FK → Business, tenant_id)
  email: String (globally unique)
  password_hash: String
  role: Enum {OWNER, ADMIN, MEMBER}
  email_verified: Boolean
  created_at: Timestamp
  CONSTRAINT: unique(business_id, email)

Invitation
  id: UUID (PK)
  business_id: UUID (FK → Business, tenant_id)
  email: String
  role: Enum {ADMIN, MEMBER}
  token: String (unique, random 256-bit)
  expires_at: Timestamp
  accepted_at: Timestamp (nullable)
  CONSTRAINT: unique(business_id, email) WHERE accepted_at IS NULL

KnowledgeBase
  id: UUID (PK)
  business_id: UUID (FK → Business, tenant_id)
  name: String
  description: String (nullable)
  status: Enum {ACTIVE, ARCHIVED}
  created_at: Timestamp

Document
  id: UUID (PK)
  knowledge_base_id: UUID (FK → KnowledgeBase)
  type: Enum {PDF, MARKDOWN, URL, TEXT, DOCX}
  source: String (filename hoặc URL)
  original_filename: String (nullable)
  status: Enum {PENDING, PROCESSING, INDEXED, FAILED, DELETING}
  error_message: String (nullable)
  retry_count: Integer (default 0)
  last_error: String (nullable)
  file_storage_key: String (nullable — S3/R2 key)
  created_at: Timestamp

ProcessingJob
  id: UUID (PK)
  document_id: UUID (FK → Document)
  business_id: UUID (tenant_id)
  status: Enum {QUEUED, RUNNING, COMPLETED, FAILED}
  created_at: Timestamp
  started_at: Timestamp (nullable)
  completed_at: Timestamp (nullable)
  -- Persists async job state across app restarts

Chunk (Vector store)
  id: UUID (PK)
  document_id: UUID (FK → Document)
  content: Text
  embedding: vector(1536)
  token_count: Integer
  position: Integer
  chunk_type: Enum {PARAGRAPH, TABLE, CODE, LIST, HEADER}
  metadata: JSONB
    -- source_document_id, page_number, section_heading, position, parent_chunk_id
  INDEX: hnsw (embedding vector_cosine_ops) WITH (m=16, ef_construction=100)
  INDEX: GIN (to_tsvector(content)) -- full-text search

Chatbot
  id: UUID (PK)
  knowledge_base_id: UUID (FK → KnowledgeBase)
  name: String
  system_prompt: Text
  settings: JSONB
    -- theme_color, welcome_message, placeholder_text
    -- model: claude-3-5-sonnet | claude-3-haiku
    -- temperature: 0.0-1.0
    -- max_response_tokens: Integer
  created_at: Timestamp

Widget
  id: UUID (PK)
  chatbot_id: UUID (FK → Chatbot)
  allowed_domains: String[] (array of allowed origin domains)
  position: Enum {BOTTOM_RIGHT, BOTTOM_LEFT}
  theme: JSONB (theme color, custom CSS)

Conversation
  id: UUID (PK)
  chatbot_id: UUID (FK → Chatbot)
  session_id: String (server-generated, expires after TTL)
  user_metadata: JSONB (IP, user agent, referrer)
  started_at: Timestamp
  ended_at: Timestamp (nullable)
  INDEX: (chatbot_id, session_id)

Message
  id: UUID (PK)
  conversation_id: UUID (FK → Conversation)
  role: Enum {USER, ASSISTANT, SYSTEM}
  content: Text
  tokens_used: Integer (nullable)
  cost_usd: Decimal(10,8) (nullable — từ provider-reported usage)
  sources: JSONB (array of {chunk_id, relevance_score})
  created_at: Timestamp

Plan
  id: UUID (PK)
  name: String
  slug: String (unique)
  price_usd_monthly: Decimal(10,2)
  stripe_price_id: String (nullable)
  max_knowledge_bases: Integer (-1 = unlimited)
  max_documents_per_kb: Integer (-1 = unlimited)
  max_messages_per_month: Integer (-1 = unlimited)
  max_members: Integer (-1 = unlimited)
  features: String[] (custom_branding, api_access, priority_support)
  is_active: Boolean

Subscription
  id: UUID (PK)
  business_id: UUID (FK → Business, unique)
  plan_id: UUID (FK → Plan)
  stripe_subscription_id: String (nullable)
  status: Enum {TRIALING, ACTIVE, PAST_DUE, CANCELED, INCOMPLETE}
  current_period_start: Timestamp
  current_period_end: Timestamp
  trial_ends_at: Timestamp (nullable)
  canceled_at: Timestamp (nullable)

UsageRecord
  id: UUID (PK)
  business_id: UUID (FK → Business, tenant_id)
  period: String (YYYY-MM format)
  metric: Enum {MESSAGES_SENT, TOKENS_CONSUMED, DOCUMENTS_INDEXED, API_CALLS}
  value: Integer
  CONSTRAINT: unique(business_id, period, metric)
  INDEX: (business_id, period, metric)

AuditLog
  id: UUID (PK)
  business_id: UUID (FK → Business, tenant_id)
  member_id: UUID (FK → Member, nullable — null for system actions)
  impersonator_id: UUID (nullable — khi admin impersonate)
  action: String (LOGIN, INVITE_MEMBER, CHANGE_PLAN, DELETE_KB, IMPERSONATION, ...)
  resource_type: String (nullable)
  resource_id: UUID (nullable)
  metadata: JSONB
  created_at: Timestamp
  INDEX: (business_id, created_at)

ApiKey
  id: UUID (PK)
  business_id: UUID (FK → Business, tenant_id)
  name: String
  key_hash: String (SHA-256 of actual key, indexed)
  scope: Enum {FULL, READ_ONLY, CHAT_ONLY}
  last_used_at: Timestamp (nullable)
  expires_at: Timestamp (nullable)
  created_at: Timestamp

StripeEvent
  id: UUID (PK)
  event_id: String (unique — Stripe event ID, idempotency key)
  event_type: String
  processed_at: Timestamp
```

### Entity Relationship Diagram

```
Business 1───* Member
Business 1───* KnowledgeBase 1───* Document 1───* Chunk
Business 1───1 Subscription *───1 Plan
KnowledgeBase 1───1 Chatbot 1───* Conversation 1───* Message
Chatbot 1───1 Widget
Business 1───* UsageRecord
Business 1───* AuditLog
Business 1───* ApiKey
Document 1───1 ProcessingJob
```

### Plan Tiers

| Plan | Giá | KB tối đa | Docs/KB | Messages/tháng | Members |
|------|-----|-----------|---------|----------------|---------|
| Free | $0 | 1 | 5 | 100 | 1 |
| Starter | $29/tháng | 3 | 50 | 1,000 | 3 |
| Pro | $99/tháng | 10 | 500 | 10,000 | 10 |
| Business | $299/tháng | ∞ | ∞ | 100,000 | ∞ |

**Trial:** 14 ngày Pro plan khi signup, không cần credit card. Tự động downgrade xuống Free sau khi hết trial nếu không upgrade.

---

## 6. Functional Requirements

### 6.1 M1 — Multi-tenant Auth & Member Management

**Mục tiêu:** Working multi-tenant SaaS skeleton với auth và member management, deploy được lên Render, có demo URL.

#### FR-1.1: Business Registration (Signup)

| ID | Yêu cầu |
|----|---------|
| FR-1.1.1 | POST `/api/v1/auth/signup` nhận `businessName`, `email`, `password`. Tạo Business + Member (role=OWNER) trong một transaction. |
| FR-1.1.2 | Email phải globally unique (không chỉ unique per business). |
| FR-1.1.3 | Trả về JWT access token (15 phút) + refresh token (7 ngày). |
| FR-1.1.4 | Subscription được tạo với 14-day Pro trial, `status=TRIALING`. |
| FR-1.1.5 | Gửi verification email (async). |
| FR-1.1.6 | Business `slug` được auto-generate từ `businessName`, đảm bảo unique. |

**Acceptance Criteria:**
- Signup với email đã tồn tại → 409 Conflict với message rõ ràng
- Signup thành công → JWT valid, decode được `tenant_id`, `role=OWNER`
- Business và Member được tạo trong cùng transaction — nếu fail thì rollback cả hai

#### FR-1.2: Authentication

| ID | Yêu cầu |
|----|---------|
| FR-1.2.1 | POST `/api/v1/auth/login` với email + password → JWT access token + refresh token. |
| FR-1.2.2 | POST `/api/v1/auth/refresh` với refresh token → JWT access token mới. Rotate refresh token. |
| FR-1.2.3 | POST `/api/v1/auth/logout` → revoke refresh token (mark as used in DB). |
| FR-1.2.4 | JWT payload phải chứa: `sub` (member UUID), `tenant_id` (business UUID), `role`, `email`, `exp`, `jti` (unique token ID). |
| FR-1.2.5 | Refresh token lưu dạng SHA-256 hash trong DB (không lưu plaintext). Dùng constant-time comparison. |
| FR-1.2.6 | POST `/api/v1/auth/forgot-password` → gửi email reset link (token expires sau 1 giờ). |
| FR-1.2.7 | POST `/api/v1/auth/reset-password` với token + new password → invalidate tất cả refresh tokens của user. |

**Acceptance Criteria:**
- Login với sai password → 401 với message không reveal password hint
- Expired access token → 401 với error code `TOKEN_EXPIRED` (phân biệt với `TOKEN_INVALID`)
- Refresh với revoked token → 401
- Forgot password với email không tồn tại → 200 (không leak thông tin user có tồn tại không)

#### FR-1.3: Multi-tenancy

| ID | Yêu cầu |
|----|---------|
| FR-1.3.1 | `TenantContext` dùng `ThreadLocal<UUID>` để hold current tenant ID. |
| FR-1.3.2 | `JwtAuthFilter` set `TenantContext` từ JWT claim `tenant_id` sau khi validate JWT. |
| FR-1.3.3 | `TenantContext.clear()` PHẢI được gọi trong `finally` block — không có exception nào được bypass. |
| FR-1.3.4 | Hibernate filter tự động append `WHERE tenant_id = :currentTenantId` cho tất cả entities có `tenant_id`. |
| FR-1.3.5 | `TenantContextCopyingDecorator` propagate `TenantContext` sang `@Async` threads. Decorator phải clear context trong `finally` block sau khi task hoàn thành. |
| FR-1.3.6 | `spring.threads.virtual.enabled=false` — Virtual threads bị disabled để tránh ThreadLocal propagation issues với Project Loom. |

**Acceptance Criteria (CRITICAL):**
- `TenantIsolationIT`: Member của Business A không thể nhận dữ liệu của Business B qua bất kỳ API endpoint nào
- Test phải cover: KnowledgeBase, Document, Member, AuditLog endpoints
- Test chạy với 2 tenants có dữ liệu overlap (same names, same IDs in different tenants)

#### FR-1.4: Member Management

| ID | Yêu cầu |
|----|---------|
| FR-1.4.1 | `GET /api/v1/members` — List members trong current tenant. Chỉ ADMIN và OWNER. |
| FR-1.4.2 | `POST /api/v1/members/invite` — Gửi invitation email với token (expires 72 giờ). Chỉ OWNER/ADMIN. Không cho phép invite email đã là member. |
| FR-1.4.3 | `POST /api/v1/invitations/accept` — Accept invitation với token. Tạo Member account. |
| FR-1.4.4 | `DELETE /api/v1/members/{id}` — Remove member. Chỉ OWNER. OWNER không thể tự xóa chính mình. |
| FR-1.4.5 | `PATCH /api/v1/members/{id}/role` — Change role. Chỉ OWNER. Không thể change role của chính mình. |
| FR-1.4.6 | Plan limit: Số lượng members hiện tại không vượt `Plan.max_members` khi accept invitation. |

**Acceptance Criteria:**
- OWNER cố xóa chính mình → 403 với message rõ ràng
- Accept invitation expired → 400 với message rõ ràng
- Invite email đã là member → 409
- Accept invitation khi tenant đã full members → 403 với upgrade prompt

#### FR-1.5: Error Handling & API Standards

| ID | Yêu cầu |
|----|---------|
| FR-1.5.1 | `GlobalExceptionHandler` map typed exceptions sang `ProblemDetail` (RFC 7807). |
| FR-1.5.2 | API response envelope: `ApiResponse<T> { success, data, error }`. |
| FR-1.5.3 | Typed exceptions: `TenantNotFoundException`, `QuotaExceededException`, `DocumentProcessingException`, `InvalidTokenException`, `EmailAlreadyExistsException`. |
| FR-1.5.4 | Validation errors (400): chứa field-level error messages. |
| FR-1.5.5 | Không return `null` — dùng `Optional<T>` hoặc throw typed exception. |

---

### 6.2 M2 — Knowledge Base & Document Processing

**Mục tiêu:** Working RAG pipeline — upload documents, index vào vector store, search được.

#### FR-2.1: Knowledge Base CRUD

| ID | Yêu cầu |
|----|---------|
| FR-2.1.1 | CRUD đầy đủ cho KnowledgeBase (`GET/POST/PATCH/DELETE /api/v1/knowledge-bases`). |
| FR-2.1.2 | Plan limit enforcement: số KB active không vượt `Plan.max_knowledge_bases`. |
| FR-2.1.3 | `DELETE /api/v1/knowledge-bases/{id}`: Mark KB status = `DELETING`, return 202, cascade delete async. |
| FR-2.1.4 | Trong async deletion: xóa Documents → Chunks → ProcessingJobs. Log completion. |

#### FR-2.2: Document Upload & Processing

| ID | Yêu cầu |
|----|---------|
| FR-2.2.1 | `POST /api/v1/knowledge-bases/{kbId}/documents` — Multipart upload. Accept PDF, DOCX, TXT, MD. Max 10MB per file. Max 100 pages per PDF. |
| FR-2.2.2 | File lưu vào cloud storage (Cloudflare R2 hoặc S3), KHÔNG lưu local filesystem. `file_storage_key` được save vào Document entity. |
| FR-2.2.3 | Sau upload: tạo Document (status=PENDING) + ProcessingJob (status=QUEUED). Return 202 Accepted với document ID. |
| FR-2.2.4 | Plan limit: `documents_count < Plan.max_documents_per_kb` trước khi accept upload. |
| FR-2.2.5 | URL ingestion: POST body chứa URL thay vì file. Crawl và extract content từ URL. |

**Document Processing Pipeline (async):**

| Step | Yêu cầu |
|------|---------|
| FR-2.2.6 | Status: PENDING → PROCESSING → INDEXED → FAILED. Update ProcessingJob accordingly. |
| FR-2.2.7 | Parse: Apache Tika cho PDF/DOCX. UTF-8 encoding. Strip HTML tags. |
| FR-2.2.8 | Chunking: Structure-aware splitter. Giữ nguyên tables (không split giữa rows). Giữ nguyên code blocks. Prefix section heading vào chunk con. Max 1000 tokens, overlap 100 tokens. |
| FR-2.2.9 | Metadata per chunk: `source_document_id`, `page_number`, `section_heading`, `position`, `chunk_type`. |
| FR-2.2.10 | Embedding: OpenAI `text-embedding-3-small` (1536 dims). Batch 20 chunks per API call. |
| FR-2.2.11 | Store chunks vào PostgreSQL với PgVector extension. |
| FR-2.2.12 | Retry logic: Max 3 retries với exponential backoff (1s, 4s, 16s). Sau 3 lần → status=FAILED, set `error_message` human-readable. |
| FR-2.2.13 | Tenant-level concurrency limit: max 3 ProcessingJobs RUNNING cùng lúc per tenant. |
| FR-2.2.14 | Khi app restart: re-queue tất cả ProcessingJobs có status=RUNNING (recover từ crash). |
| FR-2.2.15 | Email/in-app notification khi document FAILED sau tất cả retries. |

**Acceptance Criteria:**
- Upload valid 10-page PDF → status = INDEXED trong vòng 60 giây
- Upload corrupted PDF → status = FAILED với human-readable error_message trong vòng 30 giây
- Failure không block processing của documents khác trong cùng KB
- Upload file >10MB → 413 ngay lập tức, không queue
- 50 concurrent uploads từ same tenant → max 3 RUNNING cùng lúc, rest queued

#### FR-2.3: Vector Search

| ID | Yêu cầu |
|----|---------|
| FR-2.3.1 | HNSW index: `CREATE INDEX ON chunks USING hnsw (embedding vector_cosine_ops) WITH (m=16, ef_construction=100)`. |
| FR-2.3.2 | Vector search: Top-10 chunks by cosine similarity. |
| FR-2.3.3 | Keyword search: PG full-text search với GIN index trên `content`. |
| FR-2.3.4 | Hybrid search: Reciprocal Rank Fusion (k=60) kết hợp vector + keyword results. |
| FR-2.3.5 | Final result: Top-5 chunks sau RRF → làm context cho LLM. |
| FR-2.3.6 | Fallback: Nếu keyword search trả về empty → dùng vector-only results (và ngược lại). |
| FR-2.3.7 | `GET /api/v1/documents/{id}/chunks` — Debug endpoint để view chunks (ADMIN only). |

---

### 6.3 M3 — AI Chat & Embeddable Widget

**Mục tiêu:** End-to-end chat experience với embeddable widget, streaming responses.

#### FR-3.1: Chatbot Management

| ID | Yêu cầu |
|----|---------|
| FR-3.1.1 | CRUD cho Chatbot (`GET/POST/PATCH/DELETE /api/v1/chatbots`). |
| FR-3.1.2 | Chatbot linked 1-to-1 với KnowledgeBase. |
| FR-3.1.3 | Configurable settings: `model` (claude-3-5-sonnet / claude-3-haiku), `temperature` (0.0-1.0), `max_response_tokens`, `system_prompt`, `welcome_message`, `theme_color`. |
| FR-3.1.4 | Widget config: `allowed_domains[]`, `position` (BOTTOM_RIGHT/BOTTOM_LEFT). |

#### FR-3.2: Public Chat API (Widget-facing)

| ID | Yêu cầu |
|----|---------|
| FR-3.2.1 | `POST /api/v1/chat/{chatbotId}` — Stream response qua Server-Sent Events (SSE). No auth required. |
| FR-3.2.2 | Request body: `{ sessionId, message }`. `sessionId` là server-generated token (được cấp từ endpoint riêng hoặc trả về lần đầu). |
| FR-3.2.3 | **Origin validation**: So sánh `Origin` header với `Widget.allowed_domains`. Return 403 nếu không match. Hỗ trợ wildcard subdomain (`*.example.com`). |
| FR-3.2.4 | **Token budget**: Hard limit 6000 tokens cho retrieved context. |
| FR-3.2.5 | **Conversation summarization**: Sau 8 messages, summarize các messages cũ, giữ verbatim 4 messages gần nhất. |
| FR-3.2.6 | SSE stream: Gửi tokens as `data: {"token": "..."}`. Kết thúc với `data: {"done": true, "sources": [...], "conversationId": "..."}`. |
| FR-3.2.7 | Khi client disconnect mid-stream: Server phải detect và dừng LLM generation (không waste API credits). |
| FR-3.2.8 | Empty KB (no INDEXED documents): Trả về message "Knowledge base is being prepared" thay vì hallucinate. |
| FR-3.2.9 | Track token usage và cost per message từ provider-reported `usage` field (không tự estimate). |
| FR-3.2.10 | Ghi UsageRecord atomic: `UPDATE ... WHERE value < plan_limit`. 0 rows affected = quota exceeded → 403. |
| FR-3.2.11 | `GET /api/v1/chat/{chatbotId}/history/{sessionId}` — Conversation history. Chỉ trả về nếu session hợp lệ và chưa expire. |
| FR-3.2.12 | Anonymous session TTL: 7 ngày kể từ lần cuối active. Scheduled job cleanup sessions expired. |
| FR-3.2.13 | `POST /api/v1/chat/{chatbotId}/feedback` — Thumbs up/down feedback với optional comment. |

**Acceptance Criteria:**
- Request từ domain không trong `allowed_domains` → 403
- 100-token response stream đến client trong real-time (không buffer toàn bộ)
- Client disconnect → server stop generation trong 2 giây
- Message 101 với Free tier (limit 100) → 403 với upgrade URL
- Chat với empty KB → "preparing" message, không hallucinate

#### FR-3.3: Embeddable Widget

| ID | Yêu cầu |
|----|---------|
| FR-3.3.1 | Vanilla JS single file, NO framework dependencies, <50KB minified + gzipped. |
| FR-3.3.2 | Embed code: `<script src="https://your-domain/widget.js" data-chatbot-id="UUID"></script>` |
| FR-3.3.3 | Widget phải không conflict với host page CSS (Shadow DOM hoặc scoped CSS). |
| FR-3.3.4 | Widget load trong <3 giây trên 3G connection. |
| FR-3.3.5 | Position: BOTTOM_RIGHT hoặc BOTTOM_LEFT, configurable. |
| FR-3.3.6 | Theming: primary color, welcome message, placeholder text. |
| FR-3.3.7 | `GET /api/v1/chatbots/{id}/widget-config` — Public endpoint trả về widget config (không cần auth). |

#### FR-3.4: Conversation Management (Admin)

| ID | Yêu cầu |
|----|---------|
| FR-3.4.1 | `GET /api/v1/conversations` — Paginated list với filters (chatbot_id, date range, has_feedback). |
| FR-3.4.2 | `GET /api/v1/conversations/{id}` — Full conversation với messages và sources. |
| FR-3.4.3 | `GET /api/v1/conversations/analytics` — Aggregated stats: total conversations, avg messages/conversation, satisfaction rate. |

---

### 6.4 M4 — Billing, Quotas & Production Hardening

**Mục tiêu:** Production-ready SaaS với Stripe billing, quota enforcement, và observability.

#### FR-4.1: Stripe Billing Integration

| ID | Yêu cầu |
|----|---------|
| FR-4.1.1 | `GET /api/v1/billing/plans` — List available plans với pricing. |
| FR-4.1.2 | `GET /api/v1/billing/subscription` — Current subscription status của tenant. |
| FR-4.1.3 | `POST /api/v1/billing/checkout-session` — Tạo Stripe Checkout Session, redirect URL. |
| FR-4.1.4 | `POST /api/v1/billing/portal-session` — Tạo Stripe Customer Portal session (manage/cancel subscription). |
| FR-4.1.5 | `GET /api/v1/billing/invoices` — Invoice history. |
| FR-4.1.6 | `POST /api/v1/billing/webhook` — Stripe webhook handler (public endpoint). |

**Webhook Handler Requirements:**
| ID | Yêu cầu |
|----|---------|
| FR-4.1.7 | **Idempotency**: Insert `event_id` vào `stripe_events` trước khi xử lý. Duplicate key exception = skip (event đã processed). |
| FR-4.1.8 | **Signature verification**: Verify `Stripe-Signature` header với `Webhook.constructEvent()` trước khi xử lý bất cứ gì. |
| FR-4.1.9 | Handle events: `checkout.session.completed`, `invoice.payment_succeeded`, `invoice.payment_failed`, `customer.subscription.updated`, `customer.subscription.deleted`, `customer.subscription.trial_will_end`. |
| FR-4.1.10 | `trial_will_end` (fired 3 ngày trước): Gửi email notification + daily cron job backup check `trial_ends_at` gửi email 1 ngày trước và ngay ngày hết. |

#### FR-4.2: Quota Enforcement

| ID | Yêu cầu |
|----|---------|
| FR-4.2.1 | Quota check trước khi: gửi chat message, upload document, tạo KB, invite member. |
| FR-4.2.2 | **Atomic enforcement**: `UPDATE usage_records SET value = value + 1 WHERE business_id = ? AND period = ? AND metric = ? AND value < :limit`. 0 rows affected = reject với 403. |
| FR-4.2.3 | Upsert pattern: `INSERT ... ON CONFLICT DO UPDATE` khi row chưa tồn tại. |
| FR-4.2.4 | 403 response phải chứa: quota limit, current usage, metric type, upgrade plan URL. |
| FR-4.2.5 | `GET /api/v1/usage/current` — Current period usage cho tất cả metrics. |
| FR-4.2.6 | `GET /api/v1/usage/history` — Monthly usage history (12 tháng). |

#### FR-4.3: Plan Downgrade — "Soft Freeze"

| ID | Yêu cầu |
|----|---------|
| FR-4.3.1 | Khi downgrade plan, resources vượt limit chuyển sang `FROZEN` status (KB/Documents). |
| FR-4.3.2 | FROZEN KB: read-only, không nhận chat messages mới, không index documents mới. |
| FR-4.3.3 | Admin UI phải show banner "X resources frozen. Upgrade to access." cho resources bị frozen. |
| FR-4.3.4 | KHÔNG auto-delete user data khi downgrade. User phải manually delete nếu muốn. |
| FR-4.3.5 | Upgrade plan → unfreeze resources tự động. |

#### FR-4.4: Rate Limiting

| ID | Yêu cầu |
|----|---------|
| FR-4.4.1 | Rate limiting với Bucket4j. In-memory cho single instance (MVP); Redis backend khi scale. |
| FR-4.4.2 | Public chat endpoint: composite key `chatbotId + clientIP`. Limits: Free=10/min, Starter=30/min, Pro=60/min, Business=120/min. |
| FR-4.4.3 | Auth endpoints (login/signup/reset): 5 attempts per IP per minute. |
| FR-4.4.4 | Admin API: 100 requests/min per tenant. |
| FR-4.4.5 | Rate limit exceeded → 429 với `Retry-After` header. |

#### FR-4.5: Admin Endpoints (Super-admin)

| ID | Yêu cầu |
|----|---------|
| FR-4.5.1 | Super-admin role phân biệt với OWNER/ADMIN/MEMBER — không được expose qua regular signup. |
| FR-4.5.2 | `GET /api/v1/admin/businesses` — List tất cả businesses (cross-tenant). Dùng `AdminRepository` layer bypass Hibernate filter. |
| FR-4.5.3 | `POST /api/v1/admin/businesses/{id}/suspend` — Suspend business. Revoke tất cả active tokens của tenant. |
| FR-4.5.4 | `POST /api/v1/admin/impersonate/{businessId}` — Issue impersonation JWT với: `impersonator_id` claim, TTL max 15 phút, `read_only=true`. |
| FR-4.5.5 | Impersonation JWT: Block billing changes và delete operations. Tất cả actions trong impersonated session log với cả `impersonator_id` và `tenant_id` trong AuditLog. |
| FR-4.5.6 | Không cho phép nested impersonation. |

#### FR-4.6: JWT Revocation (Critical Path)

| ID | Yêu cầu |
|----|---------|
| FR-4.6.1 | Khi business bị suspend: store revoked `jti` values trong Redis với TTL = access token remaining lifetime (max 15 phút). |
| FR-4.6.2 | Mọi authenticated request phải check Redis blocklist (1 lookup). |
| FR-4.6.3 | Khi member bị remove: revoke tất cả refresh tokens của member đó (delete từ DB). |

#### FR-4.7: Observability & Production Hardening

| ID | Yêu cầu |
|----|---------|
| FR-4.7.1 | Micrometer metrics: request latency (p50, p95, p99), LLM API latency, document processing duration, quota check outcomes. |
| FR-4.7.2 | Health endpoint `/actuator/health` với indicators: DB, Redis (nếu enabled), disk space. Không expose external API checks (avoid rate limit drain). |
| FR-4.7.3 | Security headers: `X-Content-Type-Options`, `X-Frame-Options`, `Content-Security-Policy`. |
| FR-4.7.4 | Audit logging: mọi WRITE operation trên sensitive resources phải ghi AuditLog async. |
| FR-4.7.5 | CORS cấu hình strict cho `/api/` (only allowed origins); wildcard chỉ cho `/api/v1/chat/` với server-side origin validation bổ sung. |

---

## 7. Non-Functional Requirements

### NFR-1: Performance

| Metric | Target |
|--------|--------|
| API response time (P95) | < 300ms (excluding LLM calls) |
| Chat SSE first token | < 2 giây kể từ khi user gửi message |
| Document processing 10-page PDF | < 60 giây |
| Vector search (Top-10) | < 100ms cho corpus < 100K chunks |
| Widget load time | < 3 giây trên 3G |

### NFR-2: Reliability

| Metric | Target |
|--------|--------|
| API uptime | 99% (cho free tier, no SLA) |
| Tenant data isolation | 100% — zero cross-tenant data leakage |
| Processing job recovery | 100% of RUNNING jobs re-queued after app restart |
| Stripe webhook idempotency | 100% — no double-processing |

### NFR-3: Security

| Requirement | Implementation |
|-------------|----------------|
| Tenant isolation | Hibernate filter + `TenantIsolationIT` (100% coverage) |
| Password hashing | BCrypt (strength 12) |
| Refresh token storage | SHA-256 hash, indexed, constant-time comparison |
| JWT signing | HS256 với secret key min 256 bits, stored in env var |
| API keys (external) | Random 256-bit, SHA-256 hashed in DB, scoped |
| Stripe webhooks | Signature verification bắt buộc |
| Rate limiting | Bucket4j trên auth và public endpoints |
| Input validation | Spring Validation trên tất cả DTOs |
| SQL injection | JPA parameterized queries only, không string concat |
| Secrets | Không hardcode trong source code; env vars only |

### NFR-4: Test Coverage

| Layer | Minimum Coverage |
|-------|-----------------|
| Service layer | 60% |
| Tenant isolation paths | **100%** |
| Auth flows | 80% |
| Stripe webhook handlers | 80% |
| Quota enforcement | 80% |

**Test infrastructure:**
- `JUnit 5` + `Testcontainers` (PostgreSQL 16) — không dùng H2
- Abstract base class với static `@Container` chia sẻ 1 container across all IT tests
- Test naming: `{ClassName}Test` (unit), `{ClassName}IT` (integration)
- `TenantIsolationIT` phải pass trước bất kỳ release nào

### NFR-5: Code Quality

| Standard | Rule |
|----------|------|
| No Lombok | Dùng Java 21 records cho DTOs |
| DTOs | Records (immutable by default) |
| Error handling | Typed exceptions, không return null |
| API response | `ApiResponse<T>` envelope thống nhất |
| Comments | Chỉ comment WHY (hidden constraint, subtle invariant) — không comment WHAT |
| File size | < 800 lines per file; ideal < 400 lines |
| Functions | < 50 lines |

### NFR-6: Deployment & Operations

| Requirement | Detail |
|-------------|--------|
| Containerization | Docker multi-stage build |
| CI/CD | GitHub Actions (build + test on PR, deploy on merge to main) |
| Database migrations | Flyway (không manual SQL) |
| Configuration | Spring profiles: `dev`, `test`, `prod` |
| Secrets management | Environment variables (không trong application.yml committed) |
| Hosting | Render / Railway free tier cho demo |
| Document storage | Cloudflare R2 (10GB free tier) hoặc AWS S3 |

---

## 8. API Specification

### Authentication

```
POST   /api/v1/auth/signup              Request: { businessName, email, password }
                                        Response: { access_token, refresh_token, business_id }

POST   /api/v1/auth/login               Request: { email, password }
                                        Response: { access_token, refresh_token }

POST   /api/v1/auth/refresh             Request: { refresh_token }
                                        Response: { access_token, refresh_token }

POST   /api/v1/auth/logout              Header: Authorization Bearer
                                        Response: 204

POST   /api/v1/auth/forgot-password     Request: { email }
                                        Response: 200 (always, no user enumeration)

POST   /api/v1/auth/reset-password      Request: { token, new_password }
                                        Response: 204

POST   /api/v1/auth/verify-email        Request: { token }
                                        Response: 204

GET    /api/v1/auth/me                  Header: Authorization Bearer
                                        Response: { id, email, role, business }
```

### Member Management

```
GET    /api/v1/members                  Role: ADMIN+
GET    /api/v1/members/{id}             Role: ADMIN+
POST   /api/v1/members/invite           Role: ADMIN+  Request: { email, role }
POST   /api/v1/invitations/accept       Public       Request: { token, password }
DELETE /api/v1/members/{id}             Role: OWNER
PATCH  /api/v1/members/{id}/role        Role: OWNER  Request: { role }
```

### Knowledge Base

```
GET    /api/v1/knowledge-bases          Role: MEMBER+
POST   /api/v1/knowledge-bases          Role: ADMIN+  Request: { name, description }
GET    /api/v1/knowledge-bases/{id}     Role: MEMBER+
PATCH  /api/v1/knowledge-bases/{id}     Role: ADMIN+
DELETE /api/v1/knowledge-bases/{id}     Role: ADMIN+  Response: 202 Accepted
```

### Document Management

```
POST   /api/v1/knowledge-bases/{kbId}/documents       Multipart: file OR { url }
                                                       Role: ADMIN+  Response: 202
GET    /api/v1/knowledge-bases/{kbId}/documents       Role: MEMBER+
GET    /api/v1/documents/{id}                          Role: MEMBER+  (includes status)
DELETE /api/v1/documents/{id}                          Role: ADMIN+
POST   /api/v1/documents/{id}/reindex                  Role: ADMIN+
GET    /api/v1/documents/{id}/chunks                   Role: ADMIN+ (debug)
```

### Chatbot

```
GET    /api/v1/chatbots                    Role: MEMBER+
POST   /api/v1/chatbots                    Role: ADMIN+
GET    /api/v1/chatbots/{id}               Role: MEMBER+
PATCH  /api/v1/chatbots/{id}               Role: ADMIN+
DELETE /api/v1/chatbots/{id}               Role: ADMIN+
GET    /api/v1/chatbots/{id}/widget-config Public (no auth)
```

### Public Chat (Widget-facing)

```
POST   /api/v1/chat/{chatbotId}                    Public  SSE stream
                                                   Request: { sessionId?, message }
                                                   Response: SSE stream of tokens

GET    /api/v1/chat/{chatbotId}/history/{sessionId} Public (session-scoped)

POST   /api/v1/chat/{chatbotId}/feedback            Public
                                                    Request: { conversationId, rating, comment? }
```

### Conversations (Admin)

```
GET    /api/v1/conversations                Role: ADMIN+  ?chatbot_id&from&to&page
GET    /api/v1/conversations/{id}           Role: ADMIN+
GET    /api/v1/conversations/analytics      Role: ADMIN+
```

### Billing & Subscription

```
GET    /api/v1/billing/plans               Public (no auth)
GET    /api/v1/billing/subscription        Role: OWNER
POST   /api/v1/billing/checkout-session    Role: OWNER  Request: { planSlug }
POST   /api/v1/billing/portal-session      Role: OWNER
POST   /api/v1/billing/webhook             Public (Stripe webhook, signature-verified)
GET    /api/v1/billing/invoices            Role: OWNER
```

### Usage

```
GET    /api/v1/usage/current               Role: ADMIN+
GET    /api/v1/usage/history               Role: ADMIN+
```

### Admin (Super-admin only)

```
GET    /api/v1/admin/businesses             Role: SUPER_ADMIN
GET    /api/v1/admin/businesses/{id}        Role: SUPER_ADMIN
POST   /api/v1/admin/businesses/{id}/suspend Role: SUPER_ADMIN
POST   /api/v1/admin/impersonate/{businessId} Role: SUPER_ADMIN
```

### Common Response Format

```json
// Success
{ "success": true, "data": { ... }, "error": null }

// Error
{ "success": false, "data": null, "error": "MESSAGE" }

// Paginated
{ "success": true, "data": { "items": [...], "total": 100, "page": 1, "limit": 20 }, "error": null }

// Problem Detail (error responses)
{
  "type": "https://api.saas.dev/errors/quota-exceeded",
  "title": "Quota Exceeded",
  "status": 403,
  "detail": "You have sent 100/100 messages this month. Upgrade to Starter for 1,000 messages.",
  "upgrade_url": "/billing/plans"
}
```

---

## 9. System Constraints & Hard Rules

### Time Constraints

1. **7-ngày hard cap per milestone** — Ship trên Render vào Day 7, 14, 21, 28. Không slip.
2. **Apply Upwork jobs sau mỗi milestone** — Không chờ v1.0.
3. **Blog post mỗi milestone** — Rough draft là đủ.

### Scope Constraints

**Tuyệt đối KHÔNG thêm:**
- Mobile app, Voice interface, Multi-language UI
- Analytics dashboard ngoài basic stats
- Third-party integrations ngoài Stripe
- Nhiều hơn 1 LLM provider (Claude only)
- Self-hosted LLM

**Được phép thêm nếu cần:**
- Critical security fixes
- Bug fixes blocking live demo
- Performance issues ảnh hưởng demo UX

### Architecture Constraints

- Java 21 records cho DTOs — không dùng Lombok
- Testcontainers — không dùng H2
- `@Async` + ThreadPoolTaskExecutor — không dùng Kafka/RabbitMQ
- PostgreSQL + PgVector — không dùng ElasticSearch hay dedicated vector DB
- `spring.threads.virtual.enabled=false` — tắt virtual threads cho M1 (tương thích ThreadLocal)
- Không bypass Hibernate tenant filter — không có ngoại lệ nào ngoài `AdminRepository`

### Package Structure

```
com.leonardtrinh.supportsaas
├── auth/            # JWT, signup, login, refresh, password reset
├── tenant/          # TenantContext, Hibernate filter, Business entity
├── member/          # Member, Invitation, role management
├── knowledgebase/   # KB CRUD
├── document/        # Upload, parse, chunk, embed pipeline
├── chatbot/         # Chatbot settings, Widget config
├── chat/            # Streaming SSE, conversation, message
├── billing/         # Stripe, Subscription, Plan, UsageRecord
├── admin/           # Super-admin endpoints, AdminRepository
├── common/          # ApiResponse, ProblemDetail handler, base entities
└── config/          # Security, async executor, OpenAPI, CORS, HikariCP
```

Mỗi package: `Controller → Service → Repository → Entity/DTO`.

---

## 10. Open Questions

| # | Question | Milestone | Owner | Decision |
|---|----------|-----------|-------|----------|
| 1 | PgVector extension có available trên Render free tier PostgreSQL? Nếu không → switch sang Supabase/Neon. | Trước M2 | Leonard | ⏳ Chưa verify |
| 2 | Cloudflare R2 hay AWS S3 cho document storage? R2: $0 egress + 10GB free. S3: more mature SDK. | Trước M2 | Leonard | ⏳ Pending |
| 3 | Email provider: Resend hay SMTP (mailhog dev, SES prod)? Resend: dev-friendly, $0 cho 3K/tháng. | Trước M1 | Leonard | ⏳ Pending |
| 4 | Redis cho JWT blocklist: có sẵn từ M1 hay chỉ add ở M4? Redis cần thiết cho JWT revocation khi suspend. | M4 | Leonard | ⏳ Pending |
| 5 | Render hay Railway cho hosting? Render: PostgreSQL native, 97-ngày free DB. Railway: simpler env. | Trước M1 | Leonard | ⏳ Pending |
| 6 | Session ID generation cho anonymous chat: UUID v4 hay signed JWT với expiry? | Trước M3 | Leonard | Khuyến nghị: signed JWT (server-generated, có expiry, không thể spoof) |

---

## 11. Timeline & Milestones

### Overview

| Milestone | Theme | Days | Release | Status |
|-----------|-------|------|---------|--------|
| M1 | Multi-tenant Auth + Member Management | 1-7 | v0.1.0 | ⏳ Not started |
| M2 | Knowledge Base + RAG Pipeline | 8-14 | v0.2.0 | ⏳ Not started |
| M3 | AI Chat + Embeddable Widget | 15-21 | v0.3.0 | ⏳ Not started |
| M4 | Billing + Production Hardening | 22-28 | v1.0.0 | ⏳ Not started |

### M1 — Days 1-7 (Multi-tenant Foundation)

| Day | Tasks |
|-----|-------|
| 1-2 | Spring Boot 3.3 + Java 21 setup; Docker compose (postgres + adminer + mailhog); base package structure; application profiles; Flyway setup |
| 3-4 | Business + Member entities + migrations; TenantContext + Hibernate filter; JwtService; Signup + Login + Refresh + Logout endpoints |
| 5-6 | Member invitation flow + accept endpoint; List/remove members; Change role; `TenantIsolationIT` (CRITICAL) |
| 7 | GlobalExceptionHandler; OpenAPI config; Deploy to Render; README v0.1; **Release v0.1.0**; Blog post draft |

**Definition of Done M1:**
- `TenantIsolationIT` passes (100%)
- Live demo URL trên Render responds to API calls
- Swagger UI accessible
- README có setup instructions

### M2 — Days 8-14 (Knowledge Base + RAG)

| Day | Tasks |
|-----|-------|
| 8-9 | KnowledgeBase + Document + Chunk entities + migrations; PgVector extension setup; KB CRUD endpoints; Document upload với cloud storage |
| 10-11 | Apache Tika integration; Structure-aware chunking; Async embedding pipeline; ProcessingJob table + recovery |
| 12-13 | Hybrid search (vector + keyword + RRF); Document deletion (cascade async); Reindex endpoint; Plan limit enforcement |
| 14 | Integration tests cho pipeline; Update README; Deploy v0.2; **Release v0.2.0**; Blog post |

### M3 — Days 15-21 (AI Chat + Widget)

| Day | Tasks |
|-----|-------|
| 15-16 | Chatbot entity + CRUD; SSE streaming chat endpoint; RAG retrieval (Top-5); Source citation |
| 17-18 | Session management (anonymous); Conversation history; Token tracking + cost calculation; Feedback endpoint |
| 19-20 | Widget config entity; Vanilla JS widget (<50KB); CORS + origin validation; Widget customization; Demo page |
| 21 | E2E chat flow test; Record 3-min demo video; **Release v0.3.0**; Blog post; Portfolio entry #3 |

### M4 — Days 22-28 (Billing + Production)

| Day | Tasks |
|-----|-------|
| 22-23 | Plan + Subscription entities + seed data; Stripe Checkout + Portal; Webhook handler (idempotent) |
| 24-25 | UsageRecord tracking; Atomic quota enforcement; Usage dashboard endpoints; Downgrade soft-freeze |
| 26-27 | Bucket4j rate limiting; JWT revocation (Redis blocklist); Security headers + CORS hardening; Admin endpoints; Audit logging |
| 28 | Comprehensive README; Architecture diagram (Mermaid); Deployment guide; **Release v1.0.0**; 5-min demo video; Final blog post |

### Post-release Actions (Mỗi milestone)

- [ ] Tag GitHub release (`v0.x.0`)
- [ ] Update README với live demo URL
- [ ] Publish blog post (Dev.to + Medium)
- [ ] Share LinkedIn post (build in public)
- [ ] Tạo portfolio entry trên Upwork
- [ ] Gửi 5-10 Upwork applications với keywords phù hợp milestone

---

*PRD được tổng hợp từ PROJECT_SPEC.md và SYSTEM_DESIGN_ANALYSIS.md.*  
*Version: 1.0 — 2026-05-06*
