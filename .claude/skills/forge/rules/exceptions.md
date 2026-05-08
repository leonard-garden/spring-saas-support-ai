# Exception Rules & Catalog

Load khi: thêm exception mới, hoặc Phase 5 static check.

---

## Naming Convention

Pattern: `{Concept}{Problem}Exception`

| Concept | Problem | Result |
|---------|---------|--------|
| Token | Expired | `ExpiredTokenException` |
| Token | Invalid | `InvalidTokenException` |
| Tenant | NotFound | `TenantNotFoundException` |
| Member | NotFound | `MemberNotFoundException` |
| Quota | Exceeded | `QuotaExceededException` |
| Document | Processing | `DocumentProcessingException` |
| Invitation | Expired | `InvitationExpiredException` |
| Invitation | AlreadyAccepted | `InvitationAlreadyAcceptedException` |

---

## Structure bắt buộc

```java
public class {Name}Exception extends RuntimeException {
    public {Name}Exception(String message) {
        super(message);
    }
}
```

Không thêm fields, không thêm constructors trừ khi có lý do đặc biệt.
Exception nằm trong package của domain nó thuộc về.

---

## Catalog hiện có

| Exception | Package | HTTP Status |
|-----------|---------|-------------|
| `ExpiredTokenException` | `auth` | 401 |
| `InvalidTokenException` | `auth` | 401 |

---

## GlobalExceptionHandler — cách thêm handler mới

Khi thêm exception mới, phải thêm handler vào `GlobalExceptionHandler`:

```java
@ExceptionHandler({Name}Exception.class)
public ProblemDetail handle{Name}Exception({Name}Exception ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.{STATUS}, ex.getMessage());
    problem.setTitle("{Human Readable Title}");
    problem.setType(URI.create("https://problems.supportsaas.io/{slug}"));
    return problem;
}
```

URI slug: lowercase-with-dashes, ví dụ: `token-expired`, `tenant-not-found`.

---

## HTTP Status mapping

| Situation | Status |
|-----------|--------|
| Resource không tồn tại | 404 NOT_FOUND |
| Token hết hạn / invalid | 401 UNAUTHORIZED |
| Không có quyền | 403 FORBIDDEN |
| Quota vượt giới hạn | 429 TOO_MANY_REQUESTS |
| Lỗi business logic | 422 UNPROCESSABLE_ENTITY |
| Lỗi processing (document, email) | 500 INTERNAL_SERVER_ERROR |

---

## Static check

```bash
# Verify tất cả exceptions trong src/ đều extend RuntimeException
grep -rn "class.*Exception" src/main/java --include="*.java" \
  | grep -v "extends RuntimeException"
# Kết quả (trừ abstract classes) = FAIL
```
