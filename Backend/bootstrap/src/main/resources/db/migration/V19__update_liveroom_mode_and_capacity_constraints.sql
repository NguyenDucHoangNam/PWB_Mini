-- V19: Update live_rooms constraints for new room modes and reduced capacity

ALTER TABLE live_rooms DROP CONSTRAINT IF EXISTS chk_live_rooms_mode;
ALTER TABLE live_rooms ADD CONSTRAINT chk_live_rooms_mode
    CHECK (mode IN ('PUBLIC', 'PRIVATE'));

ALTER TABLE live_rooms DROP CONSTRAINT IF EXISTS chk_live_rooms_capacity;
ALTER TABLE live_rooms ADD CONSTRAINT chk_live_rooms_capacity
    CHECK (max_participants BETWEEN 2 AND 5);
