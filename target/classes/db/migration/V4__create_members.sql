CREATE TABLE members (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id    UUID NOT NULL REFERENCES businesses(id),
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    role           VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    email_verified BOOLEAN NOT NULL DEFAULT false,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_business_email UNIQUE (business_id, email)
);

CREATE INDEX idx_members_business_id ON members(business_id);
