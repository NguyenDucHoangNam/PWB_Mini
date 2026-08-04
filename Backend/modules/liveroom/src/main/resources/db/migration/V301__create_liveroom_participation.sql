-- Standing of one user in one room, spanning every session cycle the room ever runs.
-- was_approved must outlive a cycle (a reopened room lets previously admitted people back in
-- without asking again), while the reject counters are wiped on reopen — different lifetimes,
-- but both answer the same question at join time, so they share a row.
CREATE TABLE liveroom_room_members (
    id                       UUID            NOT NULL,
    created_at               TIMESTAMPTZ(6)  NOT NULL,
    updated_at               TIMESTAMPTZ(6)  NOT NULL,
    created_by               VARCHAR(255)    NOT NULL,
    updated_by               VARCHAR(255)    NOT NULL,
    deleted                  BOOLEAN         NOT NULL DEFAULT FALSE,
    version                  BIGINT          NOT NULL DEFAULT 0,

    room_id                  UUID            NOT NULL,
    user_id                  UUID            NOT NULL,
    -- TRUE only once the user has actually been inside the room; being approved and never
    -- turning up does not grant a standing pass.
    was_approved             BOOLEAN         NOT NULL DEFAULT FALSE,
    kicked_at                TIMESTAMPTZ(6),
    kicked_cooldown_until    TIMESTAMPTZ(6),
    -- Only owner-initiated rejections count towards the lockout; being turned away because the
    -- room was full is not the user's doing.
    reject_count_by_owner    INTEGER         NOT NULL DEFAULT 0,
    reject_count_by_capacity INTEGER         NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uk_liveroom_room_members_room_user UNIQUE (room_id, user_id),
    CONSTRAINT fk_liveroom_room_members_room FOREIGN KEY (room_id) REFERENCES liveroom_rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_liveroom_room_members_user FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_liveroom_room_members_counts CHECK (reject_count_by_owner >= 0 AND reject_count_by_capacity >= 0)
);

-- One user's presence within one session cycle. Scoped to the cycle, not the room, so a reopened
-- room starts with an empty roster while the previous meeting's attendance stays on record.
CREATE TABLE liveroom_participants (
    id                  UUID            NOT NULL,
    created_at          TIMESTAMPTZ(6)  NOT NULL,
    updated_at          TIMESTAMPTZ(6)  NOT NULL,
    created_by          VARCHAR(255)    NOT NULL,
    updated_by          VARCHAR(255)    NOT NULL,
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    version             BIGINT          NOT NULL DEFAULT 0,

    room_id             UUID            NOT NULL,
    cycle_id            UUID            NOT NULL,
    user_id             UUID            NOT NULL,
    -- Snapshot of the address at join time. Email is the display name and is immutable in IAM, so
    -- this is an audit record rather than a cache; it also keeps the roster readable without
    -- reaching into another module.
    user_email          VARCHAR(255)    NOT NULL,
    room_role           VARCHAR(16)     NOT NULL,
    state               VARCHAR(16)     NOT NULL,
    joined_at           TIMESTAMPTZ(6)  NOT NULL,
    left_at             TIMESTAMPTZ(6),
    camera_on           BOOLEAN         NOT NULL DEFAULT FALSE,
    mic_on              BOOLEAN         NOT NULL DEFAULT FALSE,
    mic_state           VARCHAR(20)     NOT NULL DEFAULT 'SELF_MUTED',
    last_interaction_at TIMESTAMPTZ(6),

    PRIMARY KEY (id),
    CONSTRAINT uk_liveroom_participants_cycle_user UNIQUE (cycle_id, user_id),
    CONSTRAINT fk_liveroom_participants_room FOREIGN KEY (room_id) REFERENCES liveroom_rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_liveroom_participants_cycle FOREIGN KEY (cycle_id) REFERENCES liveroom_session_cycles(id) ON DELETE CASCADE,
    CONSTRAINT fk_liveroom_participants_user FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE RESTRICT
);

CREATE TABLE liveroom_join_requests (
    id               UUID            NOT NULL,
    created_at       TIMESTAMPTZ(6)  NOT NULL,
    updated_at       TIMESTAMPTZ(6)  NOT NULL,
    created_by       VARCHAR(255)    NOT NULL,
    updated_by       VARCHAR(255)    NOT NULL,
    deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    version          BIGINT          NOT NULL DEFAULT 0,

    room_id          UUID            NOT NULL,
    cycle_id         UUID            NOT NULL,
    user_id          UUID            NOT NULL,
    user_email       VARCHAR(255)    NOT NULL,
    state            VARCHAR(32)     NOT NULL,
    rejection_reason VARCHAR(32),
    -- Supplied by the client and replayed on retry, so a double-tapped button does not put two
    -- requests in front of the owner.
    idempotency_key  VARCHAR(64)     NOT NULL,
    decided_at       TIMESTAMPTZ(6),
    decided_by       UUID,

    PRIMARY KEY (id),
    CONSTRAINT uk_liveroom_join_requests_idem UNIQUE (room_id, user_id, idempotency_key),
    CONSTRAINT fk_liveroom_join_requests_room FOREIGN KEY (room_id) REFERENCES liveroom_rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_liveroom_join_requests_cycle FOREIGN KEY (cycle_id) REFERENCES liveroom_session_cycles(id) ON DELETE CASCADE,
    CONSTRAINT fk_liveroom_join_requests_user FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE RESTRICT
);

-- At most one request may be waiting on the owner at a time, regardless of idempotency key.
CREATE UNIQUE INDEX uk_liveroom_join_requests_pending
    ON liveroom_join_requests (room_id, user_id) WHERE state = 'PENDING';

CREATE INDEX ix_liveroom_room_members_user        ON liveroom_room_members (user_id);
CREATE INDEX ix_liveroom_participants_room        ON liveroom_participants (room_id);
CREATE INDEX ix_liveroom_participants_user        ON liveroom_participants (user_id);
-- Serves both the roster query and the "who joined first" ordering.
CREATE INDEX ix_liveroom_participants_cycle_state ON liveroom_participants (cycle_id, state, joined_at);
CREATE INDEX ix_liveroom_join_requests_room_state ON liveroom_join_requests (room_id, state, created_at);
CREATE INDEX ix_liveroom_join_requests_user       ON liveroom_join_requests (user_id);
