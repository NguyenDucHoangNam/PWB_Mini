-- The room's group conversation, one row per message.
--
-- Scoped to the session cycle rather than the room: reopening a room opens a fresh conversation,
-- while the previous meeting's transcript stays on record. Nothing here is ever deleted when a room
-- ends — the transcript is the record of what was said, and outliving the meeting is the point.
CREATE TABLE liveroom_chat_messages (
    id         UUID            NOT NULL,
    created_at TIMESTAMPTZ(6)  NOT NULL,
    updated_at TIMESTAMPTZ(6)  NOT NULL,
    created_by VARCHAR(255)    NOT NULL,
    updated_by VARCHAR(255)    NOT NULL,
    deleted    BOOLEAN         NOT NULL DEFAULT FALSE,
    version    BIGINT          NOT NULL DEFAULT 0,

    room_id    UUID            NOT NULL,
    cycle_id   UUID            NOT NULL,
    user_id    UUID            NOT NULL,
    -- Snapshot of the sender's address, as on the participant row. Email is the display name and is
    -- immutable in IAM, so this keeps a transcript readable without reaching into another module.
    user_email VARCHAR(255)    NOT NULL,
    content    VARCHAR(500)    NOT NULL,
    sent_at    TIMESTAMPTZ(6)  NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_liveroom_chat_messages_room  FOREIGN KEY (room_id)  REFERENCES liveroom_rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_liveroom_chat_messages_cycle FOREIGN KEY (cycle_id) REFERENCES liveroom_session_cycles(id) ON DELETE CASCADE,
    CONSTRAINT fk_liveroom_chat_messages_user  FOREIGN KEY (user_id)  REFERENCES iam_users(id) ON DELETE RESTRICT,
    -- Whitespace is stripped before the row is written, so an empty content column can only mean a
    -- write that bypassed the application.
    CONSTRAINT ck_liveroom_chat_messages_content CHECK (LENGTH(content) > 0)
);

-- Serves the history query, which reads one cycle newest-first. The id is part of the ordering so a
-- page boundary is still deterministic when two messages share a timestamp.
CREATE INDEX ix_liveroom_chat_messages_cycle_sent
    ON liveroom_chat_messages (cycle_id, sent_at DESC, id DESC);
