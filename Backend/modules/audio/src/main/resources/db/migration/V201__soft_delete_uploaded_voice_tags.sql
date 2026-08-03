UPDATE audio_voice_tags
SET deleted = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'system'
WHERE tag_type = 'UPLOADED' AND deleted = FALSE;
