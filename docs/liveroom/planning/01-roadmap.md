# 01 - Roadmap tổng quan

**Mục đích**: Bản đồ tổng quan 5 phase implementation, dependency, timeline.

**Cập nhật**: 2026-07-26

---

## 🎯 Tổng quan 5 Phase

```
Phase 0 ──► Phase 1 ──► Phase 2 ──► Phase 3 ──► Phase 4 ──► Phase 5
(2-3 ngày)  (5-7 ng)  (7-10 ng)   (7-10 ng)  (7-10 ng)  (5-7 ng)
Foundation  Create+Join Lifecycle  Realtime   Music+Annot  Admin+Polish
                              + Media
```

**Tổng**: 33-47 ngày làm việc (1-2 tháng rưỡi)

---

## 📦 Phase 0: Foundation (2-3 ngày)

**Mục tiêu**: Codebase sạch, build pass, "Hello World" chạy được.

**Deliverable**:
- [ ] Maven module `liveroom` skeleton
- [ ] 11 entity classes (empty, chỉ field + Lombok)
- [ ] Flyway migration `V1__liveroom_initial_schema.sql`
- [ ] WebSocketConfig + STOMP setup
- [ ] MessageSource (i18n) setup
- [ ] JWT Auth filter + CORS
- [ ] Health check endpoint
- [ ] FE: `features/liveroom/` folder + base hooks
- [ ] API gateway route

**Doc tham chiếu**:
- `liveroom-data-model.md` §2-3 (schema)
- `liveroom-concurrency.md` §1-2 (locking strategy)
- `liveroom-ws-protocol.md` §1-2 (WS config)

**Definition of Done**:
- BE build pass, app start, Swagger accessible
- FE build pass, route `/live-room/test` render
- DB schema migrated thành công

**Chi tiết**: xem `02-phase-0-foundation.md`

---

## 🎯 Phase 1: Create + Join (5-7 ngày)

**Mục tiêu**: Owner tạo phòng → User nhập code → Vào phòng ACTIVE.

**Deliverable**:
- [ ] BE: 5 endpoints (create, get-by-code, join-request, approve, get-state)
- [ ] BE: 3 WS events (JOIN_REQUEST_CREATED, REQUEST_APPROVED, PARTICIPANT_JOINED)
- [ ] FE: 4 screens (SC-01 Create, SC-03 Pre-Join, SC-04 Waiting, SC-06 In-Room basic)
- [ ] i18n: ~10 keys
- [ ] E2E test: 2 browsers vào cùng phòng

**Doc tham chiếu**:
- `liveroom-api-spec.md` §1.1-1.3, §2.1-2.2
- `liveroom-state-machines.md` §1 (JoinRequest states)
- `liveroom-screen-inventory.md` SC-01/03/04/06
- `liveroom-permission-matrix.md` §1-2

**Definition of Done**:
- Owner tạo phòng → user nhập code → request → owner approve → cả 2 thấy nhau trong phòng
- WS real-time hiển thị participant list
- 2 browsers test pass

**Chi tiết**: xem `03-phase-1-create-join.md`

---

## 🔄 Phase 2: Lifecycle (7-10 ngày)

**Mục tiêu**: Reject, leave, end, reopen + edge cases.

**Deliverable**:
- [ ] BE: 6 endpoints (reject, cancel, rejoin, leave, end, reopen)
- [ ] BE: 9 WS events (REQUEST_REJECTED, PARTICIPANT_LEFT, OWNER_LEFT, ROOM_ENDED, ROOM_REVIVED, ROOM_REOPENED, CAPACITY_REACHED, ...)
- [ ] BE: 3 jobs (GraceExpiry, EmptyRoomTimeout, ParticipantReconnectTimeout)
- [ ] FE: 2 screens (SC-02 Owner Waiting, SC-03b Rejected)
- [ ] FE: owner leave banner + grace timer + end undo (5s)
- [ ] i18n: ~25 keys
- [ ] E2E test: 15+ edge cases

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.3-2.8
- `liveroom-state-machines.md` §2 (leave), §3 (reopen)
- `liveroom-concurrency.md` §2 (pessimistic lock), §2.4 (UPSERT)
- `liveroom-jobs.md` §2-4

