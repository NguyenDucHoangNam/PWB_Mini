ALTER TABLE audio_song_tag_configs
    ADD COLUMN ducking_percentage INTEGER NOT NULL DEFAULT 100;

ALTER TABLE audio_song_tag_configs
    DROP COLUMN fade_in_duration_ms,
    DROP COLUMN fade_out_duration_ms;

ALTER TABLE audio_song_tag_configs
    ADD CONSTRAINT ck_audio_song_tag_configs_ducking
        CHECK (ducking_percentage BETWEEN 0 AND 100);

ALTER TABLE audio_song_tag_configs
    ADD CONSTRAINT ck_audio_song_tag_configs_volume
        CHECK (volume_percentage IS NULL OR volume_percentage BETWEEN 0 AND 100);
