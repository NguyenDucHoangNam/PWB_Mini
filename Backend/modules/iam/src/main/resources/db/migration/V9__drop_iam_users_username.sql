ALTER TABLE iam_users ALTER COLUMN username DROP NOT NULL;
DROP INDEX IF EXISTS ix_iam_users_username;
ALTER TABLE iam_users DROP CONSTRAINT IF EXISTS uk_iam_users_username;
ALTER TABLE iam_users DROP COLUMN IF EXISTS username;
ALTER TABLE iam_users DROP COLUMN IF EXISTS is_provisional_username;
ALTER TABLE iam_users ALTER COLUMN full_name SET NOT NULL;