**Definition of Done**:
- Capacity full → reject đúng lý do (OWNER_REJECT vs CAPACITY)
- Owner leave → grace 60s → auto-end nếu không rejoin
- End room + undo trong 5s hoạt động
- 3 rejects → locked
- Multi-tab conflict detected

**Chi tiết**: xem `04-phase-2-lifecycle.md`

---

## 🎥 Phase 3: Realtime + Media (7-10 ngày)

**Mục tiêu**: WebRTC mic/cam, chat real-time, WS ổn định.

**Deliverable**:
- [ ] BE: WebRTC signaling endpoint
- [ ] BE: 2 endpoints (POST/GET chat messages)
- [ ] BE: 3 WS events (CHAT_MESSAGE_RECEIVED, CHAT_HISTORY_SNAPSHOT, ...)
- [ ] BE: 2 jobs (IdleGhostIndicator, ParticipantReconnectTimeout)
- [ ] FE: WebRTC peer manager + media tile
- [ ] FE: Chat panel (infinite scroll, 200 messages)
- [ ] FE: WS reconnect (exponential backoff)
- [ ] FE: Multi-tab detection (BroadcastChannel)
- [ ] i18n: ~20 keys
- [ ] E2E test: 2 browsers voice chat + chat

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.4-2.5
- `liveroom-ws-protocol.md` §4.2-4.4
- `liveroom-jobs.md` §4-5

**Definition of Done**:
- 2 browsers voice chat được
- Chat realtime + load history 200 messages
- WS reconnect tự động sau network drop
- 2 tab cùng user → 1 active, 1 warning

**Chi tiết**: xem `05-phase-3-realtime-media.md`

---

## 🎵 Phase 4: Music + Annotation (7-10 ngày)

**Mục tiêu**: Shared music, annotation với optimistic lock + sequence number.

**Deliverable**:
- [ ] BE: STOMP `/music/play`, `/pause`, `/seek`, `/volume`, `/get-state`
- [ ] BE: REST `GET /music/state`
- [ ] BE: PlaybackState entity + `@Version` optimistic lock
- [ ] BE: 2 endpoints (POST annotation, GET sessions)
- [ ] BE: 4 WS events (MUSIC_SONG_CHANGED, MUSIC_PLAYBACK_STATE_CHANGED, ANNOTATION_CREATED, ...)
- [ ] FE: Music player (play/pause/seek/volume + Song picker)
- [ ] FE: Annotation popup
- [ ] FE: SC-07 Session history view
- [ ] i18n: ~15 keys
- [ ] E2E test: 2 users cùng tap play (race condition)

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.5-2.6
- `liveroom-ws-protocol.md` §4.5-4.6
- `liveroom-concurrency.md` §3 (optimistic lock)
- `liveroom-jobs.md` §6-8

**Definition of Done**:
- 2 user cùng tap play → 1 success, 1 conflict 409
- Owner absent → music pause + non-owner control disabled
- Annotation tạo được, hiển thị ở marker đúng vị trí
- Session history view load cycle cũ

**Chi tiết**: xem `06-phase-4-music-annotation.md`

---

## 🛡️ Phase 5: Admin + Polish (5-7 ngày)

**Mục tiêu**: Kick/remote mute, audit log, GDPR, monitoring.

**Deliverable**:
- [ ] BE: 2 endpoints (POST kick, POST mute-mic)
- [ ] BE: 3 audit tables + audit logging service
- [ ] BE: 1 endpoint (PATCH privacy/email)
- [ ] BE: 1 job (IdempotencyCleanup)
- [ ] BE: OpenTelemetry tracing + Prometheus metrics
- [ ] FE: Admin context menu (kick/mute) cho owner
- [ ] FE: SC-13 Kicked landing
- [ ] FE: Email display toggle (GDPR)
- [ ] i18n: ~15 keys
- [ ] Final E2E test (full flow + all edge cases)

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.7-2.9
- `liveroom-audit-logging.md` §2-6
- `liveroom-jobs.md` §9

**Definition of Done**:
- Kick → SC-13 + 5min cooldown
- Remote mute → mic disabled + WS event
- Audit log có record cho admin actions
- Email masking toggle hoạt động
- Prometheus metrics + trace ID hoạt động

