-- Remove duplicates, keep latest per business before adding unique constraint
DELETE FROM chatbots
WHERE id NOT IN (
    SELECT DISTINCT ON (business_id) id
    FROM chatbots
    ORDER BY business_id, created_at DESC
);

ALTER TABLE chatbots
    ADD CONSTRAINT uq_chatbots_business UNIQUE (business_id);

CREATE TABLE chatbot_knowledge_bases (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    chatbot_id  UUID        NOT NULL REFERENCES chatbots(id) ON DELETE CASCADE,
    kb_id       UUID        NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    business_id UUID        NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    UNIQUE (chatbot_id, kb_id)
);

CREATE INDEX idx_chatbot_kbs_chatbot_id ON chatbot_knowledge_bases(chatbot_id);
CREATE INDEX idx_chatbot_kbs_business_id ON chatbot_knowledge_bases(business_id);
