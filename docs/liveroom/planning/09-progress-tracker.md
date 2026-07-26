# 09 - Progress Tracker

**Mục đích**: Bảng theo dõi tiến độ toàn bộ project Live Room.

**Cập nhật**: Mỗi khi 1 task hoàn thành → update status + ngày.

**Cách dùng**: Copy nội dung sang Excel/Google Sheet/Notion để dễ filter/sort.

---

## 🎯 Tổng quan

| Phase | Tổng tasks | Done | % | Ngày bắt đầu | Ngày xong |
|---|---|---|---|---|---|
| Phase 0: Foundation | 10 | 0 | 0% | — | — |
| Phase 1: Create + Join | 13 | 0 | 0% | — | — |
| Phase 2: Lifecycle | 18 | 0 | 0% | — | — |
| Phase 3: Realtime + Media | 14 | 0 | 0% | — | — |
| Phase 4: Music + Annotation | 16 | 0 | 0% | — | — |
| Phase 5: Admin + Polish | 14 | 0 | 0% | — | — |
| **TỔNG** | **85** | **0** | **0%** | — | — |

---

## 📋 Phase 0: Foundation (10 tasks)

| # | Task | Doc tham chiếu | Status | Owner | Bắt đầu | Xong |
|---|---|---|---|---|---|---|
| 0.1 | Maven module skeleton | data-model.md §2 | ⏳ | | | |
| 0.2 | 11 entity classes | data-model.md §3 | ⏳ | | | |
| 0.3 | Flyway migration V1 | data-model.md §3 | ⏳ | | | |
| 0.4 | WebSocketConfig + STOMP | ws-protocol.md §1-2 | ⏳ | | | |
| 0.5 | MessageSource (i18n) | i18n-keys.md | ⏳ | | | |
| 0.6 | JWT Auth filter | permission-matrix.md | ⏳ | | | |
| 0.7 | Health check + Swagger | — | ⏳ | | | |
| 0.8 | FE features folder | screen-inventory.md | ⏳ | | | |
| 0.9 | API gateway route | — | ⏳ | | | |
| 0.10 | Verify build pass | — | ⏳ | | | |

---

## 📋 Phase 1: Create + Join (13 tasks)

| # | Task | Doc tham chiếu | Status | Owner | Bắt đầu | Xong |
|---|---|---|---|---|---|---|
| 1.1 | POST /liverooms | api-spec.md §1.1 | ⏳ | | | |
| 1.2 | GET /liverooms/by-code | api-spec.md §1.2 | ⏳ | | | |
| 1.3 | POST /join-requests | api-spec.md §2.1 | ⏳ | | | |
| 1.4 | POST /approve (pessimistic lock) | api-spec.md §2.2, concurrency.md §2 | ⏳ | | | |
| 1.5 | GET /liverooms/{id}/state | api-spec.md §1.3 | ⏳ | | | |
| 1.6 | WS: 3 events | ws-protocol.md §4.1 | ⏳ | | | |
| 1.7 | FE: SC-01 Create Room | screen-inventory.md SC-01 | ⏳ | | | |
| 1.8 | FE: SC-03 Pre-Join | screen-inventory.md SC-03 | ⏳ | | | |
| 1.9 | FE: SC-04 Waiting Room | screen-inventory.md SC-04 | ⏳ | | | |
| 1.10 | FE: SC-06 In-Room basic | screen-inventory.md SC-06 | ⏳ | | | |
| 1.11 | FE: WS subscription | ws-protocol.md §3 | ⏳ | | | |
| 1.12 | i18n keys (~10) | i18n-keys.md | ⏳ | | | |
| 1.13 | E2E test 2 browsers | user-flow.md UC-01/02 | ⏳ | | | |

---

## 📋 Phase 2: Lifecycle (18 tasks)

