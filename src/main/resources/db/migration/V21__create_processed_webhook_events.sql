CREATE TABLE processed_webhook_events (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    stripe_event_id VARCHAR(255)    NOT NULL UNIQUE,
    event_type      VARCHAR(100)    NOT NULL,
    processed_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_processed_webhook_events_stripe_event_id
    ON processed_webhook_events (stripe_event_id);
