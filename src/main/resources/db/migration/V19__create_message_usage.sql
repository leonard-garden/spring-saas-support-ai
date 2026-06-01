CREATE TABLE message_usage (
    business_id UUID   NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    year_month  CHAR(7) NOT NULL,    -- e.g. '2026-05'
    msg_count   INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (business_id, year_month)
);
