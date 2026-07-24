CREATE TABLE live_room_playback (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,

    room_code VARCHAR(6) NOT NULL,
    song_id UUID,
    song_owner_user_id UUID,
    status VARCHAR(32) NOT NULL,
    position_seconds BIGINT NOT NULL DEFAULT 0,
    effective_at TIMESTAMP WITH TIME ZONE NOT NULL,
    playback_version BIGINT NOT NULL DEFAULT 0,
    changed_by_user_id UUID,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uk_live_room_playback_room_code UNIQUE (room_code),
    CONSTRAINT fk_live_room_playback_room_code
        FOREIGN KEY (room_code) REFERENCES live_rooms (room_code)
        ON DELETE CASCADE
);

CREATE INDEX ix_live_room_playback_deleted ON live_room_playback (deleted);
CREATE INDEX ix_live_room_playback_status ON live_room_playback (status);
CREATE INDEX ix_live_room_playback_song_id ON live_room_playback (song_id);