| # | Task | Doc tham chiếu | Status | Owner | Bắt đầu | Xong |
|---|---|---|---|---|---|---|
| 2.1 | POST /reject | api-spec.md §2.3 | ⏳ | | | |
| 2.2 | Capacity check atomic | concurrency.md §2.1 | ⏳ | | | |
| 2.3 | RejectCounter UPSERT | concurrency.md §2.4 | ⏳ | | | |
| 2.4 | Lock after 3 rejects | state-machines.md §1 | ⏳ | | | |
| 2.5 | POST /cancel, /rejoin | api-spec.md §2.4-5 | ⏳ | | | |
| 2.6 | POST /leave (debounce + grace) | api-spec.md §2.6, concurrency.md §2.3 | ⏳ | | | |
| 2.7 | GraceExpiryJob | jobs.md §2 | ⏳ | | | |
| 2.8 | EmptyRoomTimeoutJob | jobs.md §3 | ⏳ | | | |
| 2.9 | POST /end + 5s undo | api-spec.md §2.7 | ⏳ | | | |
| 2.10 | POST /reopen | api-spec.md §2.8 | ⏳ | | | |
| 2.11 | Multi-tab conflict | concurrency.md §2.5 | ⏳ | | | |
| 2.12 | WS: 9 events | ws-protocol.md §4.1-2 | ⏳ | | | |
| 2.13 | FE: SC-02 Owner Waiting | screen-inventory.md SC-02 | ⏳ | | | |
| 2.14 | FE: SC-03b Rejected | screen-inventory.md SC-03b | ⏳ | | | |
| 2.15 | FE: Owner leave banner | state-ui-mapping.md | ⏳ | | | |
| 2.16 | FE: End room + undo | state-ui-mapping.md | ⏳ | | | |
| 2.17 | i18n keys (~25) | i18n-keys.md | ⏳ | | | |
| 2.18 | E2E test (15+ edge cases) | user-flow.md §4 | ⏳ | | | |

---

## 📋 Phase 3: Realtime + Media (14 tasks)

| # | Task | Doc tham chiếu | Status | Owner | Bắt đầu | Xong |
|---|---|---|---|---|---|---|
| 3.1 | WebRTC signaling | api-spec.md §2.4, ws-protocol.md §4.4 | ⏳ | | | |
| 3.2 | POST /chat/messages | api-spec.md §2.5 | ⏳ | | | |
| 3.3 | GET /chat/messages | api-spec.md §2.5 | ⏳ | | | |
| 3.4 | WS: 3 chat events | ws-protocol.md §4.2 | ⏳ | | | |
| 3.5 | WS scope enforcement | ws-protocol.md §2 | ⏳ | | | |
| 3.6 | ParticipantReconnectTimeoutJob | jobs.md §4 | ⏳ | | | |
| 3.7 | IdleGhostIndicatorJob | jobs.md §4 | ⏳ | | | |
| 3.8 | FE: WebRTC peer manager | screen-inventory.md SC-06 | ⏳ | | | |
| 3.9 | FE: Media tile | screen-inventory.md SC-06 | ⏳ | | | |
| 3.10 | FE: Chat panel | screen-inventory.md SC-06 | ⏳ | | | |
| 3.11 | FE: WS reconnect | ws-protocol.md §3 | ⏳ | | | |
| 3.12 | FE: Multi-tab detection | state-machines.md §1 | ⏳ | | | |
| 3.13 | i18n keys (~20) | i18n-keys.md | ⏳ | | | |
| 3.14 | E2E test voice chat | user-flow.md §5.7 | ⏳ | | | |

---

## 📋 Phase 4: Music + Annotation (16 tasks)

