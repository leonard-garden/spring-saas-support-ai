# Architecture Rules

Checklist cho Phase 5 Static Verification. Chạy từng rule bằng Grep/AST/LSP.
FAIL bất kỳ rule nào → fix trước khi commit.

---

## Rule 1 — Entity phải extend TenantEntity

Mọi business entity (không phải Business/Tenant itself) phải extend `TenantEntity`.

```bash
# Tìm @Entity không extend TenantEntity
grep -rn "@Entity" src/main/java --include="*.java" -l
# Với mỗi file, verify: "extends TenantEntity"
```

❌ FAIL:
```java
@Entity
public class KnowledgeBase { ... }
```

✅ PASS:
```java
@Entity
public class KnowledgeBase extends TenantEntity { ... }
```

Exception: `Business` entity — it IS the tenant, không extend.

---

## Rule 2 — @Async phải chỉ định executor

Bare `@Async` dùng default Spring executor — không có `TenantContextCopyingDecorator`.
Tenant context sẽ bị null trên async thread.

```bash
grep -rn "@Async" src/main/java --include="*.java" | grep -v '"taskExecutor"'
# Kết quả không rỗng = FAIL
```

❌ FAIL: `@Async`
✅ PASS: `@Async("taskExecutor")`

---

## Rule 3 — disableFilter chỉ trong Admin* class

```bash
grep -rn "disableFilter" src/main/java --include="*.java"
# Mỗi kết quả: verify class name starts with "Admin"
```

❌ FAIL: `disableFilter` trong `MemberRepository`
✅ PASS: `disableFilter` trong `AdminMemberRepository`

---

## Rule 4 — Service không gọi EntityManager trực tiếp

Service layer phải dùng Repository. Chỉ `Admin*Repository` được dùng `EntityManager`.

```bash
grep -rn "EntityManager" src/main/java --include="*.java" \
  | grep -v "Repository\|Config\|Test"
# Kết quả không rỗng = FAIL
```

---

## Rule 5 — TenantContext.clear() trong finally

```bash
# Tìm TenantContext.setTenantId không có finally pair
grep -rn "TenantContext.setTenantId" src/main/java --include="*.java" -l
# Với mỗi file: verify có "finally" block chứa TenantContext.clear()
```

Pattern bắt buộc:
```java
try {
    TenantContext.setTenantId(tenantId);
    // ...
} finally {
    TenantContext.clear(); // NEVER skip
}
```

---

## Rule 6 — Virtual threads disabled

`spring.threads.virtual.enabled=false` — ThreadLocal-based TenantContext không tương thích với virtual threads.

```bash
grep -rn "virtual.enabled" src/main/resources --include="*.yml" --include="*.properties"
# Phải là false hoặc absent (default false)
```

---

## Rule 7 — Controller không import Repository trực tiếp

```bash
grep -rn "import.*Repository" src/main/java --include="*.java" \
  | grep "Controller"
# Kết quả không rỗng = FAIL
```

Controller chỉ được import Service interface.

---

## Rule 8 — @Async methods that write to DB must use @Transactional(REQUIRES_NEW)

`@Async` runs on a separate thread — no transaction from the caller propagates.
Without an explicit `@Transactional`, JdbcTemplate/JPA uses auto-commit (implicit, no rollback on failure).

```bash
# Find @Async methods missing @Transactional
grep -B2 "@Async" src/main/java/**/*.java | grep -v "@Transactional"
```

Every `@Async` method that calls `jdbcTemplate.update`, `repository.save`, or any DB write MUST declare:

```java
@Async("taskExecutor")
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void someAsyncWriteMethod(...) { ... }
```

`REQUIRES_NEW` is required (not `REQUIRED`) to make intent explicit: always own transaction, independent of caller.
