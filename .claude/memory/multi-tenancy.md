# Multi-Tenancy Architecture

## Model: Row-level isolation (shared DB, shared schema)

Every business table has a `business_id` (UUID) column. Hibernate automatically
appends `WHERE business_id = :tenantId` to every query — developers never write
this condition manually.

---

## How tenantId flows through a request

```
HTTP Request arrives on Thread T1
         │
         ▼
JwtAuthFilter (OncePerRequestFilter)
   ├─ extract JWT from Authorization header
   ├─ validate token → get JwtClaims (userId, tenantId, role)
   ├─ TenantContext.setTenantId(claims.tenantId())   ← PUSH
   ├─ SecurityContextHolder.setAuthentication(...)
   └─ filterChain.doFilter(...)
              │
              ▼
         Controller
              │
              ▼
         Service
              │
              ▼
         Repository.findAll() / findById() / save()
              │
              ▼ (AOP intercepts before method runs)
   TenantFilterAspect
   ├─ TenantContext.getTenantId()                    ← PULL
   └─ session.enableFilter("tenantFilter", tenantId)
              │
              ▼
   Hibernate generates SQL:
   SELECT * FROM members WHERE business_id = 'abc-123'
              │
              ▼
         Response returned
              │
              ▼ (JwtAuthFilter finally block — NEVER skipped)
   TenantContext.clear()                             ← CLEAR
```

### Why clear() in finally is non-negotiable

Servlet containers reuse threads (thread pool). Without clearing:
- Thread T1 handles request for Company A → tenantId = "A"
- Request finishes, T1 returns to pool
- Next request for Company B picks up T1 → tenantId is still "A"
- **Data leak: Company B sees Company A's data**

---

## ThreadLocal analogy

`TenantContext` works exactly like Spring's `RequestContextHolder` — both use
`ThreadLocal` to scope data to the current HTTP thread. Spring stores
`HttpServletRequest`; we store `tenantId`.

```java
// Spring's pattern (familiar reference)
RequestContextHolder.getRequestAttributes()

// Our pattern (same mechanism)
TenantContext.getTenantId()
```

---

## Async threads — the trap

`@Async` methods run on a **different thread** from the HTTP thread. That new
thread has an empty ThreadLocal — it never ran through `JwtAuthFilter`.

**Solution: `TenantContextCopyingDecorator`**

```
HTTP Thread T1                      Async Thread T2
tenantId = "company-A"
   │
   └─ submits @Async task
         │
         ▼
   TenantContextCopyingDecorator.decorate(task)
   ├─ captures: tenantId = TenantContext.get()   ← captured BEFORE hand-off
   └─ wraps task:
         try {
             TenantContext.set(tenantId)          ← set on T2
             task.run()
         } finally {
             TenantContext.clear()                ← clear T2 after done
         }
```

**Rule:** All `@Async` methods MUST use the `taskExecutor` bean defined in
`AsyncConfig` — never the default Spring executor (it has no decorator).

---

## Entity hierarchy

```
TenantEntity  (@MappedSuperclass)
├─ business_id UUID  (non-null, non-updatable)
├─ @FilterDef(name = "tenantFilter", ...)
└─ @Filter(condition = "business_id = :tenantId")
      │
      ├─ Member
      ├─ Invitation
      ├─ KnowledgeBase
      ├─ Document
      ├─ Chatbot
      └─ (all business-data entities)

Business  (does NOT extend TenantEntity — it IS the tenant)
```

---

## Admin bypass (super-admin only)

Regular repositories use the Hibernate filter automatically.
Super-admin repositories must **explicitly disable** the filter:

```java
// ONLY in /admin/** code paths
@Repository
public class AdminMemberRepository {
    @PersistenceContext EntityManager em;

    public List<Member> findAllAcrossTenants() {
        Session session = em.unwrap(Session.class);
        session.disableFilter("tenantFilter");      // explicit disable
        return em.createQuery("FROM Member", Member.class).getResultList();
    }
}
```

**Rule:** `disableFilter` is only allowed in classes prefixed `Admin*`.
Any other usage is a security violation.

---

## Key files

| File | Role |
|------|------|
| `tenant/TenantContext.java` | ThreadLocal store — set/get/clear |
| `common/TenantEntity.java` | Base entity with business_id + Hibernate filter def |
| `tenant/TenantFilterAspect.java` | AOP: enables filter before every Repository call |
| `tenant/TenantContextCopyingDecorator.java` | Copies tenantId to async threads |
| `config/AsyncConfig.java` | Thread pool wired with the decorator |
| `auth/JwtAuthFilter.java` | Entry point: sets TenantContext from JWT, clears in finally |

---

## Service layer usage of TenantContext

Hibernate filter handles **reads** automatically — service does not need tenantId for queries.
**Writes MUST set businessId explicitly:**

```java
// READ — no tenantId needed
public List<Member> listMembers() {
    return memberRepository.findAll(); // filter auto-applies
}

// WRITE — must fetch and set
public Member createMember(CreateMemberRequest req) {
    UUID tenantId = TenantContext.getTenantId(); // required
    Member member = new Member();
    member.setBusinessId(tenantId);              // must set before save
    member.setEmail(req.email());
    return memberRepository.save(member);
}
```

Other cases needing `TenantContext.getTenantId()` in service: quota checks, audit logging, cross-entity validation.

---

## Critical rules (enforce on every PR)

1. Every business entity MUST extend `TenantEntity`
2. `TenantContext.clear()` MUST be in a `finally` block — no exceptions
3. `@Async` methods MUST declare `@Async("taskExecutor")` — not bare `@Async`
4. `disableFilter` is ONLY allowed in `Admin*` repository classes
5. `spring.threads.virtual.enabled=false` — virtual threads break ThreadLocal scoping
6. Service MUST call `TenantContext.getTenantId()` when creating entities — Hibernate filter does NOT auto-set `business_id` on save