**Chi tiết**: xem `07-phase-5-admin-polish.md`

---

## 📊 Dependency giữa các Phase

```
Phase 0 ─────Phase 1 ─────Phase 2 ─────Phase 3 ─────Phase 4 ─────Phase 5
  │              │            │            │            │            │
  │              │            │            │            │            │
  ▼              ▼            ▼            ▼            ▼            ▼
Schema       Join flow    Leave/End    WebRTC      Music       Admin
                              Reopen    + Chat       + Annot      + Audit
                                         
                         (Phase 2 cần Phase 1)
                         (Phase 3 cần Phase 2 — có state machine)
                         (Phase 4 cần Phase 3 — có WS ổn định)
                         (Phase 5 cần Phase 4 — có đầy đủ feature)
```

**KHÔNG được skip phase**. Phase 0 nền tảng, Phase 1 là MVP, Phase 2-5 mở rộng.

---

## 🎯 Mapping giữa Phase và BR

| Phase | BR Rules liên quan | Use Cases (UC) | Edge Cases (EC) |
|---|---|---|---|
| Phase 0 | R-CREATE-01..06, R-ROLE-01..12 | — | — |
| Phase 1 | R-JOIN-01..10, R-APPROVE-01..06 | UC-01, UC-02 | EC-01, EC-02, EC-22, EC-24 |
| Phase 2 | R-LEAVE-01..09, R-END-01..12, R-REOPEN-01..08, R-CAPACITY-01..02, R-REJECT-01..04 | UC-03..UC-08 | EC-03..EC-12, EC-23..EC-29 |
| Phase 3 | R-MEDIA-01..12, R-CHAT-01..08, R-WS-SCOPE-01..02 | UC-09, UC-10 | EC-13..EC-15 |
| Phase 4 | R-MUSIC-01..11, R-ANNOT-01..08 | UC-11, UC-12, UC-13 | EC-16..EC-21 |
| Phase 5 | R-ADMIN-01..05, R-KICK-01..04, R-DISPLAY-01..06, R-AUDIT-01..05 | — | EC-30..EC-32 |

---

## 📈 Timeline đề xuất

| Tuần | Phase | Ghi chú |
|---|---|---|
| Tuần 1 | Phase 0 + bắt đầu Phase 1 | Foundation + setup join flow |
| Tuần 2 | Phase 1 hoàn thành | MVP demo được |
| Tuần 3-4 | Phase 2 | Lifecycle + edge cases |
| Tuần 5-6 | Phase 3 | WebRTC + chat |
| Tuần 7-8 | Phase 4 | Music + annotation |
| Tuần 9 | Phase 5 | Admin + polish |

**Tổng**: 9 tuần (2 tháng)

---

## 🚦 Definition of Done cho TOÀN BỘ project

- [ ] Tất cả 5 phase đã DONE
- [ ] 25+ REST endpoints hoạt động
- [ ] 30+ WS events broadcast đúng
- [ ] 13 screens render đúng
- [ ] 200+ i18n keys EN + VI
- [ ] 9 background jobs chạy đúng
- [ ] 3 audit tables ghi log
- [ ] Tất cả edge cases (EC-01..EC-32) đã test
- [ ] Pessimistic + optimistic lock race condition test pass
- [ ] Full E2E test pass với 2 browsers
- [ ] No hardcode messages, no code comments, no @Version trên LiveRoom
- [ ] 8 workspace rules (.cursor/rules) tuân thủ 100%

---

## 📋 Công cụ theo dõi

Copy bảng dưới sang Excel/Notion/Google Sheet:

| Phase | Task | Doc tham chiếu | Owner | Status | Ngày bắt đầu | Ngày xong | Ghi chú |
|---|---|---|---|---|---|---|---|
| P0 | Tạo 11 entity | data-model.md | Dev | 🔄 | 2026-07-27 | — | |
| P0 | Flyway migration | data-model.md | Dev | ⏳ | — | — | |
| ... | ... | ... | ... | ... | ... | ... | ... |

**Legend**:
- ⏳ Pending
- 🔄 In progress
- ✅ Done
- ❌ Blocked

Xem chi tiết trong `09-progress-tracker.md`.

---

**Cập nhật**: 2026-07-26
