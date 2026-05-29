CREATE TABLE conversations (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID        NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    chatbot_id  UUID        NOT NULL REFERENCES chatbots(id) ON DELETE CASCADE,
    session_id  VARCHAR(100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_business_id ON conversations(business_id);
CREATE INDEX idx_conversations_chatbot_id ON conversations(chatbot_id);
-- Widget needs to look up existing conversation by session_id quickly
CREATE INDEX idx_conversations_session_id ON conversations(session_id) WHERE session_id IS NOT NULL;
