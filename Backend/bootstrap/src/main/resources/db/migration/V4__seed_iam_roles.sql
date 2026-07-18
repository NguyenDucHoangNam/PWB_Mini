INSERT INTO iam_roles (id, name, description, created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'USER',  'Default user role',      NOW(), NOW(), 'system', 'system', FALSE, 0),
    ('00000000-0000-0000-0000-000000000002', 'PRO',   'Professional user role', NOW(), NOW(), 'system', 'system', FALSE, 0),
    ('00000000-0000-0000-0000-000000000003', 'ADMIN', 'Administrator role',     NOW(), NOW(), 'system', 'system', FALSE, 0)
ON CONFLICT (name) DO NOTHING;
