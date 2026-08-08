-- Append-only record of the owner acting on somebody. Kicking and silencing a person are the two
-- things one participant can do to another, so they are the two things that need to be answerable
-- for afterwards.
CREATE TABLE liveroom_admin_actions (
    id             UUID            NOT NULL,
    room_id        UUID            NOT NULL,
    cycle_id       UUID,
    actor_user_id  UUID            NOT NULL,
    target_user_id UUID            NOT NULL,
    action_type    VARCHAR(32)     NOT NULL,
    reason         VARCHAR(512),
    created_at     TIMESTAMPTZ(6)  NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_liveroom_admin_actions_room FOREIGN KEY (room_id) REFERENCES liveroom_rooms(id) ON DELETE CASCADE,
    -- Nullable and not cascaded from the cycle: the record must outlive the meeting it happened in.
    CONSTRAINT fk_liveroom_admin_actions_cycle FOREIGN KEY (cycle_id) REFERENCES liveroom_session_cycles(id) ON DELETE SET NULL
);

CREATE INDEX ix_liveroom_admin_actions_room   ON liveroom_admin_actions (room_id, created_at DESC);
CREATE INDEX ix_liveroom_admin_actions_target ON liveroom_admin_actions (target_user_id, created_at DESC);

-- When the owner silenced this participant, and until when they may not switch themselves back on.
-- Kept on the participant rather than derived from the audit log: it is read on every media toggle.
ALTER TABLE liveroom_participants
    ADD COLUMN mic_muted_by_owner_at     TIMESTAMPTZ(6),
    ADD COLUMN mic_unmute_cooldown_until TIMESTAMPTZ(6);
