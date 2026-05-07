# Multi-Tenancy Architecture

> **Scope:** Row-level tenant isolation — TenantContext, Hibernate Filter, Async propagation  
> **Status:** Implemented in Milestone 1 (PR #13)  
> **Related issue:** [#3 Multi-tenancy Infrastructure](https://github.com/leonard-garden/spring-saas-support-ai/issues/3)

---

## 1. Strategy: Row-level Isolation

Three common SaaS multi-tenancy models:

| Model | Isolation | Cost | Complexity |
|-------|-----------|------|------------|
| DB per tenant | Strongest | Very high | High |
| Schema per tenant | Strong | High | Medium |
| **Row-level (chosen)** | **Good** | **Low** | **Low** |

**Decision:** Shared DB + shared schema. Every business table has a `business_id` column.
Hibernate automatically appends `WHERE business_id = ?` — developers never write this condition manually.

**Why not schema/DB per tenant:** Cost is prohibitive at MVP scale. Row-level is the industry standard for early-stage SaaS (Notion, Linear, etc. all started here).

---

## 2. High-Level Architecture

```mermaid
graph TB
    subgraph Internet
        UA[User A - Company A]
        UB[User B - Company B]
    end

    subgraph "Spring Boot App"
        F[JwtAuthFilter]
        TC["TenantContext<br/>ThreadLocal"]
        C[Controller]
        S[Service]
        R[Repository]
        FA["TenantFilterAspect<br/>AOP"]
        H[Hibernate ORM]
    end

    subgraph "PostgreSQL"
        T1[("members<br/>business_id = A")]
        T2[("members<br/>business_id = B")]
    end

    UA -->|Bearer JWT-A| F
    UB -->|Bearer JWT-B| F
    F -->|setTenantId| TC
    F --> C --> S --> R
    R -->|AOP intercept| FA
    FA -->|getTenantId| TC
    FA -->|enableFilter| H
    H -->|WHERE business_id = A| T1
    H -->|WHERE business_id = B| T2

    style TC fill:#f59e0b,color:#000
    style FA fill:#3b82f6,color:#fff
    style F fill:#10b981,color:#fff
```

---

## 3. Request Lifecycle

```mermaid
sequenceDiagram
    participant Client
    participant JwtAuthFilter
    participant TenantContext
    participant Controller
    participant TenantFilterAspect
    participant Repository
    participant Hibernate
    participant DB

    Client->>JwtAuthFilter: GET /members (Bearer JWT)
    JwtAuthFilter->>JwtAuthFilter: validateToken(jwt)
    JwtAuthFilter->>TenantContext: setTenantId("biz-A")

    JwtAuthFilter->>Controller: doFilter()
    Controller->>Repository: findAll()

    Note over TenantFilterAspect: AOP Before intercept
    TenantFilterAspect->>TenantContext: getTenantId()
    TenantContext-->>TenantFilterAspect: "biz-A"
    TenantFilterAspect->>Hibernate: enableFilter("tenantFilter", "biz-A")

    Hibernate->>DB: SELECT * FROM members WHERE business_id = 'biz-A'
    DB-->>Hibernate: [member rows for biz-A only]
    Hibernate-->>Repository: List<Member>
    Repository-->>Controller: List<Member>
    Controller-->>Client: 200 OK

    Note over JwtAuthFilter: finally block — always runs
    JwtAuthFilter->>TenantContext: clear()
```

---

## 4. ThreadLocal Scoping

`TenantContext` works identically to Spring's `RequestContextHolder` — both use `ThreadLocal` to scope data to the current HTTP thread.

```mermaid
graph LR
    subgraph "Thread Pool"
        direction TB
        T1["Thread 1<br/>tenantId = biz-A"]
        T2["Thread 2<br/>tenantId = biz-B"]
        T3["Thread 3<br/>tenantId = null"]
    end

    subgraph "ThreadLocal storage per thread"
        TL1[TL: biz-A]
        TL2[TL: biz-B]
        TL3[TL: empty]
    end

    T1 --- TL1
    T2 --- TL2
    T3 --- TL3

    R1[Request Company A] --> T1
    R2[Request Company B] --> T2
    R3[Public endpoint] --> T3
```

**Why `clear()` in `finally` is mandatory:**

```mermaid
sequenceDiagram
    participant Pool as Thread Pool
    participant T1 as Thread T1
    participant ReqA as Request: Company A
    participant ReqB as Request: Company B

    Pool->>T1: assign T1 to Request A
    ReqA->>T1: setTenantId("biz-A")
    ReqA->>T1: process...
    Note over T1: ❌ NO clear() — bug!
    T1->>Pool: return to pool

    Pool->>T1: assign T1 to Request B
    ReqB->>T1: getTenantId() → "biz-A" ← DATA LEAK!
    Note over T1: Company B sees Company A data
```

---

## 5. Async Thread Propagation

`@Async` runs on a **different thread** with an empty `ThreadLocal`. The `TenantContextCopyingDecorator` bridges this gap.

```mermaid
sequenceDiagram
    participant HTTP as HTTP Thread (T1)
    participant Decorator as TenantContextCopyingDecorator
    participant Pool as Async Thread Pool
    participant T2 as Async Thread (T2)

    HTTP->>HTTP: TenantContext = "biz-A"
    HTTP->>Decorator: submit Async task

    Note over Decorator: capture BEFORE hand-off
    Decorator->>Decorator: tenantId = TenantContext.get() → "biz-A"
    Decorator->>Decorator: wrap task with try/finally

    Decorator->>Pool: submit wrapped task
    Pool->>T2: assign thread

    T2->>T2: TenantContext.set("biz-A") ← from captured value
    T2->>T2: task.run()
    Note over T2: operates with correct tenant
    T2->>T2: finally: TenantContext.clear()
```

**Rule:** Always use `@Async("taskExecutor")` — bare `@Async` uses Spring's default executor which has no decorator.

```java
// CORRECT
@Async("taskExecutor")
public CompletableFuture<Void> processDocument(UUID docId) { ... }

// WRONG — default executor, no tenant propagation
@Async
public CompletableFuture<Void> processDocument(UUID docId) { ... }
```

---

## 6. Entity Hierarchy

```mermaid
classDiagram
    class TenantEntity {
        <<MappedSuperclass>>
        UUID businessId
        +FilterDef tenantFilter
        +Filter business_id = :tenantId
    }

    class Business {
        UUID id
        String name
        String slug
        String stripeCustomerId
        Instant suspendedAt
        Instant createdAt
        note: IS the tenant — does NOT extend TenantEntity
    }

    class Member {
        UUID id
        String email
        String passwordHash
        Role role
        boolean emailVerified
    }

    class Invitation {
        UUID id
        String email
        Role role
        String token
        Instant expiresAt
        Instant acceptedAt
    }

    class KnowledgeBase {
        UUID id
        String name
    }

    class Document {
        UUID id
        String filename
        DocumentStatus status
    }

    TenantEntity <|-- Member
    TenantEntity <|-- Invitation
    TenantEntity <|-- KnowledgeBase
    TenantEntity <|-- Document
    Business "1" --> "many" Member : owns
```

---

## 7. Admin Bypass Pattern

Super-admin endpoints need cross-tenant visibility. Only `Admin*` prefixed repositories are allowed to disable the filter.

```mermaid
flowchart TD
    A[Incoming Request] --> B{Is /admin/** path?}
    B -->|No — normal request| C[JwtAuthFilter sets TenantContext]
    B -->|Yes — super-admin| D[AdminAuthFilter validates SUPER_ADMIN role]

    C --> E[TenantFilterAspect enables tenantFilter]
    E --> F[Repository: scoped to one tenant]

    D --> G[AdminRepository explicitly disables tenantFilter]
    G --> H[Repository: sees ALL tenants]

    style G fill:#ef4444,color:#fff
    style F fill:#10b981,color:#fff
```

```java
// ONLY in Admin* repositories — anywhere else is a security violation
Session session = em.unwrap(Session.class);
session.disableFilter("tenantFilter");
```

---

## 8. Component Map

```mermaid
graph TB
    subgraph "auth package"
        JwtAuthFilter
        JwtService["JwtService (interface)"]
        JwtClaims["JwtClaims (record)"]
    end

    subgraph "tenant package"
        TenantContext
        TenantFilterAspect
        TenantContextCopyingDecorator
    end

    subgraph "common package"
        TenantEntity["TenantEntity (@MappedSuperclass)"]
    end

    subgraph "config package"
        AsyncConfig
        SecurityConfig
    end

    JwtAuthFilter --> JwtService
    JwtAuthFilter --> TenantContext
    JwtAuthFilter --> JwtClaims
    TenantFilterAspect --> TenantContext
    AsyncConfig --> TenantContextCopyingDecorator
    SecurityConfig --> JwtAuthFilter
```

---

## 9. Critical Rules

> Violations must be rejected in PR review — no exceptions.

| # | Rule | Consequence if broken |
|---|------|-----------------------|
| 1 | Every business entity MUST extend `TenantEntity` | Data from all tenants visible |
| 2 | `TenantContext.clear()` MUST be in `finally` | Tenant leak between requests |
| 3 | `@Async` MUST use `@Async("taskExecutor")` | Async tasks run without tenant |
| 4 | `disableFilter` ONLY in `Admin*` classes | Cross-tenant data exposure |
| 5 | `spring.threads.virtual.enabled=false` | ThreadLocal breaks with virtual threads |
| 6 | Service MUST call `TenantContext.getTenantId()` when **creating** entities | `business_id` saved as null → constraint fail or orphaned data |

## 10. TenantContext Usage in Service Layer

Hibernate filter handles **reads** automatically — service layer does not need `tenantId` for queries.

**Writes require explicit set:**

```java
// READ — no tenantId needed, filter applies automatically
public List<Member> listMembers() {
    return memberRepository.findAll(); // Hibernate adds: WHERE business_id = ?
}

// WRITE — must set businessId on new entity
public Member createMember(CreateMemberRequest req) {
    UUID tenantId = TenantContext.getTenantId(); // required
    Member member = new Member();
    member.setBusinessId(tenantId);              // must set before save
    member.setEmail(req.email());
    return memberRepository.save(member);
}
```

**Other cases that need `TenantContext.getTenantId()` in service:**

| Case | Reason |
|------|--------|
| Create any entity | Set `business_id` before `repository.save()` |
| Quota enforcement | `COUNT(*) WHERE business_id = ?` for plan limits |
| Audit logging | Record `tenantId` in audit entry |
| Cross-entity validation | Verify invitation token belongs to current tenant |