| # | Task | Doc tham chiếu | Status | Owner | Bắt đầu | Xong |
|---|---|---|---|---|---|---|
| 4.1 | STOMP /music/play | api-spec.md §2.5.2 | ⏳ | | | |
| 4.2 | STOMP /music/pause, /seek, /volume | api-spec.md §2.5 | ⏳ | | | |
| 4.3 | REST GET /music/state | api-spec.md §2.5.1 | ⏳ | | | |
| 4.4 | PlaybackState + @Version | data-model.md §3.8, concurrency.md §3 | ⏳ | | | |
| 4.5 | Sequence number ordering | BR R-MUSIC-10 | ⏳ | | | |
| 4.6 | Owner absent logic | BR R-MUSIC-11 | ⏳ | | | |
| 4.7 | POST /annotations | api-spec.md §2.6.1 | ⏳ | | | |
| 4.8 | GET /sessions, /annotations | api-spec.md §2.6.2-3 | ⏳ | | | |
| 4.9 | WS: 4 music/annotation events | ws-protocol.md §4.5-6 | ⏳ | | | |
| 4.10 | FE: Music player | screen-inventory.md SC-06 | ⏳ | | | |
| 4.11 | FE: Song picker | screen-inventory.md SC-06 | ⏳ | | | |
| 4.12 | FE: Annotation popup | screen-inventory.md SC-06 | ⏳ | | | |
| 4.13 | FE: SC-07 Session history | screen-inventory.md SC-07 | ⏳ | | | |
| 4.14 | FE: Mobile progress bar | screen-inventory.md SC-06 | ⏳ | | | |
| 4.15 | i18n keys (~15) | i18n-keys.md | ⏳ | | | |
| 4.16 | E2E test race condition | user-flow.md §5.11 | ⏳ | | | |

---

## 📋 Phase 5: Admin + Polish (14 tasks)

| # | Task | Doc tham chiếu | Status | Owner | Bắt đầu | Xong |
|---|---|---|---|---|---|---|
| 5.1 | POST /kick | api-spec.md §2.7 | ⏳ | | | |
| 5.2 | POST /mute-mic | api-spec.md §2.8 | ⏳ | | | |
| 5.3 | 3 audit tables | audit-logging.md §2-4 | ⏳ | | | |
| 5.4 | Audit logging service | audit-logging.md §5 | ⏳ | | | |
| 5.5 | Email masking helper | BR R-DISPLAY-06 | ⏳ | | | |
| 5.6 | PATCH /privacy/email | api-spec.md §2.9 | ⏳ | | | |
| 5.7 | IdempotencyCleanupJob | jobs.md §9 | ⏳ | | | |
| 5.8 | OpenTelemetry tracing | audit-logging.md §6 | ⏳ | | | |
| 5.9 | Prometheus metrics | audit-logging.md §6 | ⏳ | | | |
| 5.10 | FE: Admin context menu | screen-inventory.md SC-06 | ⏳ | | | |
| 5.11 | FE: SC-13 Kicked landing | screen-inventory.md SC-13 | ⏳ | | | |
| 5.12 | FE: Email display toggle | screen-inventory.md SC-06 | ⏳ | | | |
| 5.13 | i18n keys (~15) | i18n-keys.md | ⏳ | | | |
| 5.14 | Final E2E test | user-flow.md §5 | ⏳ | | | |

---

## 📊 Status Legend

| Symbol | Meaning |
|---|---|
| ⏳ | Pending (chưa bắt đầu) |
| 🔄 | In progress (đang làm) |
| ✅ | Done (hoàn thành) |
| ❌ | Blocked (bị chặn) |
| ⚠️ | At risk (có vấn đề) |

---

## 📝 Notes

### Format commit message
```
[Phase X] Task X.Y: <action>

- Doc tham chiếu: <doc filename>
- Acceptance: <pass/fail>
```

### Ví dụ
```
[Phase 1] Task 1.4: POST /join-requests/{id}/approve

- Doc tham chiếu: api-spec.md §2.2, concurrency.md §2
- Acceptance: 5/5 pass
- Tests: 2 browsers E2E pass
```

---

## 🎯 Milestone

| Milestone | Ngày | Status |
|---|---|---|
| Phase 0 DONE | — | ⏳ |
| Phase 1 DONE (MVP) | — | ⏳ |
| Phase 2 DONE | — | ⏳ |
| Phase 3 DONE | — | ⏳ |
| Phase 4 DONE | — | ⏳ |
| Phase 5 DONE (full) | — | ⏳ |

---

## ⚠️ Risk log

| # | Risk | Mitigation |
|---|---|---|
| 1 | WebRTC firewall chặn | Dùng TURN server fallback |
| 2 | Concurrency bug ở capacity | Test bằng 2 thread parallel |
| 3 | Music race condition | Test bằng 2 STOMP send cùng lúc |
| 4 | i18n key thiếu | Grep `t\\(` để check keys |
| 5 | WS reconnect loop | Exponential backoff |

---

**Cập nhật lần cuối**: 2026-07-26
