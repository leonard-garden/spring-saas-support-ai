# Coding Style Rules

Java 21 idioms của project này. Load khi: executor cần context, hoặc Phase 5 static check.

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

## Không dùng Optional.get() trực tiếp

```bash
grep -rn "\.get()" src/main/java --include="*.java" \
  | grep "Optional\|optional"
# Kết quả = FAIL — dùng orElseThrow() thay thế
```
