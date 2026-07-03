CREATE TABLE roles (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'system',
    updated_by VARCHAR(50) DEFAULT 'system',
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);

INSERT INTO roles (id, name, description, created_by) VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 'ROLE_USER', 'Default role for registered users', 'system'),
    ('550e8400-e29b-41d4-a716-446655440002', 'ROLE_USER_PRO', 'Producer role with extended privileges', 'system'),
    ('550e8400-e29b-41d4-a716-446655440003', 'ROLE_ADMIN', 'Administrator with full system access', 'system');
