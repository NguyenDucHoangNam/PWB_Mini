ALTER TABLE iam_users ADD COLUMN ban_reason VARCHAR(500);
ALTER TABLE iam_users ADD COLUMN banned_at TIMESTAMP;
ALTER TABLE iam_users ADD COLUMN banned_by UUID;
