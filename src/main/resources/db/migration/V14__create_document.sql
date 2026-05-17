CREATE TABLE documents (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id      UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    knowledge_base_id UUID NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    filename         TEXT NOT NULL,
    content_type     TEXT NOT NULL,
    size_bytes       BIGINT NOT NULL,
    minio_key        TEXT NOT NULL UNIQUE,
    status           TEXT NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING','PROCESSING','READY','FAILED')),
    chunk_count      INT NOT NULL DEFAULT 0,
    error_message    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_business_id ON documents(business_id);
CREATE INDEX idx_documents_knowledge_base_id ON documents(knowledge_base_id);
CREATE INDEX idx_documents_status ON documents(status);
