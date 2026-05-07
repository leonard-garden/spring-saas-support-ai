CREATE TABLE businesses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    stripe_customer_id VARCHAR(255),
    suspended_at TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
