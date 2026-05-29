# Testing Patterns

Conventions for unit tests and integration tests. Load when writing tests.

---

## Naming Convention

| Type | Suffix | When |
|------|--------|------|
| Unit test | `{ClassName}Test` | Test single class, mock dependencies |
| Integration test | `{ClassName}IT` | Test with real DB via Testcontainers |

---

## Unit Test — Structure

```java
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {

    @Mock
    private KnowledgeBaseRepository repository;

    @Mock
    private TenantContext tenantContext;  // if needed

    private KnowledgeBaseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeBaseServiceImpl(repository);
    }

    @Test
    @DisplayName("create → saves entity with correct businessId and name")
    void create_savesEntityWithCorrectFields() {
        UUID tenantId = UUID.randomUUID();
        // setup TenantContext mock if needed

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateKnowledgeBaseRequest("My KB");
        var result = service.create(request);

        ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("My KB");
        assertThat(captor.getValue().getBusinessId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("getById → throws KnowledgeBaseNotFoundException when not found")
    void getById_notFound_throwsException() {
        when(repository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(UUID.randomUUID()))
            .isInstanceOf(KnowledgeBaseNotFoundException.class);
    }
}
```

**Rules:**
- `@DisplayName` on every test — describe behavior, not implementation
- AssertJ (`assertThat`, `assertThatThrownBy`) — do not use JUnit `assertEquals`
- Mock only direct dependencies — do not mock transitive dependencies
- Do not use `@SpringBootTest` for unit tests — too heavy

---

## Integration Test — Structure

```java
@SpringBootTest
@ActiveProfiles("test")
class KnowledgeBaseServiceIT {

    @Autowired
    private KnowledgeBaseService service;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        // Set tenant context for each test
        TenantContext.setTenantId(TENANT_A_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        // Clean up test data if needed
    }

    @Test
    @DisplayName("create → persists to DB with correct tenant isolation")
    void create_persistsWithTenantIsolation() {
        var request = new CreateKnowledgeBaseRequest("My KB");
        var response = service.create(request);

        assertThat(response.id()).isNotNull();

        // Verify DB directly
        var row = jdbc.queryForMap(
            "SELECT * FROM knowledge_bases WHERE id = ?", response.id());
        assertThat(row.get("business_id")).isEqualTo(TENANT_A_ID);
    }
}
```

**Rules:**
- `@SpringBootTest` + `@ActiveProfiles("test")` — uses Testcontainers (configured in application-test.yml)
- Do not use H2 — **no exceptions**
- `TenantContext.setTenantId()` in `@BeforeEach`, clear in `@AfterEach`
- Verify data directly via `JdbcTemplate` when persistence must be confirmed

---

## Tenant Isolation Test Pattern

For every new entity, cross-tenant isolation must be tested:

```java
@Test
@DisplayName("listAll → returns only entities belonging to current tenant")
void listAll_returnOnlyCurrentTenantEntities() {
    // Create for tenant A
    TenantContext.setTenantId(TENANT_A_ID);
    service.create(new CreateKnowledgeBaseRequest("KB for A"));

    // Create for tenant B
    TenantContext.setTenantId(TENANT_B_ID);
    service.create(new CreateKnowledgeBaseRequest("KB for B"));

    // Query as tenant A — must NOT see tenant B's data
    TenantContext.setTenantId(TENANT_A_ID);
    var results = service.listAll();

    assertThat(results).hasSize(1);
    assertThat(results.get(0).name()).isEqualTo("KB for A");
}
```

---

## Test Data Helpers

```java
// Static constants for test tenants
static final UUID TENANT_A_ID = UUID.fromString("11111111-0000-0000-0000-000000000000");
static final UUID TENANT_B_ID = UUID.fromString("22222222-0000-0000-0000-000000000000");

// Member builder for tests
static Member testMember(UUID tenantId, Role role) {
    Member m = new Member();
    m.setId(UUID.randomUUID());
    m.setBusinessId(tenantId);
    m.setEmail(UUID.randomUUID() + "@test.com");
    m.setPasswordHash("hashed");
    m.setRole(role);
    return m;
}
```

---

## Coverage Requirements

| Layer | Minimum |
|-------|---------|
| Service (unit) | 60% |
| Tenant isolation paths | 100% |
| New entity operations | Create + Read + Isolation test |
