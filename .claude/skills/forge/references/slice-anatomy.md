# Slice Anatomy

Cấu trúc một package slice hoàn chỉnh. Load khi tạo package mới.

---

## Directory Layout

```
{domain}/
  {Domain}Controller.java       # REST layer
  {Domain}Service.java          # Interface
  {Domain}ServiceImpl.java      # Implementation (@Service)
  {Domain}Repository.java       # Spring Data interface
  {Domain}.java                 # Entity (extends TenantEntity nếu business data)
  {Domain}Request.java          # record — inbound DTO
  {Domain}Response.java         # record — outbound DTO
  {Domain}NotFoundException.java  # Typed exception (nếu cần)
  package-info.java             # Package declaration
```

---

## Entity

```java
@Entity
@Table(name = "{domains}")
public class KnowledgeBase extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // explicit getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }

    // explicit setters
    public void setName(String name) { this.name = name; }

    @Override
    public void setBusinessId(UUID businessId) {
        super.setBusinessId(businessId);
    }
}
```

---

## Repository

```java
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, UUID> {
    List<KnowledgeBase> findAll();  // filter auto-applied by TenantFilterAspect
    Optional<KnowledgeBase> findByIdAndDeletedAtIsNull(UUID id);
}
```

---

## Service Interface

```java
public interface KnowledgeBaseService {
    List<KnowledgeBaseResponse> listAll();
    KnowledgeBaseResponse getById(UUID id);
    KnowledgeBaseResponse create(CreateKnowledgeBaseRequest request);
    KnowledgeBaseResponse update(UUID id, UpdateKnowledgeBaseRequest request);
    void delete(UUID id);
}
```

---

## Service Implementation

```java
@Service
@Transactional(readOnly = true)
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseRepository repository;

    public KnowledgeBaseServiceImpl(KnowledgeBaseRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<KnowledgeBaseResponse> listAll() {
        return repository.findAll().stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public KnowledgeBaseResponse create(CreateKnowledgeBaseRequest request) {
        UUID tenantId = TenantContext.getTenantId();  // required for writes
        KnowledgeBase kb = new KnowledgeBase();
        kb.setBusinessId(tenantId);
        kb.setName(request.name());
        return toResponse(repository.save(kb));
    }

    private KnowledgeBaseResponse toResponse(KnowledgeBase kb) {
        return new KnowledgeBaseResponse(kb.getId(), kb.getName(), kb.getCreatedAt());
    }
}
```

Key points:
- Class-level `@Transactional(readOnly = true)`, override với `@Transactional` cho writes
- Constructor injection (không @Autowired)
- `TenantContext.getTenantId()` PHẢI được gọi khi tạo entity mới

---

## DTOs (records)

```java
public record CreateKnowledgeBaseRequest(
    @NotBlank @Size(max = 100) String name
) {}

public record KnowledgeBaseResponse(
    UUID id,
    String name,
    Instant createdAt
) {}
```

---

## Controller

```java
@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;

    public KnowledgeBaseController(KnowledgeBaseService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseResponse>> list() {
        return ApiResponse.ok(service.listAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KnowledgeBaseResponse> create(
            @Valid @RequestBody CreateKnowledgeBaseRequest request) {
        return ApiResponse.ok(service.create(request));
    }
}
```
