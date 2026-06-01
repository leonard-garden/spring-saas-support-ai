---
name: db-schema
version: 1.0.0
description: |
  Design database schema and Flyway migration for a feature.
  Output: docs/features/{slug}/db-schema.md
  Triggers: "design table", "db schema", "migration for X", "ERD",
  "flyway migration", "database schema", "schema for X", "create table"
---

## Purpose

Design database tables, produce Flyway migration SQL, and draft JPA entity skeletons for a feature.

## Invoke

`/db-schema {feature-name}` — or auto-triggered.

## Process

1. **Get feature name** — from args or ask.
2. **Derive slug** — kebab-case.
3. **Read context:**
   - `CLAUDE.md`
   - `.claude/memory/architecture.md`
   - `.claude/memory/multi-tenancy.md`
   - `.claude/memory/tech-stack.md`
   - `.claude/skills/forge/references/flyway-guide.md`
   - `.claude/skills/forge/references/multi-tenancy-guide.md`
4. **Read SRS** — `docs/features/{slug}/SRS.md` for data requirements.
5. **Scan existing migrations** — list files in `src/main/resources/db/migration/`, find the highest V-number. New migration = that number + 1 (zero-padded to 3 digits, e.g., V005).
6. **Read existing entity classes** — check `src/main/java/com/leonardtrinh/supportsaas/` for entities related to this feature.
7. **Create directory** — `mkdir -p docs/features/{slug}`
8. **Generate schema design** — follow template below
9. **Save** — `docs/features/{slug}/db-schema.md`
10. **Commit:**
    ```bash
    git add docs/features/{slug}/db-schema.md
    git commit -m "docs(db): add schema design for {slug}"
    ```

## Output Template

```
# DB Schema — {Feature Name}

**Version:** 1.0
**Date:** {YYYY-MM-DD}
**Flyway migration:** V{NNN}__{feature_description}.sql

---

## ERD

​```mermaid
erDiagram
  TenantEntity {
    uuid business_id FK
  }
  {TableName} {
    uuid id PK
    uuid business_id FK
    varchar field_name
    timestamptz created_at
    timestamptz updated_at
  }
  businesses ||--o{ {TableName} : "owns"
​```

---

## Table Definitions

### {table_name}

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | UUID | NOT NULL | gen_random_uuid() | Primary key |
| business_id | UUID | NOT NULL | — | Tenant FK → businesses(id) |
| {field} | {type} | {nullable} | {default} | {description} |
| created_at | TIMESTAMPTZ | NOT NULL | now() | |
| updated_at | TIMESTAMPTZ | NOT NULL | now() | |

---

## Flyway Migration

**File:** `src/main/resources/db/migration/V{NNN}__{feature_description}.sql`

​```sql
CREATE TABLE {table_name} (
  id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id  UUID        NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  {column}     {TYPE}      {NOT NULL | NULL} {DEFAULT},
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Always index business_id for tenant filtering performance
CREATE INDEX idx_{table_name}_business_id ON {table_name}(business_id);
​```

---

## Entity Skeleton (Java)

​```java
@Entity
@Table(name = "{table_name}")
public class {ClassName} extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // {field}: {description}
    @Column(name = "{column}", nullable = {true|false})
    private {Type} {field};
}
​```

---

## Index Strategy

| Index | Columns | Reason |
|-------|---------|--------|
| idx_{table}_business_id | business_id | Tenant filter on every query |
| {idx name} | {columns} | {why} |

---

## Migration Notes

- Run `mvn flyway:info` before applying to verify version sequence
- Test migration on local docker-compose postgres before committing
- pgvector column syntax: `{column} VECTOR(1536)`
- Full-text search column: `{column}_tsv TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', {column})) STORED`
```

## Hard Constraints

- Every business table MUST have `business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE` — no exceptions except `businesses` table itself
- JPA entity MUST extend `TenantEntity` — this wires the Hibernate tenant filter
- PK: `UUID PRIMARY KEY DEFAULT gen_random_uuid()` — never `BIGSERIAL` or `SERIAL`
- Timestamps: `TIMESTAMPTZ` not `TIMESTAMP` — timezone-aware
- Pure PostgreSQL 16 syntax — no H2-compatible constructs
- Flyway file: `V{NNN}__{snake_case_description}.sql` — zero-pad version to 3 digits
- Always create index on `business_id` — the Hibernate filter runs this on every query
