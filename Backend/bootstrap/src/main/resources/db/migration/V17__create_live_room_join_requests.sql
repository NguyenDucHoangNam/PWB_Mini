-- V17: Create live_room_join_requests table for waiting-room (host approves join)

CREATE TABLE live_room_join_requests (
    id                 UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at         TIMESTAMPTZ(6)  NOT NULL,
    updated_at         TIMESTAMPTZ(6)  NOT NULL,
    created_by         VARCHAR(255)    NOT NULL DEFAULT 'system',
    updated_by         VARCHAR(255)    NOT NULL DEFAULT 'system',
    deleted            BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at         TIMESTAMPTZ(6),
    version            BIGINT          NOT NULL DEFAULT 0,

    room_code          VARCHAR(6)      NOT NULL,
    user_id            UUID            NOT NULL,
    display_name       VARCHAR(100)    NOT NULL,
    message            VARCHAR(500),
    status             VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    decision_reason    VARCHAR(500),
    decided_by_user_id UUID,
    decided_at         TIMESTAMPTZ(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_join_requests_room FOREIGN KEY (room_code) REFERENCES live_rooms(room_code) ON DELETE CASCADE,
    CONSTRAINT fk_join_requests_user FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_join_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uk_active_join_request_per_user_per_room
    ON live_room_join_requests (room_code, user_id)
    WHERE status = 'PENDING' AND deleted = FALSE;

CREATE INDEX ix_join_requests_room_status
    ON live_room_join_requests (room_code, status)
    WHERE deleted = FALSE;

CREATE INDEX ix_join_requests_user
    ON live_room_join_requests (user_id);

CREATE INDEX ix_join_requests_decided_at
    ON live_room_join_requests (decided_at DESC)
    WHERE decided_at IS NOT NULL;
