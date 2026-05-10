# UML Diagrams — spring-saas-support-ai

> Tất cả diagrams dùng Mermaid syntax — render trực tiếp trên GitHub.

---

## 1. Use Case Diagram

```mermaid
graph TB
    subgraph Actors
        OWNER([👤 Owner])
        ADMIN([👤 Admin])
        MEMBER([👤 Member])
        ENDUSER([👥 End User\nAnonymous])
        SUPERADMIN([🔐 Super Admin])
        STRIPE([💳 Stripe\nWebhook])
    end

    subgraph UC_AUTH ["Auth & Account"]
        UC1[Đăng ký Business]
        UC2[Đăng nhập]
        UC3[Refresh Token]
        UC4[Đổi mật khẩu]
        UC5[Verify Email]
    end

    subgraph UC_MEMBER ["Member Management"]
        UC6[Mời thành viên]
        UC7[Chấp nhận lời mời]
        UC8[Xóa thành viên]
        UC9[Đổi role thành viên]
        UC10[Xem danh sách thành viên]
    end

    subgraph UC_KB ["Knowledge Base"]
        UC11[Tạo Knowledge Base]
        UC12[Upload tài liệu]
        UC13[Xem trạng thái xử lý]
        UC14[Xóa tài liệu / KB]
        UC15[Reindex tài liệu]
    end

    subgraph UC_CHATBOT ["Chatbot & Widget"]
        UC16[Tạo Chatbot]
        UC17[Cấu hình Chatbot]
        UC18[Lấy embed code]
        UC19[Xem conversations]
        UC20[Xem analytics]
    end

    subgraph UC_CHAT ["Chat \(Public\)"]
        UC21[Gửi tin nhắn\n→ SSE stream]
        UC22[Xem lịch sử chat]
        UC23[Gửi feedback\n👍👎]
    end

    subgraph UC_BILLING ["Billing"]
        UC24[Xem plans]
        UC25[Upgrade / Checkout]
        UC26[Quản lý subscription]
        UC27[Xem hóa đơn]
        UC28[Xử lý webhook\nStripe]
    end

    subgraph UC_ADMIN ["Super Admin"]
        UC29[Xem tất cả businesses]
        UC30[Suspend business]
        UC31[Impersonate tenant]
    end

    %% Owner
    OWNER --> UC1
    OWNER --> UC2
    OWNER --> UC3
    OWNER --> UC4
    OWNER --> UC5
    OWNER --> UC6
    OWNER --> UC8
    OWNER --> UC9
    OWNER --> UC10
    OWNER --> UC11
    OWNER --> UC12
    OWNER --> UC13
    OWNER --> UC14
    OWNER --> UC15
    OWNER --> UC16
    OWNER --> UC17
    OWNER --> UC18
    OWNER --> UC19
    OWNER --> UC20
    OWNER --> UC24
    OWNER --> UC25
    OWNER --> UC26
    OWNER --> UC27

    %% Admin
    ADMIN --> UC2
    ADMIN --> UC6
    ADMIN --> UC10
    ADMIN --> UC11
    ADMIN --> UC12
    ADMIN --> UC13
    ADMIN --> UC14
    ADMIN --> UC15
    ADMIN --> UC16
    ADMIN --> UC17
    ADMIN --> UC18
    ADMIN --> UC19
    ADMIN --> UC20

    %% Member
    MEMBER --> UC2
    MEMBER --> UC10
    MEMBER --> UC13
    MEMBER --> UC19

    %% End User
    ENDUSER --> UC7
    ENDUSER --> UC21
    ENDUSER --> UC22
    ENDUSER --> UC23

    %% Super Admin
    SUPERADMIN --> UC29
    SUPERADMIN --> UC30
    SUPERADMIN --> UC31

    %% Stripe
    STRIPE --> UC28
```

---

## 2. Class Diagram

> Tập trung vào **Service layer** và các quan hệ chính. Entity classes được mô tả trong ER Diagram.

