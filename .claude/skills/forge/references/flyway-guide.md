# Flyway Migration Guide

Load when the task requires adding tables, columns, or new seed data.

---

## Naming Convention

```
V{N}__{description_with_underscores}.sql
```

Next version: check `src/main/resources/db/migration/` → take the highest number + 1.

| Current | Next migration |
|---------|----------------|
| V8__seed_plans.sql | V9__... |

---

## Migration must be created BEFORE writing the Entity

Mandatory order:
1. Create migration SQL
2. Write Java Entity
3. Verify with `FlywayMigrationIT`

---

## Template — Business Data Table (with tenant isolation)

```sql
-- V9__create_knowledge_bases.sql
CREATE TABLE knowledge_bases (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Mandatory index on business_id (query performance + tenant filter)
CREATE INDEX idx_knowledge_bases_business_id ON knowledge_bases(business_id);
```

**Rules:**
- `business_id UUID NOT NULL REFERENCES businesses(id)` — required for every business table
- `id UUID PRIMARY KEY DEFAULT gen_random_uuid()` — always use UUID
- Timestamps: `TIMESTAMPTZ` not `TIMESTAMP`
- `ON DELETE CASCADE` for FK to `businesses`

---

## Template — Join / Reference Table (no tenant)

```sql
-- V10__create_document_chunks.sql
CREATE TABLE document_chunks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    content     TEXT NOT NULL,
    chunk_index INT  NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_document_chunks_document_id ON document_chunks(document_id);
```

---

## Template — Adding a column to an existing table

```sql
-- V11__add_avatar_url_to_members.sql
ALTER TABLE members
    ADD COLUMN avatar_url VARCHAR(500);

-- Do not use NOT NULL when adding to a table that already has data
-- Unless a DEFAULT value is provided
ALTER TABLE members
    ADD COLUMN display_name VARCHAR(100) NOT NULL DEFAULT '';
```

---

## Template — Seed Data

```sql
-- V12__seed_default_chatbot_config.sql
INSERT INTO chatbot_configs (id, name, welcome_message)
VALUES
    (gen_random_uuid(), 'Default', 'Hi! How can I help you?');
```

---

## Indexes — When to create

| Pattern | Index |
|---------|-------|
| FK column used in WHERE | Always create |
| Column used frequently in ORDER BY | Create |
| Low-cardinality column (boolean, status enum) | Partial index |
| Unique constraint | `CREATE UNIQUE INDEX` or `UNIQUE` constraint |

---

## Verify migration

```bash
mvn flyway:info      # view migration status
mvn verify           # run FlywayMigrationIT
```

`FlywayMigrationIT` verifies:
- All migrations applied successfully
- Required columns exist
- Required indexes exist

When adding a new table → add `assertColumnsExist` to `FlywayMigrationIT`.

---

## Do NOT

- ❌ Edit a migration that has already been applied (create a new migration instead)
- ❌ Delete migration files
- ❌ Add a NOT NULL column to a table with existing data without a DEFAULT
- ❌ Use `SERIAL` / `BIGSERIAL` — use `UUID DEFAULT gen_random_uuid()`
