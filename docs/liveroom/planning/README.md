# Live Room — Planning Hub

**Mục đích**: Bộ tài liệu lập kế hoạch để code toàn bộ Live Room module một cách có hệ thống, tránh bỏ sót và lỗi.

**Người dùng**: Senior Dev + AI Agent (Cursor) + QA

---

## 📚 12 tài liệu gốc (bắt buộc đọc)

| # | File | Mục đích |
|---|---|---|
| 1 | `../liveroom-business-requirements.md` | **Source of truth** — requirements chính |
| 2 | `../liveroom-data-model.md` | Database schema, 11 entity, locking |
| 3 | `../liveroom-api-spec.md` | REST endpoints + STOMP destinations |
| 4 | `../liveroom-ws-protocol.md` | WebSocket events catalog |
| 5 | `../liveroom-permission-matrix.md` | Phân quyền theo role + state |
| 6 | `../liveroom-state-machines.md` | State transitions + side effects |
| 7 | `../liveroom-state-ui-mapping.md` | UI ↔ state mapping |
| 8 | `../liveroom-screen-inventory.md` | 13 screens, component tree |
| 9 | `../liveroom-user-flow.md` | User journeys + edge cases |
| 10 | `../liveroom-concurrency.md` | Race condition protection |
| 11 | `../liveroom-jobs.md` | 9 background jobs |
| 12 | `../liveroom-i18n-keys.md` | 200+ i18n keys EN + VI |
| 13 | `../liveroom-audit-logging.md` | 3 audit tables + action types |

---

## 📋 11 tài liệu planning (file trong folder này)

| # | File | Đọc khi nào |
|---|---|---|
| 0 | `00-reading-guide.md` | **ĐỌC ĐẦU TIÊN** — cách đọc 12 docs gốc theo thứ tự |
| 1 | `01-roadmap.md` | Tổng quan 5 phase, dependency, timeline |
| 2 | `02-phase-0-foundation.md` | Chi tiết Phase 0 (skeleton + entity) |
| 3 | `03-phase-1-create-join.md` | Chi tiết Phase 1 (tạo + join phòng) |
| 4 | `04-phase-2-lifecycle.md` | Chi tiết Phase 2 (leave/end/reopen) |
| 5 | `05-phase-3-realtime-media.md` | Chi tiết Phase 3 (WebRTC + chat) |
| 6 | `06-phase-4-music-annotation.md` | Chi tiết Phase 4 (music + annotation) |
| 7 | `07-phase-5-admin-polish.md` | Chi tiết Phase 5 (admin + audit) |
| 8 | `08-self-review-checklist.md` | Checklist tự kiểm tra trước khi commit |
| 9 | `09-progress-tracker.md` | Bảng theo dõi tiến độ (copy sang Excel/Notion) |
| 10 | `10-codebase-templates.md` | Template code base (entity, service, controller, FE hook) |

---

## 🚀 Workflow khi code

### Bước 1: Đọc hướng dẫn đọc tài liệu

→ Mở `00-reading-guide.md` và đọc toàn bộ.

### Bước 2: Đọc roadmap

→ Mở `01-roadmap.md` để hiểu 5 phase + dependency.

### Bước 3: Đọc chi tiết Phase hiện tại

→ Mở `02-phase-0-foundation.md` (hoặc phase đang làm).

### Bước 4: Đọc docs gốc tham chiếu

→ Mỗi task trong phase doc có ghi rõ **"Xem doc nào"**. Đọc doc đó trước khi code.

### Bước 5: Code theo template

→ Mở `10-codebase-templates.md` để copy template entity/service/controller/hook.

### Bước 6: Self-review trước khi commit

→ Mở `08-self-review-checklist.md` và check từng mục.

### Bước 7: Cập nhật tiến độ

→ Sửa `09-progress-tracker.md` (hoặc copy sang Excel/Notion).

---

## 📌 Nguyên tắc vàng

1. **BR là source of truth** — mọi doc khác phải khớp BR. Nếu phát hiện mâu thuẫn → fix doc khác cho khớp BR.

2. **Đọc doc trước khi code** — không code "theo trí nhớ". Mỗi task có ghi rõ doc tham chiếu.

3. **Self-review trước khi commit** — dùng checklist ở `08-self-review-checklist.md`.

4. **Một phase = một PR** — chia PR theo phase để dễ review.

5. **E2E test thủ công** — sau mỗi phase, test bằng 2 browsers theo user-flow.

6. **i18n 2 ngôn ngữ** — mỗi label mới phải có EN + VI.

7. **Không hardcode message** — dùng `MessageSource` (BE) + `useTranslations` (FE).

---

## ⚠️ Đừng quên

- File `.cursor/rules/` có 8 rules bắt buộc: Lombok, no-comments, no-auto-tests, no-auto-commit, i18n, etc.
- File `liveroom-business-requirements.md` luôn đọc trước.
- Backend dùng Spring Boot Multi-module + Maven.
- Frontend dùng Next.js (App Router) + React + TypeScript.

---

## 📞 Khi gặp vấn đề

| Vấn đề | Làm gì |
|---|---|
| Mâu thuẫn giữa 2 docs | Ưu tiên BR, sửa docs khác cho khớp |
| Không tìm thấy thông tin | Grep trong folder `docs/liveroom/` |
| Không biết bắt đầu từ đâu | Đọc `00-reading-guide.md` |
| Không biết task đã xong chưa | Check `09-progress-tracker.md` |
| Code xong nhưng không chắc đúng | Dùng `08-self-review-checklist.md` |
| AI agent code sai | Sửa prompt, đưa phase doc + template code |

---

**Cập nhật lần cuối**: 2026-07-26
