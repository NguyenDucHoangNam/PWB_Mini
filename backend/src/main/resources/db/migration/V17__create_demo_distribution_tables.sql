-- ============================================================================
-- V17: Create demo distribution tables (docs 03 §3.1)
--      - shared_threads
--      - demo_distributions
--      - email_blacklisted_domains
-- ============================================================================

CREATE TABLE shared_threads (
    id                     UUID         PRIMARY KEY,
    producer_id            UUID         NOT NULL REFERENCES users(id),
    recipient_email        VARCHAR(100) NOT NULL,
    recipient_email_hash   VARCHAR(64)  NOT NULL,
    last_interacted_at     TIMESTAMP    NOT NULL,
    created_at             TIMESTAMP    NOT NULL,
    updated_at             TIMESTAMP    NOT NULL,
    created_by             VARCHAR(64),
    updated_by             VARCHAR(64),
    deleted_at             TIMESTAMP,
    deleted_by             VARCHAR(64),
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_threads_pair      UNIQUE (producer_id, recipient_email),
    CONSTRAINT uq_threads_pair_hash UNIQUE (producer_id, recipient_email_hash)
);

CREATE INDEX idx_threads_producer_email_prefix
    ON shared_threads (producer_id, recipient_email text_pattern_ops);

CREATE INDEX idx_threads_producer_last_interacted
    ON shared_threads (producer_id, last_interacted_at DESC);

CREATE TABLE demo_distributions (
    id                 UUID         PRIMARY KEY,
    thread_id          UUID         NOT NULL REFERENCES shared_threads(id),
    demo_id            UUID         NOT NULL REFERENCES demos(id),
    recipient_email    VARCHAR(100) NOT NULL,
    share_token        UUID         NOT NULL UNIQUE,
    allow_download     BOOLEAN      NOT NULL DEFAULT false,
    is_revoked         BOOLEAN      NOT NULL DEFAULT false,
    revoked_at         TIMESTAMP,
    play_count         INT          NOT NULL DEFAULT 0,
    last_played_at     TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL,
    created_by         VARCHAR(64),
    updated_by         VARCHAR(64),
    deleted_at         TIMESTAMP,
    deleted_by         VARCHAR(64),
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT demo_distributions_play_count_check CHECK (play_count >= 0)
);

CREATE INDEX idx_distributions_demo_created
    ON demo_distributions (demo_id, created_at DESC)
    WHERE is_revoked = false;

CREATE INDEX idx_distributions_token_active
    ON demo_distributions (share_token)
    WHERE is_revoked = false;

CREATE TABLE email_blacklisted_domains (
    id          UUID         PRIMARY KEY,
    domain      VARCHAR(255) NOT NULL UNIQUE,
    reason      VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    created_by  VARCHAR(64),
    updated_by  VARCHAR(64),
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(64),
    version     BIGINT       NOT NULL DEFAULT 0
);

INSERT INTO email_blacklisted_domains (id, domain, reason, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'mailinator.com',    'Disposable email domain',  NOW(), NOW()),
    (gen_random_uuid(), 'tempmail.com',      'Disposable email domain',  NOW(), NOW()),
    (gen_random_uuid(), 'guerrillamail.com', 'Disposable email domain',  NOW(), NOW()),
    (gen_random_uuid(), 'yopmail.com',       'Disposable email domain',  NOW(), NOW()),
    (gen_random_uuid(), 'trashmail.com',     'Disposable email domain',  NOW(), NOW());

CREATE OR REPLACE FUNCTION fn_touch_shared_thread() RETURNS TRIGGER AS $$
BEGIN
    UPDATE shared_threads
       SET last_interacted_at = NOW(),
           updated_at = NOW()
     WHERE id = NEW.thread_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_distributions_touch_thread
AFTER INSERT ON demo_distributions
FOR EACH ROW
EXECUTE FUNCTION fn_touch_shared_thread();