```mermaid
classDiagram

    %% ── Auth ──────────────────────────────────────────
    class AuthService {
        +signup(SignupRequest) : AuthResponse
        +login(LoginRequest) : AuthResponse
        +refresh(String refreshToken) : AuthResponse
        +logout(String refreshToken) void
        +forgotPassword(String email) void
        +resetPassword(ResetPasswordRequest) void
        +verifyEmail(String token) void
    }

    class JwtService {
        +generateAccessToken(Member) : String
        +generateRefreshToken(Member) : String
        +validateToken(String) : JwtClaims
        +isTokenRevoked(String jti) : boolean
        -storeRefreshTokenHash(UUID memberId, String token) void
    }

    class JwtAuthFilter {
        +doFilterInternal(request, response, chain) void
        -extractToken(request) : Optional~String~
    }

    class TenantContext {
        <<utility>>
        -currentTenant : ThreadLocal~UUID~
        +setTenantId(UUID) void
        +getTenantId() : UUID
        +clear() void
    }

    class TenantContextCopyingDecorator {
        +decorate(Runnable) : Runnable
    }

    AuthService --> JwtService
    JwtAuthFilter --> JwtService
    JwtAuthFilter --> TenantContext

    %% ── Member ────────────────────────────────────────
    class MemberService {
        +listMembers() : List~MemberDto~
        +inviteMember(InviteRequest) void
        +acceptInvitation(String token, String password) void
        +removeMember(UUID memberId) void
        +changeRole(UUID memberId, Role role) void
    }

    class InvitationService {
        +sendInvitation(UUID businessId, String email, Role) void
        +accept(String token, String password) : Member
        +validateToken(String token) : Invitation
    }

    MemberService --> InvitationService

    %% ── Knowledge Base ────────────────────────────────
    class KnowledgeBaseService {
        +create(CreateKbRequest) : KnowledgeBaseDto
        +list() : List~KnowledgeBaseDto~
        +getById(UUID) : KnowledgeBaseDto
        +update(UUID, UpdateKbRequest) : KnowledgeBaseDto
        +delete(UUID) void
        -enforceKbLimit() void
    }

    class DocumentService {
        +upload(UUID kbId, MultipartFile) : DocumentDto
        +ingestUrl(UUID kbId, String url) : DocumentDto
        +list(UUID kbId) : List~DocumentDto~
        +getById(UUID) : DocumentDto
        +delete(UUID) void
        +reindex(UUID) void
        +listChunks(UUID docId) : List~ChunkDto~
    }

    class DocumentProcessor {
        <<async>>
        +process(UUID documentId) void
        -parse(Document) : String
        -chunk(String content) : List~ChunkData~
        -embed(List~ChunkData~) : List~Chunk~
        -recover() void
    }

    class ChunkingService {
        +chunk(String content, DocumentType) : List~ChunkData~
        -splitParagraphs(String) : List~String~
        -preserveTables(String) : List~String~
        -preserveCodeBlocks(String) : List~String~
    }

    class EmbeddingService {
        +embed(List~String~ texts) : List~float[]~
    }

    class StorageService {
        +upload(MultipartFile) : String
        +delete(String key) void
        +getDownloadUrl(String key) : String
    }

    DocumentService --> StorageService
    DocumentService --> DocumentProcessor
    DocumentProcessor --> ChunkingService
    DocumentProcessor --> EmbeddingService
    KnowledgeBaseService --> DocumentService

    %% ── Chat ──────────────────────────────────────────
    class ChatService {
        +streamChat(UUID chatbotId, ChatRequest, SseEmitter) void
        +getHistory(UUID chatbotId, String sessionId) : List~MessageDto~
        +submitFeedback(FeedbackRequest) void
        -retrieveContext(String query, UUID kbId) : List~Chunk~
        -buildPrompt(List~Chunk~, List~Message~) : String
        -trackUsage(UUID businessId, int tokens, BigDecimal cost) void
    }

    class HybridSearchService {
        +search(String query, UUID kbId, int topK) : List~Chunk~
        -vectorSearch(float[] embedding, UUID kbId, int k) : List~RankedChunk~
        -keywordSearch(String query, UUID kbId, int k) : List~RankedChunk~
        -reciprocalRankFusion(List, List, int k) : List~Chunk~
    }

    class ConversationService {
        +getOrCreate(UUID chatbotId, String sessionId) : Conversation
        +appendMessage(UUID convId, Role, String content) : Message
        +summarizeIfNeeded(UUID convId) void
        +getHistory(UUID convId) : List~Message~
    }

    class SessionService {
        +createSession(UUID chatbotId) : String
        +validateSession(String sessionToken) : SessionInfo
        +cleanupExpiredSessions() void
    }

    ChatService --> HybridSearchService
    ChatService --> ConversationService
    ChatService --> EmbeddingService
    ChatService --> SessionService

    %% ── Chatbot ───────────────────────────────────────
    class ChatbotService {
        +create(CreateChatbotRequest) : ChatbotDto
        +list() : List~ChatbotDto~
        +getById(UUID) : ChatbotDto
        +update(UUID, UpdateChatbotRequest) : ChatbotDto
        +delete(UUID) void
        +getWidgetConfig(UUID) : WidgetConfigDto
    }

    class OriginValidationFilter {
        +doFilter(request, response, chain) void
        -isOriginAllowed(String origin, Widget) : boolean
    }

    ChatService --> ChatbotService
    OriginValidationFilter --> ChatbotService

    %% ── Billing ───────────────────────────────────────
    class BillingService {
        +listPlans() : List~PlanDto~
        +getSubscription() : SubscriptionDto
        +createCheckoutSession(String planSlug) : String
        +createPortalSession() : String
        +listInvoices() : List~InvoiceDto~
    }

    class StripeWebhookHandler {
        +handle(String payload, String signature) void
        -onCheckoutCompleted(Event) void
        -onPaymentSucceeded(Event) void
        -onPaymentFailed(Event) void
        -onSubscriptionUpdated(Event) void
        -onSubscriptionDeleted(Event) void
        -onTrialWillEnd(Event) void
        -isAlreadyProcessed(String eventId) : boolean
    }

    class SubscriptionService {
        +activate(UUID businessId, String stripePlanId) void
        +cancel(UUID businessId) void
        +downgrade(UUID businessId, UUID newPlanId) void
        +softFreeze(UUID businessId) void
        -enforcePlanLimits(UUID businessId, Plan) void
    }

    BillingService --> SubscriptionService
    StripeWebhookHandler --> SubscriptionService

    %% ── Quota ─────────────────────────────────────────
    class QuotaService {
        +checkAndIncrement(UUID businessId, Metric) void
        +getCurrentUsage(UUID businessId) : UsageSummary
        +getHistory(UUID businessId) : List~MonthlyUsage~
    }

    ChatService --> QuotaService
    DocumentService --> QuotaService
    MemberService --> QuotaService
    KnowledgeBaseService --> QuotaService

    %% ── Admin ─────────────────────────────────────────
    class AdminService {
        +listBusinesses(Pageable) : Page~BusinessDto~
        +getBusinessById(UUID) : BusinessDto
        +suspend(UUID businessId) void
        +createImpersonationToken(UUID businessId) : String
    }

    AdminService --> SubscriptionService

    %% ── Audit ─────────────────────────────────────────
    class AuditLogService {
        <<async>>
        +log(AuditEvent) void
    }

    AuthService --> AuditLogService
    MemberService --> AuditLogService
    BillingService --> AuditLogService
    AdminService --> AuditLogService
```

