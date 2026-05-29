---
name: test-plan
version: 1.0.0
description: |
  Write a comprehensive test plan for a feature: unit, integration, tenant isolation, e2e.
  Output: docs/features/{slug}/test-plan.md
  Triggers: "test plan", "test strategy", "test cases for X", "what to test",
  "write test plan", "test coverage for X", "how to test X"
---

## Purpose

Write a comprehensive test plan covering unit, integration, tenant isolation, and E2E scenarios.

## Invoke

`/test-plan {feature-name}` — or auto-triggered.

## Process

1. **Get feature name** — from args or ask.
2. **Derive slug** — kebab-case.
3. **Read context:**
   - `CLAUDE.md` — testing rules and coverage targets
   - `.claude/memory/constraints.md` — coverage minimums
   - `.claude/skills/forge/references/testing-patterns.md`
4. **Read SRS** — `docs/features/{slug}/SRS.md`. Map each FR (FR-001, FR-002...) to ≥1 test case.
5. **Read API spec** — `docs/features/{slug}/api-spec.md`. Map each endpoint to ≥1 integration test.
6. **Scan existing tests** — `src/test/java/com/leonardtrinh/supportsaas/` to understand patterns and find `TenantIsolationIT`.
7. **Identify new entities** — if feature adds entity extending `TenantEntity`, flag it for `TenantIsolationIT` extension.
8. **Create directory** — `mkdir -p docs/features/{slug}`
9. **Generate test plan** — follow template below
10. **Save** — `docs/features/{slug}/test-plan.md`
11. **Commit:**
    ```bash
    git add docs/features/{slug}/test-plan.md
    git commit -m "docs(test): add test plan for {slug}"
    ```

## Output Template

```
# Test Plan — {Feature Name}

**Version:** 1.0
**Date:** {YYYY-MM-DD}
**SRS reference:** [SRS.md](SRS.md)

---

## Scope

{What is being tested: which packages, which endpoints, which entities}

---

## Coverage Targets

| Layer | Target | Notes |
|-------|--------|-------|
| Overall line coverage | 80%+ | `mvn verify` jacoco report |
| Service layer | 100% | All service methods |
| Tenant isolation paths | 100% | TenantIsolationIT |

---

## Unit Tests

**Convention:** `{ClassName}Test.java` + `@ExtendWith(MockitoExtension.class)`
**Location:** `src/test/java/com/leonardtrinh/supportsaas/{feature-package}/`

| Test class | Method under test | Scenario | Input | Expected |
|-----------|------------------|----------|-------|---------|
| {ServiceImpl}Test | {methodName} | Happy path — {FR-001} | {input} | {output} |
| {ServiceImpl}Test | {methodName} | Error case — {condition} | {input} | throws {ExceptionType} |

**Example structure:**
​```java
@ExtendWith(MockitoExtension.class)
class {ServiceImpl}Test {

    @Mock private {Repository} repository;
    @InjectMocks private {ServiceImpl} service;

    @Test
    void {methodName}_shouldReturn{X}_when{Condition}() {
        // arrange
        when(repository.findById(any())).thenReturn(Optional.of(new {Entity}()));
        // act
        var result = service.{method}({input});
        // assert
        assertThat(result).isNotNull();
    }
}
​```

---

## Integration Tests

**Convention:** `{ClassName}IT.java` + `@SpringBootTest` + `@Testcontainers`
**DB image:** `pgvector/pgvector:pg16`

| Test class | Endpoint | Method | Scenario | Expected HTTP |
|-----------|----------|--------|----------|--------------|
| {Controller}IT | /api/v1/{resource} | POST | Happy path | 201 + ApiResponse.success=true |
| {Controller}IT | /api/v1/{resource} | POST | Missing field | 400 + ProblemDetail |
| {Controller}IT | /api/v1/{resource}/{id} | GET | Not found | 404 + ProblemDetail |
| {Controller}IT | /api/v1/{resource} | GET | No JWT | 401 |

---

## Tenant Isolation Tests

**Extend:** `TenantIsolationIT.java`

| Scenario | Tenant A action | Tenant B assertion |
|----------|----------------|-------------------|
| Read isolation | Create {entity} as Tenant A | GET /api/v1/{resource} as Tenant B → empty list |
| Write isolation | POST /api/v1/{resource} as Tenant B | Cannot see or modify Tenant A's {entity} |

---

## E2E Scenarios

| Flow | Actor | Steps | Expected outcome |
|------|-------|-------|-----------------|
| {Happy path flow} | {User class} | 1. {step} 2. {step} | {outcome} |
| {Error path flow} | {User class} | 1. {step} | {error outcome} |

---

## Test Data Setup

{Describe any fixtures, seed data, or TestEntityManager setup needed}

---

## Out of Scope

- {What is NOT being tested and why}
```

## Hard Constraints

- No H2 — always `pgvector/pgvector:pg16` in `@Container` for integration tests
- Every FR in SRS must appear in at least one test case row
- `TenantIsolationIT` MUST be updated if feature adds any entity extending `TenantEntity`
- Async pipeline tests: use `CompletableFuture.get(5, TimeUnit.SECONDS)` not `Thread.sleep()`
- Tenant isolation must cover both read (GET) AND write (POST/PUT/DELETE) paths
- Unit test class naming: `{ClassName}Test`, integration test: `{ClassName}IT`
