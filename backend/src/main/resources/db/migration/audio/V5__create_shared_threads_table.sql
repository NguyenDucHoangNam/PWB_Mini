CREATE TABLE shared_threads (
    id UUID PRIMARY KEY,
    producer_id UUID NOT NULL,
    recipient_email VARCHAR(100) NOT NULL,
    recipient_email_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    last_interacted_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_threads_producer FOREIGN KEY (producer_id) REFERENCES users(id),
    CONSTRAINT uq_threads_pair UNIQUE (producer_id, recipient_email),
    CONSTRAINT uq_threads_pair_hash UNIQUE (producer_id, recipient_email_hash)
);

CREATE INDEX idx_threads_producer_email_prefix
    ON shared_threads (producer_id, recipient_email text_pattern_ops);

CREATE INDEX idx_threads_producer_last_interacted
    ON shared_threads (producer_id, last_interacted_at DESC);

CREATE TABLE email_blacklisted_domains (
    id UUID PRIMARY KEY,
    domain VARCHAR(255) NOT NULL UNIQUE,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO email_blacklisted_domains (id, domain, reason, created_at) VALUES
    (gen_random_uuid(), 'mailinator.com', 'Disposable email provider', NOW()),
    (gen_random_uuid(), 'tempmail.com', 'Disposable email provider', NOW()),
    (gen_random_uuid(), 'guerrillamail.com', 'Disposable email provider', NOW()),
    (gen_random_uuid(), 'yopmail.com', 'Disposable email provider', NOW()),
    (gen_random_uuid(), 'trashmail.com', 'Disposable email provider', NOW()),
    (gen_random_uuid(), '10minutemail.com', 'Disposable email provider', NOW()),
    (gen_random_uuid(), 'getnada.com', 'Disposable email provider', NOW());