---

## 3. Entity Relationship Diagram (ERD)

```mermaid
erDiagram

    BUSINESS {
        uuid id PK
        string name
        string slug UK
        uuid plan_id FK
        string stripe_customer_id
        timestamp suspended_at
        timestamp created_at
    }

    MEMBER {
        uuid id PK
        uuid business_id FK
        string email UK
        string password_hash
        string role
        boolean email_verified
        timestamp created_at
    }

    INVITATION {
        uuid id PK
        uuid business_id FK
        string email
        string role
        string token UK
        timestamp expires_at
        timestamp accepted_at
    }

    KNOWLEDGE_BASE {
        uuid id PK
        uuid business_id FK
        string name
        string description
        string status
        timestamp created_at
    }

    DOCUMENT {
        uuid id PK
        uuid knowledge_base_id FK
        string type
        string source
        string original_filename
        string status
        string error_message
        int retry_count
        string file_storage_key
        timestamp created_at
    }

    PROCESSING_JOB {
        uuid id PK
        uuid document_id FK
        uuid business_id FK
        string status
        timestamp created_at
        timestamp started_at
        timestamp completed_at
    }

    CHUNK {
        uuid id PK
        uuid document_id FK
        text content
        vector embedding
        int token_count
        int position
        string chunk_type
        jsonb metadata
    }

    CHATBOT {
        uuid id PK
        uuid knowledge_base_id FK
        string name
        text system_prompt
        jsonb settings
        timestamp created_at
    }

    WIDGET {
        uuid id PK
        uuid chatbot_id FK
        string[] allowed_domains
        string position
        jsonb theme
    }

    CONVERSATION {
        uuid id PK
        uuid chatbot_id FK
        string session_id
        jsonb user_metadata
        timestamp started_at
        timestamp ended_at
    }

    MESSAGE {
        uuid id PK
        uuid conversation_id FK
        string role
        text content
        int tokens_used
        decimal cost_usd
        jsonb sources
        timestamp created_at
    }

    PLAN {
        uuid id PK
        string name
        string slug UK
        decimal price_usd_monthly
        string stripe_price_id
        int max_knowledge_bases
        int max_documents_per_kb
        int max_messages_per_month
        int max_members
        string[] features
        boolean is_active
    }

    SUBSCRIPTION {
        uuid id PK
        uuid business_id FK
        uuid plan_id FK
        string stripe_subscription_id
        string status
        timestamp current_period_start
        timestamp current_period_end
        timestamp trial_ends_at
        timestamp canceled_at
    }

    USAGE_RECORD {
        uuid id PK
        uuid business_id FK
        string period
        string metric
        int value
    }

    AUDIT_LOG {
        uuid id PK
        uuid business_id FK
        uuid member_id FK
        uuid impersonator_id
        string action
        string resource_type
        uuid resource_id
        jsonb metadata
        timestamp created_at
    }

    API_KEY {
        uuid id PK
        uuid business_id FK
        string name
        string key_hash UK
        string scope
        timestamp last_used_at
        timestamp expires_at
        timestamp created_at
    }

    STRIPE_EVENT {
        uuid id PK
        string event_id UK
        string event_type
        timestamp processed_at
    }

    REFRESH_TOKEN {
        uuid id PK
        uuid member_id FK
        string token_hash UK
        timestamp expires_at
        timestamp revoked_at
        timestamp created_at
    }

    %% Relationships
    BUSINESS ||--o{ MEMBER : "has"
    BUSINESS ||--o{ INVITATION : "sends"
    BUSINESS ||--o{ KNOWLEDGE_BASE : "owns"
    BUSINESS ||--|| SUBSCRIPTION : "has"
    BUSINESS ||--o{ USAGE_RECORD : "tracks"
    BUSINESS ||--o{ AUDIT_LOG : "logs"
    BUSINESS ||--o{ API_KEY : "has"

    PLAN ||--o{ SUBSCRIPTION : "used by"

    KNOWLEDGE_BASE ||--o{ DOCUMENT : "contains"
    KNOWLEDGE_BASE ||--o| CHATBOT : "powers"

    DOCUMENT ||--o{ CHUNK : "split into"
    DOCUMENT ||--o| PROCESSING_JOB : "tracked by"

    CHATBOT ||--o| WIDGET : "embeds as"
    CHATBOT ||--o{ CONVERSATION : "has"

    CONVERSATION ||--o{ MESSAGE : "contains"

    MEMBER ||--o{ REFRESH_TOKEN : "has"
    MEMBER ||--o{ AUDIT_LOG : "performs"
```

