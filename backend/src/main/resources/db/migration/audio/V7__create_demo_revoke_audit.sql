CREATE TABLE demo_revoke_audit (
    id UUID PRIMARY KEY,
    distribution_id UUID NOT NULL,
    demo_id UUID NOT NULL,
    revoked_by_user_id UUID NOT NULL,
    revoked_at TIMESTAMP NOT NULL DEFAULT NOW(),
    reason VARCHAR(50),
    ip_subnet_hash VARCHAR(64),
    user_agent VARCHAR(255),
    CONSTRAINT fk_audit_dist FOREIGN KEY (distribution_id) REFERENCES demo_distributions(id),
    CONSTRAINT fk_audit_user FOREIGN KEY (revoked_by_user_id) REFERENCES users(id)
);

CREATE INDEX idx_audit_dist ON demo_revoke_audit (distribution_id);
CREATE INDEX idx_audit_time ON demo_revoke_audit (revoked_at);
CREATE INDEX idx_audit_demo ON demo_revoke_audit (demo_id);