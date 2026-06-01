# Exception Rules & Catalog

Load when: adding a new exception, or during Phase 5 static check.

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

## Required structure

```java
public class {Name}Exception extends RuntimeException {
    public {Name}Exception(String message) {
        super(message);
    }
}
```

Do not add fields or additional constructors unless there is a specific reason.
The exception must live in the package of the domain it belongs to.

---

## Current catalog

| Exception | Package | HTTP Status |
|-----------|---------|-------------|
| `ExpiredTokenException` | `auth` | 401 |
| `InvalidTokenException` | `auth` | 401 |

---

## GlobalExceptionHandler — how to add a new handler

When adding a new exception, a handler must be added to `GlobalExceptionHandler`:

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

URI slug: lowercase-with-dashes, e.g.: `token-expired`, `tenant-not-found`.

---

## HTTP Status mapping

| Situation | Status |
|-----------|--------|
| Resource does not exist | 404 NOT_FOUND |
| Token expired / invalid | 401 UNAUTHORIZED |
| Insufficient permissions | 403 FORBIDDEN |
| Quota exceeded | 429 TOO_MANY_REQUESTS |
| Business logic error | 422 UNPROCESSABLE_ENTITY |
| Processing error (document, email) | 500 INTERNAL_SERVER_ERROR |

---

## Static check

```bash
# Verify all exceptions in src/ extend RuntimeException
grep -rn "class.*Exception" src/main/java --include="*.java" \
  | grep -v "extends RuntimeException"
# Any result (excluding abstract classes) = FAIL
```