---

## 4. Sequence Diagram — Chat Flow (SSE Streaming)

```mermaid
sequenceDiagram
    actor User as End User (Browser)
    participant Widget as Vanilla JS Widget
    participant API as Chat API
    participant Origin as OriginValidationFilter
    participant Quota as QuotaService
    participant Search as HybridSearchService
    participant LLM as Anthropic Claude API
    participant DB as PostgreSQL

    User->>Widget: Nhập tin nhắn
    Widget->>API: POST /api/v1/chat/{chatbotId}\n{ sessionId, message }
    API->>Origin: Check Origin header
    Origin->>DB: SELECT allowed_domains WHERE chatbot_id = ?
    alt Origin không hợp lệ
        Origin-->>Widget: 403 Forbidden
    end

    API->>Quota: checkAndIncrement(businessId, MESSAGES_SENT)
    Quota->>DB: UPDATE usage_records SET value = value + 1\nWHERE value < plan_limit
    alt Quota vượt limit
        Quota-->>Widget: 403 QuotaExceeded + upgrade URL
    end

    API->>DB: GET Conversation by sessionId\n(create if not exists)
    API->>Search: search(query, kbId, topK=10)
    Search->>DB: Vector search Top-10\n+ Keyword search Top-10
    Search-->>API: Top-5 chunks (sau RRF)

    API->>DB: GET conversation history\n(summarize nếu > 8 messages)
    API->>LLM: ChatCompletionRequest\n{ systemPrompt, chunks, history, query }

    loop Streaming tokens
        LLM-->>API: Token chunk
        API-->>Widget: SSE: data: {"token": "..."}
    end

    LLM-->>API: [DONE] + usage stats
    API->>DB: INSERT Message (role=ASSISTANT)\ntrack tokens + cost
    API-->>Widget: SSE: data: {"done": true, "sources": [...]}
    Widget->>User: Hiển thị response hoàn chỉnh
```

