-- One stored object backs exactly one song.
--
-- Without this the same original_s3_key could be registered twice: deleting either song then removed
-- the object from the bucket, leaving the other row listing normally with audio that no longer exists.
--
-- Older rows are deduplicated first, keeping the earliest registration of each key, because the index
-- cannot be created while duplicates are present.
DELETE FROM audio_song_tag_configs
WHERE song_id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY original_s3_key ORDER BY created_at, id) AS rn
        FROM audio_songs
    ) ranked
    WHERE ranked.rn > 1
);

DELETE FROM audio_songs
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY original_s3_key ORDER BY created_at, id) AS rn
        FROM audio_songs
    ) ranked
    WHERE ranked.rn > 1
);

CREATE UNIQUE INDEX ux_audio_songs_original_s3_key ON audio_songs (original_s3_key);
