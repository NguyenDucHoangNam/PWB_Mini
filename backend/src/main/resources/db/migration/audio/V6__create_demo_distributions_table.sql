CREATE TABLE demo_distributions (
    id UUID PRIMARY KEY,
    thread_id UUID NOT NULL,
    demo_id UUID NOT NULL,
    recipient_email VARCHAR(100) NOT NULL,
    share_token UUID UNIQUE NOT NULL,
    allow_download BOOLEAN NOT NULL DEFAULT false,
    is_revoked BOOLEAN NOT NULL DEFAULT false,
    revoked_at TIMESTAMP NULL,
    revoke_reason VARCHAR(50) NULL,
    play_count INT NOT NULL DEFAULT 0,
    last_played_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_dist_thread FOREIGN KEY (thread_id) REFERENCES shared_threads(id),
    CONSTRAINT fk_dist_demo FOREIGN KEY (demo_id) REFERENCES demos(id)
);

CREATE INDEX idx_distributions_demo_created
    ON demo_distributions (demo_id, created_at DESC)
    WHERE is_revoked = false AND deleted = false;

CREATE INDEX idx_distributions_token_active
    ON demo_distributions (share_token)
    WHERE is_revoked = false;

CREATE INDEX idx_distributions_thread_created
    ON demo_distributions (thread_id, created_at DESC)
    WHERE deleted = false;

CREATE OR REPLACE FUNCTION fn_touch_shared_thread() RETURNS TRIGGER AS $$
BEGIN
    UPDATE shared_threads
    SET last_interacted_at = NOW()
    WHERE id = NEW.thread_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_distributions_touch_thread
    AFTER INSERT ON demo_distributions
    FOR EACH ROW
    EXECUTE FUNCTION fn_touch_shared_thread();
