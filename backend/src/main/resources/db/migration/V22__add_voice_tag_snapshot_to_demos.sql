-- ============================================================================
-- V22: Snapshot voice tag content into demos (docs 03 — IDOR hardening)
--     When a demo is created, copy owner_id/text_content/language_code/voice_name
--     from voice_tags so the demo remains self-describing if the voice tag is
--     later deleted by the owner.
-- ============================================================================

ALTER TABLE demos
    ADD COLUMN voice_tag_owner_id UUID,
    ADD COLUMN voice_tag_text_content VARCHAR(500),
    ADD COLUMN voice_tag_language_code VARCHAR(10),
    ADD COLUMN voice_tag_voice_name VARCHAR(100);

CREATE INDEX idx_demos_voice_tag_id_status
    ON demos (voice_tag_id, status)
    WHERE voice_tag_id IS NOT NULL;
