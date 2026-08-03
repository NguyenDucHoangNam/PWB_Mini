DELETE FROM audio_songs WHERE status = 'DELETED';
DELETE FROM audio_song_tag_configs WHERE deleted = TRUE;
CREATE INDEX ix_audio_songs_user_created_at ON audio_songs (user_id, created_at DESC);
DROP INDEX IF EXISTS ix_audio_songs_user_id;
