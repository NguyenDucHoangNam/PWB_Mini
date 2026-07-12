-- ============================================================================
-- V20: Live Room — Create & Configure (docs/2. Live Room/01_create_room.md)
--      - rooms table
--      - partial unique index for ACTIVE room codes (race condition guard)
-- ============================================================================

CREATE TABLE rooms (
    id                 UUID         PRIMARY KEY,
    room_code          VARCHAR(6)   NOT NULL,
    host_id            UUID         NOT NULL REFERENCES users(id),
    mode               VARCHAR(15)  NOT NULL,
    status             VARCHAR(15)  NOT NULL,
    max_participants   INT          NOT NULL DEFAULT 7,
    created_at         TIMESTAMP    NOT NULL,
    closed_at          TIMESTAMP
);

CREATE UNIQUE INDEX idx_rooms_active_code
    ON rooms (room_code)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_rooms_host_status
    ON rooms (host_id, status);