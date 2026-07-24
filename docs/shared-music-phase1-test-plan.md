# Phase 1 — Manual Test Plan (Shared Music)

> Scope: Validate Host-first MVP — chỉ host chọn bài (chỉ bài của host); cả phòng play/pause đồng bộ.

## Chuẩn bị

1. Backend boot: `mvn -pl bootstrap spring-boot:run`
2. Frontend dev: `cd frontend && npm run dev`
3. Account dùng thử:
   - User A (PRO): `hostA@example.com` — host phòng
   - User B (USER): `guestB@example.com`
   - User C (PRO): `hostC@example.com`
4. User A upload 1 bài hát MP3 và chạy process đến `PROCESSED`.
5. Tạo phòng A với `mode=PUBLIC` và join.

## Bảng 6 kịch bản

| # | Kịch bản | Bước | Kỳ vọng |
|---|----------|------|---------|
| 1 | Host chọn bài của chính host | A mở `SongPickerDialog` → chọn bài đã PROCESSED → Submit | A và B thấy `SharedPlaybackBar` hiện `Title/Artist`, status = PAUSED, version tăng 1. WS broadcast `PLAYBACK_STATE_CHANGED` đến `/topic/room/{roomCode}/playback`. |
| 2 | Participant Play | B bấm Play trên bar | Cả A và B thấy status đổi sang PLAYING, position chạy theo `now - effectiveAt`. WS broadcast `PLAYBACK_STATE_CHANGED`. |
| 3 | Host Pause | A bấm Pause | Status về PAUSED, position dừng. WS broadcast. |
| 4 | Participant chọn bài | B vào `SongPickerDialog` (nếu thấy) → chọn bất kỳ | API trả 403 `LIVEROOM_031`. UI hiển thị toast lỗi từ `resolveLiveroomErrorMessage`. |
| 5 | Host chọn bài của user khác | A chọn bài của C (gọi trực tiếp API `POST /playback/songs`) | API trả 403 `LIVEROOM_032`. UI hiển thị toast. Bài đang phát (nếu có) không thay đổi. |
| 6 | Host chọn bài UPLOADED/PROCESSING | A chọn bài chưa PROCESSED | API trả 409 `LIVEROOM_030`. UI hiển thị toast. |

## Test bổ sung

- **Đồng bộ ngay khi vào phòng:** B reload trang khi phòng đang PLAYING → WS `requestPlaybackState` → B nhận `PLAYBACK_STATE` đầy đủ kèm `positionSeconds` đúng (sai số ≤ 1.5s).
- **Host leave = phòng tiếp tục:** A leave phòng đang PLAYING → nhạc vẫn phát cho B. `currentParticipantCount` giảm.
- **Room ENDED:** A end room → cả A/B nhận `ROOM_ENDED`; UI ẩn bottom bar.
- **Restart thời gian:** B pause sau đó play lại → position giữ nguyên (do `pause` đã ghi `positionSeconds`); play tiếp tục từ đó.

## Kiểm tra log

- BE log INFO có `songId` + `version` cho mỗi state change.
- WS broadcast đến `/topic/room/{roomCode}/playback` (topic, không phải user-queue) → cả subscriber nhận.
- WS push đến `/user/queue/room/{roomCode}/playback/state` (user-queue) cho newcomer.

## Done when

- [ ] Bảng 6/6 kịch bản pass.
- [ ] Không có race condition khi A và B cùng Play trong vòng 1s (lock `findByRoomCodeForUpdate` chỉ cho 1 người ghi tại 1 thời điểm, version phải tăng đúng).
- [ ] Presigned URL của voice module hoạt động cho cả A lẫn B (vì owner là A nhưng endpoint `/songs/{id}/stream` được bảo vệ bởi `userId == song.userId`; trong Phase 1 chỉ host được chọn bài, nên A là owner duy nhất).
