-- Fix TIMESTAMP → TIMESTAMPTZ
ALTER TABLE subscriptions
    ALTER COLUMN trial_ends_at        TYPE TIMESTAMPTZ USING trial_ends_at AT TIME ZONE 'UTC',
    ALTER COLUMN current_period_start TYPE TIMESTAMPTZ USING current_period_start AT TIME ZONE 'UTC',
    ALTER COLUMN current_period_end   TYPE TIMESTAMPTZ USING current_period_end AT TIME ZONE 'UTC',
    ALTER COLUMN created_at           TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

-- Re-add businesses FK with ON DELETE CASCADE
ALTER TABLE subscriptions DROP CONSTRAINT subscriptions_business_id_fkey;
ALTER TABLE subscriptions
    ADD CONSTRAINT subscriptions_business_id_fkey
    FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE;

-- Add M4 columns
ALTER TABLE subscriptions
    ADD COLUMN stripe_customer_id   VARCHAR(255),
    ADD COLUMN pending_plan_id      UUID REFERENCES plans(id),
    ADD COLUMN cancel_at_period_end BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN updated_at           TIMESTAMPTZ NOT NULL DEFAULT now();

-- Partial indexes for M4 billing lookups
CREATE INDEX idx_subscriptions_pending_plan
    ON subscriptions (pending_plan_id)
    WHERE pending_plan_id IS NOT NULL;

CREATE INDEX idx_subscriptions_stripe_customer
    ON subscriptions (stripe_customer_id)
    WHERE stripe_customer_id IS NOT NULL;
