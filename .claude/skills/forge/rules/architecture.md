# Architecture Rules

Checklist for Step 5 Static Verification. Run each rule with Grep/AST/LSP.
FAIL on any rule → fix before committing.

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
Without an explicit `@Transactional`, JPA uses auto-commit (implicit, no rollback on failure).

```bash
grep -rn "@Async" src/main/java --include="*.java" | grep -v "taskExecutor"
# non-empty = FAIL (bare @Async)
```

Every `@Async` method that does a DB write MUST declare:

```java
@Async("taskExecutor")
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void someAsyncWriteMethod(...) { ... }
```

`REQUIRES_NEW` makes intent explicit: always own transaction, independent of caller.

---

## Rule 9 — @Async methods must live in a separate @Service bean

Spring `@Async` works through the AOP proxy. Calling an `@Async` method from within the **same class** bypasses the proxy — the method runs synchronously on the caller's thread.

```bash
# Manually verify: every @Async method must be in a class that is
# injected as a dependency by the caller, not called via this.*
grep -rn "@Async" src/main/java --include="*.java" -l
# For each file: confirm the caller injects this bean — it does NOT call self
```

❌ FAIL — self-invocation, proxy bypassed:
```java
@Service
public class AuthServiceImpl {
    @Async("taskExecutor")
    public void logAsync() { ... }

    public void login() {
        logAsync(); // runs synchronously — @Async is a no-op
    }
}
```

✅ PASS — separate bean:
```java
@Service
public class AuditLogger {
    @Async("taskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAsync() { ... }
}

@Service
public class AuthServiceImpl {
    private final AuditLogger auditLogger; // injected
    public void login() {
        auditLogger.logAsync(); // goes through proxy — truly async
    }
}
```

---

## Rule 10 — Never throw EntityNotFoundException or raw RuntimeException from service layer

All errors thrown from service layer must extend `AppException` to get proper `ProblemDetail` mapping.
`EntityNotFoundException` (JPA) leaks ORM semantics into the HTTP response.

```bash
grep -rn "throw new EntityNotFoundException\|throw new RuntimeException" \
  src/main/java --include="*.java" | grep -v "Test"
# non-empty = FAIL
```

❌ FAIL:
```java
.orElseThrow(() -> new EntityNotFoundException("Plan not found"))
```

✅ PASS:
```java
.orElseThrow(() -> new PlanMisconfiguredException("free"))
```

---

## Rule 11 — SecurityConfig must declare a custom AuthenticationEntryPoint when using JWT

Spring Security's default `AuthenticationEntryPoint` sends `WWW-Authenticate: Basic realm=...` on 401.
This causes JDK `HttpURLConnection` (TestRestTemplate default) to retry POST requests in streaming mode,
throwing `HttpRetryException: cannot retry due to server authentication, in streaming mode`.

Any `SecurityConfig` wiring a JWT filter MUST include:

```java
.exceptionHandling(ex -> ex.authenticationEntryPoint(
    (request, response, e) -> {
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\",\"detail\":\"Authentication required\"}");
    }
))
