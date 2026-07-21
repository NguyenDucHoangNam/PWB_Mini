-- V16: Drop password_hash column and relax check constraints for LiveRoom (single PUBLIC mode)

ALTER TABLE live_rooms DROP CONSTRAINT IF EXISTS chk_live_rooms_password;

ALTER TABLE live_rooms DROP CONSTRAINT IF EXISTS chk_live_rooms_mode;

ALTER TABLE live_rooms ADD CONSTRAINT chk_live_rooms_mode
    CHECK (mode IN ('PUBLIC'));

ALTER TABLE live_rooms DROP COLUMN IF EXISTS password_hash;
