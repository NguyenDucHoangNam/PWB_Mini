-- The paid tier is gone; every account gets the whole feature set.
--
-- This must land before the application code that drops PRO from RoleName. iam_users.role_id is a
-- foreign key into iam_roles, and the mapper turns whatever name it finds there into the enum, so a
-- single surviving row pointing at PRO makes rehydrating that user throw IllegalArgumentException at
-- login rather than at startup, where it would be noticed.
--
-- The PRO row itself is seeded by V7, which stays as it is: rewriting history would leave databases
-- that already ran it with a checksum mismatch.
UPDATE iam_users
SET role_id = (SELECT id FROM iam_roles WHERE name = 'USER'),
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'system'
WHERE role_id = (SELECT id FROM iam_roles WHERE name = 'PRO');

DELETE FROM iam_roles WHERE name = 'PRO';