---

## 5. Sequence Diagram — Document Processing Pipeline

```mermaid
sequenceDiagram
    actor Admin as Admin User
    participant API as Document API
    participant Storage as Cloud Storage\n(R2/S3)
    participant DB as PostgreSQL
    participant Queue as Async Thread Pool
    participant Tika as Apache Tika
    participant Embed as OpenAI Embeddings API

    Admin->>API: POST /knowledge-bases/{kbId}/documents\n(multipart file)
    API->>API: Validate: size ≤ 10MB, type allowed
    API->>Storage: Upload file → get storage_key
    API->>DB: INSERT Document (status=PENDING)\nINSERT ProcessingJob (status=QUEUED)
    API-->>Admin: 202 Accepted { documentId }

    Note over Queue: Async processing starts

    Queue->>DB: UPDATE ProcessingJob status=RUNNING\nUPDATE Document status=PROCESSING
    Queue->>Storage: Download file by storage_key
    Queue->>Tika: Parse file → extracted text
    Queue->>Queue: Structure-aware chunking\n(preserve tables, code, headers)

    loop Batch embedding (20 chunks per call)
        Queue->>Embed: POST /embeddings\n{ texts: [...20 chunks...] }
        Embed-->>Queue: vectors[]
    end

    Queue->>DB: INSERT chunks với embeddings\nUPDATE Document status=INDEXED\nUPDATE ProcessingJob status=COMPLETED

    alt Processing fails
        Queue->>DB: UPDATE retry_count++\nSchedule retry (backoff: 1s, 4s, 16s)
        alt retry_count >= 3
            Queue->>DB: UPDATE Document status=FAILED\nSET error_message = human-readable
            Queue->>Admin: Email notification: "Document failed"
        end
    end
```

---

## 6. Sequence Diagram — Stripe Subscription Flow

```mermaid
sequenceDiagram
    actor Owner as Business Owner
    participant API as Billing API
    participant Stripe as Stripe
    participant Webhook as Webhook Handler
    participant DB as PostgreSQL
    participant Email as Email Service

    Owner->>API: POST /billing/checkout-session\n{ planSlug: "pro" }
    API->>Stripe: Create Checkout Session\n(customer_id, price_id, success_url)
    Stripe-->>API: { url: "https://checkout.stripe.com/..." }
    API-->>Owner: Redirect to Stripe Checkout

    Owner->>Stripe: Thanh toán thành công
    Stripe->>Webhook: POST /billing/webhook\nEvent: checkout.session.completed\n+ Stripe-Signature header

    Webhook->>Webhook: Verify Stripe-Signature
    Webhook->>DB: INSERT stripe_events (event_id)\n→ Duplicate key = skip (idempotent)
    Webhook->>DB: UPDATE subscription\n{ status=ACTIVE, plan_id=pro }
    Webhook->>Email: Send "Upgrade successful" email (async)
    Webhook-->>Stripe: 200 OK

    Note over Stripe,Webhook: Stripe retries if no 200 →\nidempotency prevents double-processing
```

---

*Diagrams được generate từ PRD.md và SYSTEM_DESIGN_ANALYSIS.md — 2026-05-07*
