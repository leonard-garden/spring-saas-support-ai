ALTER TABLE invitations
    RENAME COLUMN token TO token_hash;

ALTER TABLE invitations
    ADD COLUMN IF NOT EXISTS invited_by UUID REFERENCES members(id),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT now();

ALTER TABLE invitations
    ADD CONSTRAINT chk_invitation_role CHECK (role IN ('ADMIN', 'MEMBER'));
