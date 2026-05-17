# Multi-Tenancy Guide

Patterns cho tenant-aware code. Load khi task liên quan đến entity, async, hoặc cross-tenant ops.

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

## Reads — không cần tenantId

Hibernate filter tự áp dụng. Service không cần gọi TenantContext cho queries:

```java
// CORRECT — filter auto-applied
public List<Member> listMembers() {
    return memberRepository.findAll();
}
```

---

## Writes — PHẢI set businessId

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

`@Async` runs on different thread — ThreadLocal empty by default.

```java
// CORRECT — use named executor
@Async("taskExecutor")   // NOT bare @Async
public CompletableFuture<Void> processDocumentAsync(UUID docId) {
    // TenantContextCopyingDecorator đã copy tenantId sang thread này
    Document doc = documentRepository.findById(docId)...;
    // ...
}
```

`taskExecutor` bean trong `AsyncConfig` đã được wrap với `TenantContextCopyingDecorator`.

---

## Admin bypass — explicit disableFilter

Chỉ dùng trong `Admin*` classes:

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
| `new Entity()` mà không set `businessId` | Gọi `TenantContext.getTenantId()` trước khi save |
| Bare `@Async` | `@Async("taskExecutor")` |
| `TenantContext.setTenantId()` không có `finally` | Wrap trong try/finally |
| Cross-tenant query trong non-Admin repo | Chỉ Admin* repos được disableFilter |
