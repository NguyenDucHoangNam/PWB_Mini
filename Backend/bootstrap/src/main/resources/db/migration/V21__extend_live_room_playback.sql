ALTER TABLE live_room_playback
    ADD COLUMN playback_rate NUMERIC(4, 2) NOT NULL DEFAULT 1.00,
    ADD COLUMN loop_mode VARCHAR(16) NOT NULL DEFAULT 'OFF',
    ADD COLUMN shuffle_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE live_room_playback
    ADD CONSTRAINT ck_live_room_playback_rate CHECK (playback_rate IN (1.00, 1.50, 2.00)),
    ADD CONSTRAINT ck_live_room_playback_loop CHECK (loop_mode IN ('OFF', 'ONE'));