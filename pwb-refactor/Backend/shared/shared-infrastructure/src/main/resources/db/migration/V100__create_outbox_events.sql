CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(128) NOT NULL,
    topic           VARCHAR(128) NOT NULL,
    payload_key     VARCHAR(128),
    payload         JSONB NOT NULL,
    headers         JSONB,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ(6),
    last_error      TEXT,
    next_attempt_at TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED'))
);

CREATE INDEX idx_outbox_status_next_attempt ON outbox_events (status, next_attempt_at);
CREATE INDEX idx_outbox_topic ON outbox_events (topic);
CREATE UNIQUE INDEX uk_outbox_event_id ON outbox_events (event_id);