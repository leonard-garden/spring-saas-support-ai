# Coding Style Rules

Java 21 idioms for this project. Load when: executor needs context, or Step 5 static check.

> All files, comments, and documentation in this project must be written in **English**.

---

## Records cho DTOs và Value Objects

DTOs và value objects PHẢI là `record`. Không dùng class + Lombok.

✅ PASS:
```java
public record JwtClaims(UUID memberId, UUID tenantId, String role, String email, String jti) {}

public record CreateMemberRequest(
    @NotBlank String email,
    @NotBlank String password,
    @NotNull Role role
) {}
```

❌ FAIL:
```java
@Data
@Builder
public class CreateMemberRequest {
    private String email;
    // ...
}
```

```bash
# Detect Lombok usage
grep -rn "@Data\|@Builder\|@Getter\|@Setter\|@AllArgsConstructor\|@NoArgsConstructor" \
  src/main/java --include="*.java"
# Kết quả không rỗng = FAIL
```

---

## Interface + Impl pattern

Service phải có interface + implementation riêng.

```
JwtService          (interface — defines contract)
JwtServiceImpl      (implementation — @Service)
```

```bash
# Tìm @Service class không có interface pair
grep -rn "@Service" src/main/java --include="*.java" -l
# Với mỗi file: verify tồn tại interface cùng tên bỏ "Impl"
```

---

## Không return null từ Service

Service layer dùng `Optional<T>` hoặc throw typed exception. Không return `null`.

```bash
grep -rn "return null" src/main/java --include="*.java" \
  | grep -v "Test\|//\|test"
# Kết quả trong service files = FAIL
```

✅ PASS:
```java
public Optional<Member> findByEmail(String email) {
    return memberRepository.findByEmail(email);
}

public Member getByEmail(String email) {
    return memberRepository.findByEmail(email)
        .orElseThrow(() -> new MemberNotFoundException(email));
}
```

---

## @ConfigurationProperties cho config

Config values không được hardcode — dùng `@ConfigurationProperties`.

```java
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret,
    long accessTokenExpirationMs,
    long refreshTokenExpirationMs
) {}
```

---

## Entity: explicit getters/setters (no Lombok)

Entity dùng explicit getters/setters. Setters cho `businessId` phải override từ `TenantEntity`.

```java
@Override
public void setBusinessId(UUID businessId) {
    super.setBusinessId(businessId);
}
```

---

## Do not call Optional.get() directly

```bash
grep -rn "\.get()" src/main/java --include="*.java" \
  | grep "Optional\|optional"
# non-empty = FAIL — use orElseThrow() instead
```

---

## Prefer JPA repository over raw JdbcTemplate

Use `JpaRepository` (entity + repository) for all DB operations.
Only use `JdbcTemplate` when Hibernate cannot express the query (e.g. bulk native updates, DDL).

```bash
grep -rn "JdbcTemplate" src/main/java --include="*.java" | grep -v "Test\|Config"
# Review each: is there a JPA equivalent available?
```

❌ FAIL — raw JDBC when a JPA entity exists:
```java
jdbcTemplate.update("INSERT INTO audit_logs ...", UUID.randomUUID(), businessId, ...);
```

✅ PASS — JPA entity + repository:
```java
AuditLog entry = new AuditLog();
entry.setBusinessId(businessId);
entry.setAction("LOGIN");
auditLogRepository.save(entry);
```

---

## Async wrapper beans must have an interface

Any `@Service` bean that exists solely to add `@Async` behavior around another service MUST declare an interface.
Without an interface, callers cannot be unit-tested (Mockito can mock concrete classes but it is fragile),
and the implementation cannot be swapped or decorated.

```
AsyncEmailSender          (interface — defines contract)
AsyncEmailSenderImpl      (implementation — @Service, @Async methods)
```

Caller injects the interface, not the concrete class:

```java
// GOOD
private final AsyncEmailSender emailSender;

// BAD — concrete class leaks into caller
private final EmailSender emailSender;
```

Also: async wrapper beans belong in the package that owns the abstraction (`email/`), not in the package
of their caller (`auth/`). Placing them in the caller's package makes reuse by other features impossible.

```bash
# Detect @Service without interface pair
grep -rn "@Service" src/main/java --include="*.java" -l
# For each: verify an interface exists with the same name minus "Impl"
```

---

## Integration tests: use Apache HttpClient, not JDK HttpURLConnection

JDK `HttpURLConnection` (TestRestTemplate default) throws `HttpRetryException` when a POST request
returns 401, because it tries to retry in streaming mode. Switch to Apache HttpClient:

Add to `pom.xml` (test scope):
```xml
<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
    <scope>test</scope>
</dependency>
```

Configure in `@BeforeEach` of every `*IT` class that tests error responses on POST:
```java
@BeforeEach
void configureHttpClient() {
    restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
}
```
