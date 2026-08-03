CREATE TABLE iam_audit_logs (
    id              UUID         PRIMARY KEY,
    event_type      VARCHAR(64)  NOT NULL,
    actor_id        UUID,
    actor_email     VARCHAR(255),
    target_id       UUID,
    client_ip       VARCHAR(64),
    success         BOOLEAN      NOT NULL,
    failure_reason  VARCHAR(512),
    metadata        JSONB,
    occurred_at     TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_iam_audit_actor     ON iam_audit_logs(actor_id);
CREATE INDEX ix_iam_audit_event     ON iam_audit_logs(event_type);
CREATE INDEX ix_iam_audit_occurred  ON iam_audit_logs(occurred_at);
