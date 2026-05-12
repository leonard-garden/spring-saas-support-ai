CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id     UUID NOT NULL REFERENCES businesses(id),
    member_id       UUID REFERENCES members(id),
    impersonator_id UUID,
    action          VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(100),
    resource_id     UUID,
    metadata        JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_business_created ON audit_logs(business_id, created_at DESC);
