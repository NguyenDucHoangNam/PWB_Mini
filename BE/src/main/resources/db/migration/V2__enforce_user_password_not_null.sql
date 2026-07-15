UPDATE users
SET password = 'OAUTH_PLACEHOLDER_' || id::text
WHERE password IS NULL;

ALTER TABLE users
    ALTER COLUMN password SET NOT NULL;