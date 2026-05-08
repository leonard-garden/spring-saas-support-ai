CREATE TABLE subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id     UUID NOT NULL REFERENCES businesses(id),
    plan_id         UUID NOT NULL REFERENCES plans(id),
    status          VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'TRIALING', 'PAST_DUE', 'CANCELED')),
    trial_ends_at   TIMESTAMP,
    current_period_start TIMESTAMP,
    current_period_end   TIMESTAMP,
    stripe_subscription_id VARCHAR(255),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_subscriptions_business ON subscriptions(business_id);
