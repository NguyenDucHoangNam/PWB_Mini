ALTER TABLE live_room_playback
    ADD COLUMN audio_source VARCHAR(16);

ALTER TABLE live_room_playback
    ADD CONSTRAINT ck_live_room_playback_audio_source
        CHECK (audio_source IS NULL OR audio_source IN ('ORIGINAL', 'PROCESSED'));