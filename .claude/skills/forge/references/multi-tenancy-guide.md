# Multi-Tenancy Guide

Patterns for tenant-aware code. Load when the task involves entities, async, or cross-tenant operations.

---

## Request Flow

```
HTTP Request
  └─ JwtAuthFilter
       ├─ validate JWT
       ├─ TenantContext.setTenantId(claims.tenantId())   ← SET
       └─ filterChain.doFilter(...)
            └─ Controller → Service → Repository
                 └─ TenantFilterAspect (AOP)
                      └─ session.enableFilter("tenantFilter", tenantId)
                           └─ SQL: WHERE business_id = 'uuid'
  └─ JwtAuthFilter finally
       └─ TenantContext.clear()                          ← ALWAYS CLEAR
```

---

## Reads — tenantId not needed

Hibernate filter is applied automatically. Service does not need to call TenantContext for queries:

```java
// CORRECT — filter auto-applied
public List<Member> listMembers() {
    return memberRepository.findAll();
}
```

---

## Writes — MUST set businessId

```java
// CORRECT — must fetch tenantId before save
public KnowledgeBase create(CreateKnowledgeBaseRequest req) {
    UUID tenantId = TenantContext.getTenantId();  // required
    KnowledgeBase kb = new KnowledgeBase();
    kb.setBusinessId(tenantId);                   // must set before save
    kb.setName(req.name());
    return repository.save(kb);
}
```

---

## Async — TenantContextCopyingDecorator

`@Async` runs on a different thread — ThreadLocal is empty by default.

```java
// CORRECT — use named executor
@Async("taskExecutor")   // NOT bare @Async
public CompletableFuture<Void> processDocumentAsync(UUID docId) {
    // TenantContextCopyingDecorator has already copied tenantId to this thread
    Document doc = documentRepository.findById(docId)...;
    // ...
}
```

The `taskExecutor` bean in `AsyncConfig` is wrapped with `TenantContextCopyingDecorator`.

---

## Admin bypass — explicit disableFilter

Only permitted in `Admin*` classes:

```java
@Repository
public class AdminMemberRepository {
    @PersistenceContext EntityManager em;

    public List<Member> findAllAcrossTenants() {
        Session session = em.unwrap(Session.class);
        session.disableFilter("tenantFilter");  // explicit, intentional
        return em.createQuery("FROM Member", Member.class).getResultList();
    }
}
```

---

## Entity hierarchy

```
TenantEntity (@MappedSuperclass)
  ├─ business_id UUID (non-null, non-updatable)
  ├─ @FilterDef + @Filter
  └─ subclasses: Member, Invitation, KnowledgeBase, Document, Chatbot, ...

Business — NOT a TenantEntity (it IS the tenant)
```

---

## Common mistakes

| Mistake | Fix |
|---------|-----|
| `new Entity()` without setting `businessId` | Call `TenantContext.getTenantId()` before save |
| Bare `@Async` | `@Async("taskExecutor")` |
| `TenantContext.setTenantId()` without `finally` | Wrap in try/finally |
| Cross-tenant query in non-Admin repo | Only Admin* repos may call disableFilter |
