# Customer Support AI Platform - Project Specification

> **Working title:** Customer Support AI Platform
> **Repository name:** `spring-saas-support-ai`
> **Tagline:** Open-source AI customer support platform for SMBs
> **One-liner:** White-label chatbot trained on your docs, embeddable anywhere in 5 minutes.
> **Position:** Open-source alternative to Intercom AI

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Goals & Strategy](#2-goals--strategy)
3. [Target Users](#3-target-users)
4. [Domain Model](#4-domain-model)
5. [Plan Tiers](#5-plan-tiers)
6. [API Design](#6-api-design)
7. [Tech Stack](#7-tech-stack)
8. [Architecture Decisions](#8-architecture-decisions)
9. [4-Milestone Roadmap](#9-4-milestone-roadmap)
10. [Success Metrics](#10-success-metrics)
11. [Sellability Analysis](#11-sellability-analysis)
12. [Hard Constraints](#12-hard-constraints)
13. [Action Plan](#13-action-plan)

---

## 1. Project Overview

### Why this project exists

This project serves **two strategic goals**:

1. **Job hunting (Priority #1):** Demonstrate senior Java backend + AI integration skills through a substantial, production-grade SaaS project that recruiters and clients can verify.
2. **Passive income (Bonus):** Build an asset that can later be monetized through open-source authority, template sales, hosted SaaS, or consulting opportunities.

### What makes this project different

Unlike generic "Spring Boot starters" that nobody uses (because every project needs customization for its business domain), this project is a **complete vertical solution** for a real business problem: AI customer support for SMBs.

The 4-milestone structure allows the project to:
- Generate **multiple portfolio entries** from one codebase
- Be **standalone deployable at every milestone** (not "wait until done")
- **Apply for jobs immediately** after each milestone in different categories
- Provide **reusable infrastructure** for future freelance client work

### Core differentiation vs. existing solutions

| Solution | Approach | Gap this fills |
|----------|----------|----------------|
| Intercom, Crisp, Zendesk AI | SaaS, $$$, locked-in | Open-source, self-hostable |
| `urosengineer/saas-backend-starter` | Generic SaaS starter | Specific business vertical |
| Spring AI examples | Toy demos | Production multi-tenant SaaS |
| Generic boilerplates | Need heavy customization | Complete working product |

---

## 2. Goals & Strategy

### Primary goals

| # | Goal | Priority | Success metric |
|---|------|----------|----------------|
| 1 | Land better job (AI Application Engineer with Java) | High | Job offers within 8-12 weeks |
| 2 | Generate freelance income | Medium | First client within 6-8 weeks |
| 3 | Build open-source authority | Low | 100+ GitHub stars in 3 months |

### Strategic positioning

**Job market positioning:**
- "Senior Java/Spring Boot Developer with AI Integration expertise"
- Target: enterprises and SaaS companies adding AI features
- Lower competition than pure Python AI roles; senior Java is rare

**Freelance positioning:**
- "I build production AI features for Java SaaS"
- Target: small businesses and agencies on Upwork ($500-10k budget tier)
- Differentiator: I built this open-source platform, can implement similar for you

### Why this strategy works

1. **Shows range:** Multi-tenancy + billing + AI = covers many job categories
2. **Shows depth:** Real production patterns, not toy demos
3. **Shows execution:** Shipping incrementally proves discipline
4. **Shows business sense:** Solving real problem, not vanity project
5. **Compounds over time:** Each milestone adds portfolio depth

---

## 3. Target Users

### End users of the platform (those who would use the SaaS)

**Primary persona:** SMB founders/teams (5-50 employees)
- **Pain:** Customer support is expensive and time-consuming
- **Want:** AI chatbot that answers common questions, escalates complex ones
- **Why open-source:** Avoid Intercom's $300+/month pricing, want data ownership

**Secondary persona:** Developers building support solutions
- **Pain:** Want to build similar feature for their company/clients
- **Want:** Production-ready code to study or fork
- **Value:** Save weeks of architecture work

**Tertiary persona:** Agencies serving SMB clients
- **Pain:** Building custom support tools for each client is unprofitable
- **Want:** Reusable foundation to customize per client
- **Value:** White-label and resell

### Stakeholders for portfolio purposes

**Recruiters/Hiring managers:**
- Want to see: production patterns, architecture decisions, code quality
- Will look at: GitHub repo structure, README quality, commit history, live demo

**Upwork clients:**
- Want to see: working examples relevant to their needs
- Will look at: portfolio entries, demo videos, case studies

---

## 4. Domain Model

### Core entities

```
Business (Tenant)
  Fields: id, name, slug, plan_id, stripe_customer_id, created_at, suspended_at
  Relations:
    - has_many: Members
    - has_many: KnowledgeBases
    - has_one: Subscription
    - has_many: ApiKeys
    - has_many: AuditLogs

Member (User in tenant)
  Fields: id, business_id, email, password_hash, role, email_verified, created_at
  Constraints: unique(business_id, email)
  Roles: OWNER | ADMIN | MEMBER

Invitation
  Fields: id, business_id, email, role, token, expires_at, accepted_at

KnowledgeBase
  Fields: id, business_id, name, description, status, created_at
  Status: ACTIVE | ARCHIVED
  Relations:
    - has_many: Documents
    - has_one: Chatbot

Document
  Fields: id, knowledge_base_id, type, source, original_filename, status, error_message, created_at
  Type: PDF | MARKDOWN | URL | TEXT | DOCX
  Status: PENDING | PROCESSING | INDEXED | FAILED
  Relations:
    - has_many: Chunks

Chunk (Vector store)
  Fields: id, document_id, content, embedding (vector(1536)), token_count, position, metadata
  Index: vector index on embedding for similarity search

Chatbot
  Fields: id, knowledge_base_id, name, system_prompt, settings (JSON), created_at
  Settings:
    - theme_color, welcome_message, placeholder_text
    - model (claude-3-5-sonnet, claude-3-haiku)
    - temperature (0.0 - 1.0)
    - max_response_tokens
  Relations:
    - has_many: Conversations
    - has_one: Widget

Conversation
  Fields: id, chatbot_id, session_id, user_metadata (JSON), started_at, ended_at
  Note: session_id allows anonymous users to maintain context
  Relations:
    - has_many: Messages

Message
  Fields: id, conversation_id, role, content, tokens_used, cost_usd, sources (JSON), created_at
  Roles: USER | ASSISTANT | SYSTEM
  Sources: array of cited chunk_ids with relevance scores

Widget
  Fields: id, chatbot_id, allowed_domains, position, theme, custom_css
  Position: BOTTOM_RIGHT | BOTTOM_LEFT
  Allows: 1-line embed code on customer websites

Subscription (Stripe-backed)
  Fields: id, business_id, plan_id, stripe_subscription_id
  Status: TRIALING | ACTIVE | PAST_DUE | CANCELED | INCOMPLETE
  Period: current_period_start, current_period_end, trial_ends_at, canceled_at

Plan (configurable)
  Fields: id, name, slug, price_usd_monthly, stripe_price_id, limits (JSON), is_active
  Limits:
    - max_knowledge_bases
    - max_documents_per_kb
    - max_messages_per_month
    - max_members
    - features: [custom_branding, api_access, priority_support]

UsageRecord
  Fields: id, business_id, period (YYYY-MM), metric, value, created_at
  Metrics:
    - messages_sent
    - tokens_consumed
    - documents_indexed
    - api_calls

AuditLog
  Fields: id, business_id, member_id, action, resource_type, resource_id, metadata, created_at
  Actions: LOGIN, INVITE_MEMBER, CHANGE_PLAN, DELETE_KB, etc.

ApiKey (for external integrations)
  Fields: id, business_id, name, key_hash, last_used_at, expires_at, created_at
```

### Key relationships diagram

```
Business 1───* Member
Business 1───* KnowledgeBase 1───* Document 1───* Chunk
Business 1───1 Subscription *───1 Plan
KnowledgeBase 1───1 Chatbot 1───* Conversation 1───* Message
Chatbot 1───1 Widget
Business 1───* UsageRecord
Business 1───* AuditLog
```

---

## 5. Plan Tiers

### Default plan configuration (seedable)

| Plan | Price | KB | Docs/KB | Messages/mo | Members | Features |
|------|-------|------|---------|-------------|---------|----------|
| **Free** | $0 | 1 | 5 | 100 | 1 | Basic |
| **Starter** | $29/mo | 3 | 50 | 1,000 | 3 | Custom branding |
| **Pro** | $99/mo | 10 | 500 | 10,000 | 10 | API access, priority support |
| **Business** | $299/mo | Unlimited | Unlimited | 100,000 | Unlimited | All features, SSO |

### Pricing rationale

- **Free:** Generous enough to test, limited enough to upgrade
- **Starter:** Sweet spot for solo founders / very small teams
- **Pro:** Most SMBs land here (10-50 person companies)
- **Business:** Larger orgs with compliance/scale needs

### Trial strategy

- 14-day free trial of Pro plan on signup
- No credit card required for trial
- Auto-downgrade to Free after trial if no upgrade

---

## 6. API Design

### Authentication & Tenant management

```
POST   /api/v1/auth/signup              # Create business + owner (returns JWT)
POST   /api/v1/auth/login               # Email/password (returns JWT + refresh)
POST   /api/v1/auth/refresh             # Exchange refresh token
POST   /api/v1/auth/logout              # Revoke tokens
POST   /api/v1/auth/forgot-password     # Send reset email
POST   /api/v1/auth/reset-password      # Reset with token
POST   /api/v1/auth/verify-email        # Verify email address
GET    /api/v1/auth/me                  # Current user info
```

### Member management

```
GET    /api/v1/members                  # List members in current tenant
POST   /api/v1/members/invite           # Send invitation email
POST   /api/v1/invitations/accept       # Accept invitation (with token)
DELETE /api/v1/members/{id}             # Remove member
PATCH  /api/v1/members/{id}/role        # Change role
```

### Knowledge Base management

```
GET    /api/v1/knowledge-bases          # List KBs
POST   /api/v1/knowledge-bases          # Create KB
GET    /api/v1/knowledge-bases/{id}     # Get KB details
PATCH  /api/v1/knowledge-bases/{id}     # Update KB
DELETE /api/v1/knowledge-bases/{id}     # Delete KB (cascades)
```

### Document management

```
POST   /api/v1/knowledge-bases/{kbId}/documents       # Upload document
GET    /api/v1/knowledge-bases/{kbId}/documents       # List documents
GET    /api/v1/documents/{id}                         # Document details + status
DELETE /api/v1/documents/{id}                         # Delete document
POST   /api/v1/documents/{id}/reindex                 # Trigger reindexing
GET    /api/v1/documents/{id}/chunks                  # View chunks (debug)
```

### Chatbot management

```
GET    /api/v1/chatbots                 # List chatbots
POST   /api/v1/chatbots                 # Create chatbot for KB
GET    /api/v1/chatbots/{id}            # Get chatbot
PATCH  /api/v1/chatbots/{id}            # Update settings
DELETE /api/v1/chatbots/{id}            # Delete chatbot
GET    /api/v1/chatbots/{id}/widget-config  # Get embed config
```

### Public chat endpoints (used by widget)

```
POST   /api/v1/chat/{chatbotId}                       # Stream response (SSE)
GET    /api/v1/chat/{chatbotId}/history/{sessionId}   # Get conversation history
POST   /api/v1/chat/{chatbotId}/feedback              # Submit feedback (thumbs up/down)
```

### Conversation management (admin)

```
GET    /api/v1/conversations            # Paginated list (with filters)
GET    /api/v1/conversations/{id}       # Get conversation
GET    /api/v1/conversations/analytics  # Aggregated stats
```

### Billing & Subscription

```
GET    /api/v1/billing/plans                    # List available plans
GET    /api/v1/billing/subscription             # Current subscription
POST   /api/v1/billing/checkout-session         # Create Stripe Checkout
POST   /api/v1/billing/portal-session           # Create Customer Portal session
POST   /api/v1/billing/webhook                  # Stripe webhook handler (public)
GET    /api/v1/billing/invoices                 # Invoice history
```

### Usage & Limits

```
GET    /api/v1/usage/current                    # Current period usage
GET    /api/v1/usage/history                    # Historical usage
```

### Admin (super-admin only)

```
GET    /api/v1/admin/businesses                 # List all businesses
GET    /api/v1/admin/businesses/{id}            # Business details
POST   /api/v1/admin/businesses/{id}/suspend    # Suspend business
POST   /api/v1/admin/impersonate/{businessId}   # Login as business (support)
```

---

## 7. Tech Stack

### Final tech decisions

| Layer | Technology | Reason |
|-------|------------|--------|
| Java version | 21 (LTS) | Modern, supported until 2031 |
| Framework | Spring Boot 3.3.x | Industry standard, mature |
| Build tool | Maven | Familiar, simple |
| Database | PostgreSQL 16 | Standard, supports vectors |
| Vector store | PgVector extension | Same DB, no extra infra |
| Migrations | Flyway | Battle-tested |
| AI framework | Spring AI 1.1+ | Official Spring project |
| Primary LLM | Anthropic Claude | Strength match |
| Embeddings | OpenAI text-embedding-3-small | Cheap, good quality |
| Auth library | jjwt (JJWT) | Standard JWT library |
| Payment | Stripe Java SDK | Industry standard |
| Email service | Resend (or SMTP) | Developer-friendly |
| Async processing | Spring @Async + ThreadPool | Built-in, no Kafka needed yet |
| Caching | Redis (optional) | For session, rate limiting |
| Testing | JUnit 5 + Testcontainers | Real DB tests |
| API docs | springdoc-openapi | Auto-generated Swagger |
| Container | Docker (multi-stage) | Standard |
| CI/CD | GitHub Actions | Free, integrated |
| Hosting | Render / Railway | Free tier sufficient |
| Frontend (admin) | Minimal React (Claude-generated) | Just for demo |
| Frontend (widget) | Vanilla JS (no framework) | Embeddable anywhere |
| Observability | Micrometer + Prometheus | Standard |

### Avoiding common traps

**Don't add prematurely:**
- ❌ Kafka/RabbitMQ (use @Async until proven needed)
- ❌ Microservices (monolith first, split later if needed)
- ❌ ElasticSearch (PgVector + PG full-text is enough)
- ❌ Multiple databases (one Postgres for everything)
- ❌ Lombok (use Java 21 records and modern syntax)

**Do add when needed:**
- ✅ Redis: when Bucket4j needs distributed rate limiting
- ✅ S3: when document storage exceeds local disk
- ✅ CDN: when widget JS needs global distribution

---

## 8. Architecture Decisions

### Multi-tenancy strategy: Row-level isolation

**Choice:** Shared database, shared schema, with `tenant_id` on every business table.

**Why over alternatives:**

| Strategy | Cost | Isolation | Scalability | Verdict |
|----------|------|-----------|-------------|---------|
| Database per tenant | Very high | Strongest | Poor | Skip |
| Schema per tenant | High | Strong | Medium | Skip |
| **Row-level (chosen)** | **Low** | **Good w/ care** | **Excellent** | **Use** |

**Implementation:**
- All business tables have `tenant_id` column (FK to businesses)
- Hibernate filters auto-apply WHERE clause
- TenantContext (ThreadLocal) holds current tenant
- JwtAuthFilter sets context on every request
- Filter cleared at request end (prevent leaks)

**Critical safety measures:**
- Integration test specifically for tenant isolation
- Filter ALWAYS active (no opt-out for safety)
- Cross-tenant queries only via super-admin context
- Audit log every cross-tenant access

### Tenant context propagation

```java
public class TenantContext {
    private static final ThreadLocal<UUID> currentTenant = new ThreadLocal<>();
    
    public static void setTenantId(UUID tenantId) { currentTenant.set(tenantId); }
    public static UUID getTenantId() { return currentTenant.get(); }
    public static void clear() { currentTenant.remove(); }
}
```

**Set in:** JwtAuthFilter (after JWT validation)
**Cleared in:** Filter chain end + finally blocks
**Propagated to async:** Custom TaskDecorator copies context

### JWT structure

```json
{
  "sub": "user-uuid",
  "tenant_id": "tenant-uuid",
  "role": "ADMIN",
  "email": "user@business.com",
  "exp": 1234567890,
  "iat": 1234567000,
  "jti": "token-uuid"
}
```

**Access token:** 15 minutes
**Refresh token:** 7 days, stored as hash in DB for revocation
**Algorithm:** HS256 (symmetric, simpler) or RS256 (asymmetric, for API access)

### RAG retrieval strategy

**Hybrid approach:**
1. **Vector search:** Top-K (k=10) by cosine similarity
2. **Keyword search:** PG full-text search
3. **Reciprocal Rank Fusion:** Combine both rankings
4. **Re-ranking:** Optional with cross-encoder (skip in MVP)
5. **Final K:** Top 5 chunks → context for LLM

**Chunking strategy:**
- Default: paragraph-aware splitting, max 1000 tokens
- Overlap: 100 tokens between chunks
- Preserve: document hierarchy (chapter → section → paragraph)
- Metadata: source document, page number, position

### Async processing

**Use @Async for:**
- Document ingestion pipeline (upload → parse → chunk → embed)
- Email sending
- Webhook delivery
- Audit log writing

**Use synchronous for:**
- API calls (must respond)
- Chat responses (streaming via SSE)
- Auth operations

**Configuration:**
```java
@Bean
public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(100);
    executor.setTaskDecorator(new TenantContextCopyingDecorator());
    return executor;
}
```

### Streaming chat responses

Use Server-Sent Events (SSE) for streaming LLM responses:
- Better UX than waiting for complete response
- Spring AI supports streaming natively
- Compatible with browser EventSource API
- No WebSocket complexity needed

---

## 9. 4-Milestone Roadmap

### Milestone 1: Multi-tenant Foundation (Days 1-7)

**Goal:** Working multi-tenant SaaS skeleton with auth and member management.

**Day 1-2: Project setup**
- [ ] Spring Boot 3.3 + Java 21 project from start.spring.io
- [ ] Docker compose: postgres + adminer + mailhog
- [ ] Base package structure
- [ ] Application profiles (dev, prod, test)
- [ ] Initial Flyway migration setup

**Day 3-4: Multi-tenancy + Auth**
- [ ] Business + Member entities
- [ ] TenantContext (ThreadLocal)
- [ ] Hibernate filter for tenant isolation
- [ ] JwtService (with tenant_id claim)
- [ ] AuthController: signup creates Business + Owner
- [ ] AuthController: login, refresh, logout
- [ ] JwtAuthFilter sets TenantContext
- [ ] Password hashing with BCrypt

**Day 5-6: Member management**
- [ ] Member invitation flow (email-based)
- [ ] Accept invitation endpoint
- [ ] List members in tenant
- [ ] Remove member, change role
- [ ] **CRITICAL TEST:** Tenant isolation integration test

**Day 7: Polish + Deploy**
- [ ] GlobalExceptionHandler
- [ ] OpenAPI/Swagger configuration
- [ ] Deploy to Render free tier
- [ ] README v0.1
- [ ] **Release v0.1.0**
- [ ] Portfolio entry #1 on Upwork
- [ ] Blog post: "Building Multi-tenant SaaS in Spring Boot 3"

**Apply jobs targeting:** "Multi-tenant Spring Boot", "SaaS architecture", "Spring Security"

---

### Milestone 2: Knowledge Base + Document Processing (Days 8-14)

**Goal:** Working RAG pipeline - upload documents, get them indexed in vector store.

**Day 8-9: KB CRUD**
- [ ] KnowledgeBase + Document entities
- [ ] PgVector extension setup in DB
- [ ] Chunk entity with vector column
- [ ] KB CRUD endpoints
- [ ] Document upload (multipart)
- [ ] Apache Tika integration

**Day 10-11: Embedding pipeline**
- [ ] Document parsing (PDF, MD, DOCX, TXT)
- [ ] Chunking strategy (paragraph-aware, 1000 tokens)
- [ ] Async embedding generation
- [ ] Status tracking (PENDING → PROCESSING → INDEXED → FAILED)
- [ ] Error handling + retry logic

**Day 12-13: Search & management**
- [ ] Hybrid search (vector + keyword)
- [ ] Document deletion (cascade chunks)
- [ ] Reindex endpoint
- [ ] Plan limit enforcement (max docs/KB)

**Day 14: Polish + Deploy**
- [ ] Tests for ingestion pipeline
- [ ] Update README + add architecture diagram
- [ ] Deploy v0.2 to Render
- [ ] **Release v0.2.0**
- [ ] Portfolio entry #2
- [ ] Blog post: "Production RAG Pipeline with Spring AI and PgVector"

**Apply jobs targeting:** "RAG implementation", "Document processing", "Spring AI", "Vector search"

---

### Milestone 3: AI Chat + Embeddable Widget (Days 15-21)

**Goal:** End-to-end chat experience with embeddable widget.

**Day 15-16: Chat backend**
- [ ] Chatbot entity with settings
- [ ] Conversation + Message entities
- [ ] Chat endpoint with streaming (SSE)
- [ ] RAG retrieval logic (top-K → context)
- [ ] Source citation in responses

**Day 17-18: Conversation features**
- [ ] Session management (anonymous users)
- [ ] Conversation history endpoints
- [ ] Token usage tracking per message
- [ ] Cost calculation per message
- [ ] Feedback endpoints (thumbs up/down)

**Day 19-20: Embeddable widget**
- [ ] Widget config entity
- [ ] Vanilla JS widget (single file, < 50KB)
- [ ] CORS handling for embed
- [ ] Widget customization (theme, position)
- [ ] Demo page with embedded widget
- [ ] 1-line embed code generator

**Day 21: Polish + Deploy**
- [ ] End-to-end test of chat flow
- [ ] Update README with embed instructions
- [ ] **Release v0.3.0**
- [ ] Record 3-minute demo video
- [ ] Portfolio entry #3
- [ ] Blog post: "Building AI Chatbot with Spring AI and RAG"

**Apply jobs targeting:** "AI chatbot", "RAG", "Spring AI", "Claude integration", "Embeddable widget"

---

### Milestone 4: Billing + Production Polish (Days 22-28)

**Goal:** Production-ready SaaS with billing and full observability.

**Day 22-23: Stripe integration**
- [ ] Plan + Subscription entities
- [ ] Plan seed data (4 tiers)
- [ ] Stripe Checkout session creation
- [ ] Stripe Customer Portal integration
- [ ] Webhook handler (subscription events)
- [ ] Trial period logic

**Day 24-25: Usage & Quotas**
- [ ] UsageRecord tracking
- [ ] Quota enforcement interceptor
- [ ] Usage dashboard endpoints
- [ ] Plan upgrade/downgrade flow
- [ ] Cancellation flow

**Day 26-27: Production hardening**
- [ ] Rate limiting per tenant (Bucket4j)
- [ ] Comprehensive integration tests
- [ ] Micrometer metrics
- [ ] Security headers, CORS hardening
- [ ] Audit logging for sensitive actions
- [ ] Admin endpoints (super-admin role)

**Day 28: Final polish**
- [ ] Comprehensive README with everything
- [ ] Architecture diagram (Mermaid)
- [ ] Deployment guide
- [ ] Customization guide
- [ ] **Release v1.0.0**
- [ ] 5-minute demo video showing full flow
- [ ] Portfolio entry #4 (the showcase entry)
- [ ] Final blog post: "I Built a Production SaaS Backend in 4 Weeks"

**Apply jobs targeting:** "Senior Spring Boot", "Production SaaS", "Stripe integration", "DevOps"

---

## 10. Success Metrics

### After each milestone

- [ ] Live demo URL works end-to-end
- [ ] GitHub release tagged
- [ ] Blog post published
- [ ] LinkedIn post shared (build in public)
- [ ] Portfolio entry on Upwork
- [ ] 5-10 Upwork applications targeting milestone keywords

### After 4 weeks total

| Metric | Pessimistic | Realistic | Optimistic |
|--------|-------------|-----------|------------|
| Portfolio entries | 4 | 4 | 4 |
| GitHub stars | 5-20 | 50-200 | 500+ |
| Blog posts | 4 | 4 | 4-6 |
| Upwork applications | 20 | 30-40 | 50+ |
| Conversations with clients | 0-1 | 2-5 | 5-10 |
| Closed deals | 0 | 0-1 | 1-3 |
| Job interviews | 0-1 | 1-3 | 3-5 |
| Income | $0 | $0-1500 | $1500-5000 |

### Long-term success indicators (3-6 months)

- Personal brand established in Spring Boot + AI niche
- 1-3 freelance clients (recurring or one-off)
- Better job offer received (or current job satisfaction increased)
- Project becomes asset (sellable template, hosted SaaS, or consulting magnet)

---

## 11. Sellability Analysis

### Revenue paths after Milestone 4

#### Path A: Open-source first (recommended default)

- License: MIT, fully free
- Goal: Build authority + GitHub stars
- Indirect monetization:
  - Better job offers (this is Goal #1)
  - Freelance leads ("you built this, can you build for me?")
  - Speaking opportunities
  - Consulting referrals

**Effort:** Low (just maintain repo)
**Income:** $0 direct, but fuels other paths

#### Path B: OSS + Hosted version

- Free OSS for self-hosted
- Paid hosted version: $29-99/month
- Examples that work: Cal.com, Plausible, PostHog

**Effort:** High (infrastructure, support, marketing)
**Income:** Realistic $500-5000 MRR after 12 months

#### Path C: Sell as template

- $99-299 one-time license
- Targets developers building similar SaaS
- Less marketing, no recurring required

**Effort:** Medium (marketing only)
**Income:** Realistic $200-2000/month

#### Path D: Implementation service

- "I'll customize this for your business"
- $5k-15k per implementation
- Recurring maintenance contracts

**Effort:** Medium (sales + delivery)
**Income:** Realistic $5k-30k/month with 2-3 clients

### Recommendation

Don't decide on monetization now. Build first, see traction, decide based on signal.

**Default to Path A initially:** zero extra effort, supports primary goals.

---

## 12. Hard Constraints

### Time constraints (enforce strictly)

1. **5-7 days per milestone, hard cap**
   - Day 7 = release, no matter what
   - Skip features if running late
   - Better to ship lesser scope on time than perfect late

2. **Apply jobs after each milestone, don't wait**
   - Milestone 1 done → apply 5-10 jobs that week
   - Don't wait for v1.0.0
   - Use what you have at each stage

3. **Documentation per milestone, no skipping**
   - README updates
   - Blog post (even if rough draft)
   - Demo video clip

### Scope constraints (resist temptation)

**DO NOT add to scope:**
- ❌ Mobile app
- ❌ Native iOS/Android SDKs
- ❌ Voice interface
- ❌ Multi-language UI (English only)
- ❌ Advanced analytics dashboard
- ❌ Third-party integrations beyond Stripe
- ❌ Multiple LLM providers (Claude only at start)
- ❌ Self-hosted LLM support (cloud APIs only)

**DO add if found necessary:**
- ✅ Critical security fixes
- ✅ Bug fixes blocking demo
- ✅ Performance issues affecting UX

### Quality constraints

- Test coverage: 60%+ for service layer, 100% for tenant isolation
- All API endpoints documented in OpenAPI
- All sensitive operations audit-logged
- Rate limiting enabled in production
- Health checks working

### Decision constraints

- After commitment, NO PIVOTS without concrete blocker
- "I'm not sure" is not a blocker - ship and iterate
- Adjust based on DATA after Milestone 2 if needed, not speculation

---

## 13. Action Plan

### Today (1 hour total)

#### Step 1: Create GitHub repo (10 min)
- Name: `spring-saas-support-ai`
- Description: "Open-source AI customer support platform for SMBs - Spring Boot 3 + Spring AI"
- Public, MIT license
- Add `.gitignore` (Java template)
- Add basic README placeholder

#### Step 2: Generate Spring Boot project (15 min)
- Visit https://start.spring.io
- Configuration:
  - Project: Maven
  - Language: Java
  - Spring Boot: 3.3.x (latest stable)
  - Group: com.yourname
  - Artifact: spring-saas-support-ai
  - Java: 21
- Dependencies:
  - Spring Web
  - Spring Data JPA
  - Spring Security
  - Validation
  - PostgreSQL Driver
  - Flyway Migration
  - Spring Boot Actuator
  - Testcontainers
  - Docker Compose Support
- Generate, extract, push to GitHub

#### Step 3: Create GitHub issues for Milestone 1 (15 min)

Create these issues:
- `[M1] Setup Docker compose with PostgreSQL + Adminer + MailHog`
- `[M1] Create Business and Member entities + Flyway migrations`
- `[M1] Implement TenantContext + Hibernate filter`
- `[M1] JWT service with tenant_id claim`
- `[M1] Signup endpoint creates Business + Owner Member`
- `[M1] Login + refresh + logout endpoints`
- `[M1] Member invitation email flow`
- `[M1] Accept invitation endpoint`
- `[M1] Tenant isolation integration tests (CRITICAL)`
- `[M1] Global exception handling`
- `[M1] Deploy to Render free tier`
- `[M1] Write README v0.1`

#### Step 4: Block calendar (10 min)

Concrete time slots for Milestone 1:
- 5-7 sessions × 2-3 hours
- Suggest: weekday evenings 8-11 PM + weekend mornings
- Add to calendar with reminders

#### Step 5: Optional brand setup (10 min)

- Reserve domain (optional): supportai.dev, helperbot.io, etc.
- Setup Twitter/LinkedIn post: "Day 1: Building open-source AI customer support platform with Spring Boot 3"
- Create accountability commit by sharing publicly

### This week (Milestone 1)

Follow the Day 1-7 breakdown above. End of week 1:
- Live demo URL
- v0.1.0 released
- First portfolio entry posted on Upwork
- Apply 5 Upwork jobs

### After Milestone 1

Reflection points:
- Was 7 days realistic? Adjust if needed.
- Did anyone respond to portfolio? If yes, what works?
- Are you still motivated? If burning out, slow down.

If everything good → continue Milestone 2.
If problems → adjust based on actual data, don't pivot wildly.

---

## Appendix A: Decision Log

This document represents the final decisions after extensive discussion. Key pivots:

| Original idea | Final decision | Reason |
|---------------|----------------|--------|
| Build enricher SDK | Build full SaaS project | More valuable, sellable, demo-able |
| 10 portfolio projects | 1 substantial project, 4 milestones | Quality over quantity |
| Generic Spring Boot boilerplate | Customer Support AI Platform | Specific business domain |
| Project 1 (chatbot) standalone | Integrated as Milestone 3 of SaaS | Synergy with rest |
| Apply for 50+ Upwork jobs immediately | Apply incrementally with each milestone | Better targeting per milestone |

---

## Appendix B: Reference Resources

### Documentation to read

- Spring AI: https://docs.spring.io/spring-ai/reference/
- Spring Security: https://docs.spring.io/spring-security/reference/
- PgVector: https://github.com/pgvector/pgvector
- Stripe Java SDK: https://stripe.com/docs/api?lang=java
- Testcontainers: https://www.testcontainers.org/

### Similar projects to study (not copy)

- urosengineer/saas-backend-starter (Java SaaS starter)
- Cal.com (open-source SaaS done well)
- Plausible (analytics, self-hostable)
- Ghost (open-source CMS, hosted version available)

### Marketing channels to use

- Hacker News (Show HN posts)
- Reddit: r/SpringBoot, r/Java, r/SideProject
- Dev.to, Medium for blog posts
- Twitter/X (build in public)
- LinkedIn (professional positioning)
- Product Hunt (when v1.0 launches)

---

**Last updated:** Project specification finalized after thorough requirements analysis.
**Status:** Ready to start Milestone 1.
**Next action:** Create GitHub repo and begin Day 1 of Milestone 1.
