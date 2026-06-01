CREATE TABLE chatbots (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id     UUID        NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    kb_id           UUID        NOT NULL REFERENCES knowledge_bases(id) ON DELETE RESTRICT,
    name            VARCHAR(100) NOT NULL,
    welcome_message VARCHAR(500) NOT NULL,
    primary_color   VARCHAR(7)  NOT NULL DEFAULT '#3B82F6',
    is_active       BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chatbots_business_id ON chatbots(business_id);
CREATE INDEX idx_chatbots_kb_id ON chatbots(kb_id);
