CREATE TABLE liveroom_rooms (
    id                        UUID            NOT NULL,
    created_at                TIMESTAMPTZ(6)  NOT NULL,
    updated_at                TIMESTAMPTZ(6)  NOT NULL,
    created_by                VARCHAR(255)    NOT NULL,
    updated_by                VARCHAR(255)    NOT NULL,
    deleted                   BOOLEAN         NOT NULL DEFAULT FALSE,
    version                   BIGINT          NOT NULL DEFAULT 0,

    owner_id                  UUID            NOT NULL,
    room_code                 VARCHAR(6)      NOT NULL,
    room_name                 VARCHAR(100)    NOT NULL,
    -- Lower-cased, trimmed form of room_name. Stored rather than derived so the per-owner
    -- uniqueness rule can be a plain unique constraint instead of a functional index.
    normalized_name           VARCHAR(100)    NOT NULL,
    status                    VARCHAR(16)     NOT NULL,
    max_participants          INTEGER         NOT NULL DEFAULT 7,
    owner_grace_seconds       INTEGER         NOT NULL DEFAULT 60,
    current_participant_count INTEGER         NOT NULL DEFAULT 0,
    reserved_owner_slot       BOOLEAN         NOT NULL DEFAULT FALSE,
    owner_left_at             TIMESTAMPTZ(6),
    current_cycle_id          UUID,
    reopened_count            INTEGER         NOT NULL DEFAULT 0,
    last_reopened_at          TIMESTAMPTZ(6),
    previous_ended_at         TIMESTAMPTZ(6),
    ended_at                  TIMESTAMPTZ(6),
    ended_reason              VARCHAR(32),

    PRIMARY KEY (id),
    CONSTRAINT uk_liveroom_rooms_room_code UNIQUE (room_code),
    CONSTRAINT uk_liveroom_rooms_owner_name UNIQUE (owner_id, normalized_name),
    CONSTRAINT fk_liveroom_rooms_owner FOREIGN KEY (owner_id) REFERENCES iam_users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_liveroom_rooms_capacity CHECK (max_participants BETWEEN 1 AND 7),
    CONSTRAINT ck_liveroom_rooms_grace CHECK (owner_grace_seconds BETWEEN 30 AND 1800),
    CONSTRAINT ck_liveroom_rooms_count CHECK (current_participant_count >= 0)
);

CREATE TABLE liveroom_session_cycles (
    id            UUID            NOT NULL,
    created_at    TIMESTAMPTZ(6)  NOT NULL,
    updated_at    TIMESTAMPTZ(6)  NOT NULL,
    created_by    VARCHAR(255)    NOT NULL,
    updated_by    VARCHAR(255)    NOT NULL,
    deleted       BOOLEAN         NOT NULL DEFAULT FALSE,
    version       BIGINT          NOT NULL DEFAULT 0,

    room_id       UUID            NOT NULL,
    cycle_number  INTEGER         NOT NULL,
    started_at    TIMESTAMPTZ(6)  NOT NULL,
    ended_at      TIMESTAMPTZ(6),
    ended_reason  VARCHAR(32),

    PRIMARY KEY (id),
    CONSTRAINT uk_liveroom_session_cycles_room_number UNIQUE (room_id, cycle_number),
    CONSTRAINT fk_liveroom_session_cycles_room FOREIGN KEY (room_id) REFERENCES liveroom_rooms(id) ON DELETE CASCADE,
    CONSTRAINT ck_liveroom_session_cycles_number CHECK (cycle_number >= 1)
);

CREATE TABLE liveroom_ownership_history (
    id            UUID            NOT NULL,
    room_id       UUID            NOT NULL,
    owner_user_id UUID            NOT NULL,
    change_type   VARCHAR(32)     NOT NULL,
    reason        VARCHAR(512),
    changed_at    TIMESTAMPTZ(6)  NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_liveroom_ownership_history_room FOREIGN KEY (room_id) REFERENCES liveroom_rooms(id) ON DELETE CASCADE
);

-- Added after both tables exist because the reference is circular: a cycle belongs to a room, and a
-- room points at the cycle it is currently running. Nullable, since a room between cycles has none.
ALTER TABLE liveroom_rooms
    ADD CONSTRAINT fk_liveroom_rooms_current_cycle
    FOREIGN KEY (current_cycle_id) REFERENCES liveroom_session_cycles(id) ON DELETE SET NULL;

CREATE INDEX ix_liveroom_rooms_owner_id          ON liveroom_rooms (owner_id);
CREATE INDEX ix_liveroom_rooms_status            ON liveroom_rooms (status);
CREATE INDEX ix_liveroom_rooms_owner_created_at  ON liveroom_rooms (owner_id, created_at DESC);
-- Drives the owner-grace sweep, which scans only active rooms whose owner has stepped out.
CREATE INDEX ix_liveroom_rooms_owner_left_at     ON liveroom_rooms (owner_left_at) WHERE owner_left_at IS NOT NULL;

CREATE INDEX ix_liveroom_session_cycles_room_id  ON liveroom_session_cycles (room_id);
CREATE INDEX ix_liveroom_session_cycles_open     ON liveroom_session_cycles (room_id) WHERE ended_at IS NULL;

CREATE INDEX ix_liveroom_ownership_history_room  ON liveroom_ownership_history (room_id, changed_at DESC);
