# API Patterns

Response envelope, error format, validation. Load when writing a controller or exception handler.

---

## ApiResponse<T> — Success Responses

```java
// Definition (common/ApiResponse.java)
public record ApiResponse<T>(boolean success, T data, String error) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }
    public static <T> ApiResponse<T> fail(String error) {
        return new ApiResponse<>(false, null, error);
    }
}
```

**Usage in controller:**
```java
// Single object
return ApiResponse.ok(service.getById(id));

// List
return ApiResponse.ok(service.listAll());

// Created (201)
@ResponseStatus(HttpStatus.CREATED)
return ApiResponse.ok(service.create(request));

// No content (204) — do not use ApiResponse
@ResponseStatus(HttpStatus.NO_CONTENT)
public void delete(@PathVariable UUID id) { ... }
```

---

## ProblemDetail — Error Responses (RFC 7807)

Exceptions are handled by `GlobalExceptionHandler` → returns `ProblemDetail`.
Controllers do NOT return errors directly — throw an exception and let the handler map it.

```java
// In GlobalExceptionHandler
@ExceptionHandler(KnowledgeBaseNotFoundException.class)
public ProblemDetail handleKnowledgeBaseNotFoundException(KnowledgeBaseNotFoundException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problem.setTitle("Knowledge Base Not Found");
    problem.setType(URI.create("https://problems.supportsaas.io/knowledge-base-not-found"));
    return problem;
}
```

Error response shape:
```json
{
  "type": "https://problems.supportsaas.io/knowledge-base-not-found",
  "title": "Knowledge Base Not Found",
  "status": 404,
  "detail": "Knowledge base abc-123 not found"
}
```

---

## Validation

Request DTOs use Bean Validation annotations:
```java
public record CreateKnowledgeBaseRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull UUID ownerId,
    @Email String contactEmail
) {}
```

Controller uses `@Valid`:
```java
public ApiResponse<KnowledgeBaseResponse> create(
    @Valid @RequestBody CreateKnowledgeBaseRequest request) { ... }
```

Validation errors are auto-handled by `GlobalExceptionHandler.handleValidationException` → 400 Bad Request.

---

## Pagination (when needed)

```java
public record PageResponse<T>(
    List<T> items,
    long total,
    int page,
    int limit
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getTotalElements(),
            page.getNumber(),
            page.getSize()
        );
    }
}

// Controller
return ApiResponse.ok(PageResponse.of(service.listPaged(pageable)));
```
