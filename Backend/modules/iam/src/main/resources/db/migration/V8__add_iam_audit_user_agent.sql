ALTER TABLE iam_audit_logs
    ADD COLUMN user_agent VARCHAR(512);

CREATE INDEX ix_iam_audit_user_agent ON iam_audit_logs(user_agent);
