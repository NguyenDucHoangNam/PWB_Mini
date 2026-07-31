UPDATE voice_voice_tags
SET deleted = TRUE,
    deleted_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'system'
WHERE tag_type = 'UPLOADED' AND deleted = FALSE;
