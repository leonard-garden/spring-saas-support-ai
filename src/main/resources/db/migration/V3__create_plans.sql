CREATE TABLE plans (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                     VARCHAR(100) NOT NULL,
    slug                     VARCHAR(50)  NOT NULL UNIQUE,
    price_usd_monthly        DECIMAL(10,2) NOT NULL DEFAULT 0,
    stripe_price_id          VARCHAR(255),
    max_knowledge_bases      INTEGER NOT NULL DEFAULT 1,
    max_documents_per_kb     INTEGER NOT NULL DEFAULT 5,
    max_messages_per_month   INTEGER NOT NULL DEFAULT 100,
    max_members              INTEGER NOT NULL DEFAULT 1,
    features                 TEXT[]  NOT NULL DEFAULT '{}',
    is_active                BOOLEAN NOT NULL DEFAULT true
);

ALTER TABLE businesses ADD COLUMN plan_id UUID REFERENCES plans(id);
