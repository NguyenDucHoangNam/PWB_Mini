CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    password VARCHAR(100),
    full_name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    role_id VARCHAR(36) NOT NULL CONSTRAINT fk_users_role_id REFERENCES roles(id),
    oauth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    oauth_id VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'system',
    updated_by VARCHAR(50) DEFAULT 'system',
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX idx_users_username_active ON users(username) WHERE deleted = FALSE;
CREATE UNIQUE INDEX idx_users_email_active ON users(email) WHERE deleted = FALSE;
CREATE UNIQUE INDEX idx_users_oauth ON users(oauth_provider, oauth_id) WHERE deleted = FALSE;
CREATE INDEX idx_users_pending_cleanup ON users(status, created_at) WHERE status = 'PENDING_VERIFICATION';
