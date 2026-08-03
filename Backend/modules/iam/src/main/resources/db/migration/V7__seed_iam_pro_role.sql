INSERT INTO iam_roles (id, name, description, created_at, updated_at, created_by, updated_by, deleted, version)
VALUES (gen_random_uuid(), 'PRO', 'Pro user role', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', FALSE, 0)
ON CONFLICT (name) DO NOTHING;
