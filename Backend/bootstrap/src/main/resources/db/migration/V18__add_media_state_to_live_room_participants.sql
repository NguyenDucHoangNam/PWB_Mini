ALTER TABLE live_room_participants
  ADD COLUMN mic_muted BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN camera_off BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN last_seen_at TIMESTAMPTZ(6) NOT NULL DEFAULT NOW();

CREATE INDEX ix_participants_room_active_mic
  ON live_room_participants (room_code)
  WHERE left_at IS NULL AND deleted = FALSE AND mic_muted = FALSE;
