-- V14: Create live_room_participants table for LiveRoom participant tracking

CREATE TABLE live_room_participants (
    id            UUID            NOT NULL DEFAULT gen_random_uuid(),
    room_code     VARCHAR(6)      NOT NULL,
    user_id       UUID            NOT NULL,
    display_name  VARCHAR(100)    NOT NULL,
    role_at_join  VARCHAR(32)     NOT NULL,
    joined_at     TIMESTAMPTZ(6)  NOT NULL DEFAULT NOW(),
    left_at       TIMESTAMPTZ(6),
    created_at    TIMESTAMPTZ(6)  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ(6)  NOT NULL DEFAULT NOW(),
    deleted       BOOLEAN         NOT NULL DEFAULT FALSE,

    PRIMARY KEY (id),
    CONSTRAINT fk_participants_room FOREIGN KEY (room_code) REFERENCES live_rooms(room_code) ON DELETE CASCADE,
    CONSTRAINT fk_participants_user FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_participants_role CHECK (role_at_join IN ('USER', 'PRO', 'ADMIN')),
    CONSTRAINT chk_participants_left_after_join CHECK (left_at IS NULL OR left_at >= joined_at)
);

CREATE UNIQUE INDEX uk_active_participant_per_user_per_room
    ON live_room_participants (room_code, user_id)
    WHERE left_at IS NULL AND deleted = FALSE;

CREATE INDEX ix_participants_room_active
    ON live_room_participants (room_code)
    WHERE left_at IS NULL AND deleted = FALSE;

CREATE INDEX ix_participants_user
    ON live_room_participants (user_id, joined_at DESC);

CREATE INDEX ix_participants_room_history
    ON live_room_participants (room_code, joined_at DESC);