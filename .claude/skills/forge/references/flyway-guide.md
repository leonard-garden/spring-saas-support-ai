# Flyway Migration Guide

Load khi task yêu cầu thêm bảng, cột, hoặc seed data mới.

---

## Naming Convention

```
V{N}__{description_with_underscores}.sql
```

Next version: check `src/main/resources/db/migration/` → lấy số lớn nhất + 1.

| Hiện tại | Migration tiếp theo |
|----------|---------------------|
| V8__seed_plans.sql | V9__... |

---

## Migration phải tạo TRƯỚC khi viết Entity

Thứ tự bắt buộc:
1. Tạo migration SQL
2. Viết Entity Java
3. Verify với `FlywayMigrationIT`

---

## Template — Bảng Business Data (có tenant isolation)

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

-- Index bắt buộc cho business_id (query performance + tenant filter)
CREATE INDEX idx_knowledge_bases_business_id ON knowledge_bases(business_id);
```

**Rules:**
- `business_id UUID NOT NULL REFERENCES businesses(id)` — bắt buộc cho mọi business table
- `id UUID PRIMARY KEY DEFAULT gen_random_uuid()` — luôn dùng UUID
- Timestamps: `TIMESTAMPTZ` không phải `TIMESTAMP`
- `ON DELETE CASCADE` cho FK đến `businesses`

---

## Template — Join / Reference Table (không có tenant)

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

## Template — Thêm cột vào bảng có sẵn

```sql
-- V11__add_avatar_url_to_members.sql
ALTER TABLE members
    ADD COLUMN avatar_url VARCHAR(500);

-- Không dùng NOT NULL nếu thêm vào bảng đã có dữ liệu
-- Trừ khi có DEFAULT value
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

## Indexes — Khi nào cần

| Pattern | Index |
|---------|-------|
| FK column dùng trong WHERE | Luôn tạo |
| Column dùng trong ORDER BY thường xuyên | Tạo |
| Column có cardinality thấp (boolean, status enum) | Partial index |
| Unique constraint | `CREATE UNIQUE INDEX` hoặc `UNIQUE` constraint |

---

## Verify migration

```bash
mvn flyway:info      # xem migration status
mvn verify           # chạy FlywayMigrationIT
```

`FlywayMigrationIT` verify:
- Tất cả migrations applied successfully
- Required columns tồn tại
- Required indexes tồn tại

Khi thêm bảng mới → thêm `assertColumnsExist` vào `FlywayMigrationIT`.

---

## Không được làm

- ❌ Sửa migration đã apply (tạo migration mới thay thế)
- ❌ Xóa migration file
- ❌ Thêm NOT NULL column vào bảng có data mà không có DEFAULT
- ❌ Dùng `SERIAL` / `BIGSERIAL` — dùng `UUID DEFAULT gen_random_uuid()`
