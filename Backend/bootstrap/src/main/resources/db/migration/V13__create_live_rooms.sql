-- V13: Create live_rooms table for LiveRoom module

CREATE TABLE live_rooms (
    id                       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at               TIMESTAMPTZ(6)  NOT NULL,
    updated_at               TIMESTAMPTZ(6)  NOT NULL,
    created_by               VARCHAR(255)    NOT NULL DEFAULT 'system',
    updated_by               VARCHAR(255)    NOT NULL DEFAULT 'system',
    deleted                  BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at               TIMESTAMPTZ(6),
    version                  BIGINT          NOT NULL DEFAULT 0,

    host_user_id             UUID            NOT NULL,
    room_code                VARCHAR(6)      NOT NULL,
    title                    VARCHAR(200)    NOT NULL,
    description              VARCHAR(1000),
    mode                     VARCHAR(32)     NOT NULL,
    password_hash            VARCHAR(255),
    max_participants         INTEGER         NOT NULL DEFAULT 50,
    status                   VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    current_participant_count INTEGER        NOT NULL DEFAULT 0,
    scheduled_start_at       TIMESTAMPTZ(6),
    started_at               TIMESTAMPTZ(6),
    ended_at                 TIMESTAMPTZ(6),

    PRIMARY KEY (id),
    CONSTRAINT uk_live_rooms_room_code UNIQUE (room_code),
    CONSTRAINT fk_live_rooms_host FOREIGN KEY (host_user_id) REFERENCES iam_users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_live_rooms_mode CHECK (mode IN ('PUBLIC', 'PRIVATE', 'INVITE_ONLY', 'PASSWORD')),
    CONSTRAINT chk_live_rooms_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ENDED')),
    CONSTRAINT chk_live_rooms_capacity CHECK (max_participants BETWEEN 2 AND 500),
    CONSTRAINT chk_live_rooms_participants CHECK (current_participant_count >= 0),
    CONSTRAINT chk_live_rooms_password CHECK (
        (mode = 'PASSWORD' AND password_hash IS NOT NULL) OR
        (mode <> 'PASSWORD' AND password_hash IS NULL)
    )
);

CREATE UNIQUE INDEX uk_live_rooms_host_active
    ON live_rooms (host_user_id)
    WHERE status = 'ACTIVE' AND deleted = FALSE;

CREATE INDEX ix_live_rooms_host_user_id ON live_rooms (host_user_id);
CREATE INDEX ix_live_rooms_status ON live_rooms (status) WHERE deleted = FALSE;
CREATE INDEX ix_live_rooms_room_code ON live_rooms (room_code) WHERE deleted = FALSE;
CREATE INDEX ix_live_rooms_created_at ON live_rooms (created_at DESC);