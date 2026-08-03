DELETE FROM audio_song_tag_configs
WHERE voice_tag_id IN (SELECT id FROM audio_voice_tags WHERE deleted = TRUE);

DELETE FROM audio_voice_tags WHERE deleted = TRUE;

CREATE INDEX ix_audio_voice_tags_user_created_at ON audio_voice_tags (user_id, created_at DESC);

DROP INDEX IF EXISTS ix_audio_voice_tags_user_id;
