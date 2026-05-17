CREATE TABLE invitations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    email       VARCHAR(255) NOT NULL,
    role        VARCHAR(20) NOT NULL,
    token       VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP
);

CREATE UNIQUE INDEX uq_pending_invitation ON invitations(business_id, email)
    WHERE accepted_at IS NULL;
