-- What the room is listening to. One row per room at most — shared listening is a single track, not
-- a queue, so there is nothing to order and nothing to hold in reserve.
--
-- Scoped to the room rather than to the session cycle, unlike chat: the row is cleared when a new
-- meeting starts, and keeping it through an ending is what lets an undone ending bring the music
-- back with everything else.
CREATE TABLE liveroom_playback_states (
    id                    UUID             NOT NULL,
    created_at            TIMESTAMPTZ(6)   NOT NULL,
    updated_at            TIMESTAMPTZ(6)   NOT NULL,
    created_by            VARCHAR(255)     NOT NULL,
    updated_by            VARCHAR(255)     NOT NULL,
    deleted               BOOLEAN          NOT NULL DEFAULT FALSE,
    -- Optimistic lock. Two people tapping play within the same instant is the expected collision
    -- here: rare, and cheap for the loser to retry, which is why this is not the row-level lock the
    -- capacity check uses.
    version               BIGINT           NOT NULL DEFAULT 0,

    room_id               UUID             NOT NULL,
    -- No foreign key to the audio library on purpose. It belongs to another module, and a song
    -- removed from somebody's library must not fail or cascade into a room that once played it —
    -- the snapshot below is what keeps the player readable if that happens.
    song_id               UUID,
    song_owner_id         UUID,
    song_title            VARCHAR(255),
    song_artist           VARCHAR(255),
    song_duration_seconds INTEGER,
    status                VARCHAR(16)      NOT NULL,
    -- Where the track stood at started_at, not where it stands now. While the status is PLAYING the
    -- live position is this plus the time since; storing the live value instead would need a writer
    -- ticking every second for no gain.
    position_seconds      DOUBLE PRECISION NOT NULL DEFAULT 0,
    volume_percent        INTEGER          NOT NULL DEFAULT 80,
    started_at            TIMESTAMPTZ(6),
    last_updated_at       TIMESTAMPTZ(6)   NOT NULL,
    last_updated_by       UUID,
    -- Rises by one on every change, so a client can drop an event that reaches it out of order.
    sequence_number       BIGINT           NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uk_liveroom_playback_states_room UNIQUE (room_id),
    CONSTRAINT fk_liveroom_playback_states_room FOREIGN KEY (room_id) REFERENCES liveroom_rooms(id) ON DELETE CASCADE,
    CONSTRAINT ck_liveroom_playback_states_volume   CHECK (volume_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_liveroom_playback_states_position CHECK (position_seconds >= 0)
);
