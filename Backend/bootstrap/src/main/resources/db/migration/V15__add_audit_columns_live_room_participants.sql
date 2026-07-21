-- V15: Add missing audit columns to live_room_participants (created_by, updated_by, deleted_at, version)

ALTER TABLE live_room_participants
    ADD COLUMN created_by  VARCHAR(255)   NOT NULL DEFAULT 'system',
    ADD COLUMN updated_by  VARCHAR(255)   NOT NULL DEFAULT 'system',
    ADD COLUMN deleted_at  TIMESTAMPTZ(6),
    ADD COLUMN version     BIGINT         NOT NULL DEFAULT 0;
