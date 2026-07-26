# 00 - Hướng dẫn đọc tài liệu

**Mục đích**: Giúp dev đọc 12 docs gốc theo thứ tự đúng, tránh đọc lan man và bỏ sót.

**Thời gian đọc ước tính**: 4-6 giờ (cho người mới)

---

## 🎯 Triết lý đọc

**KHÔNG đọc**:
- Đọc hết 1 doc rồi mới đọc doc khác (dễ quên)
- Đọc chi tiết từ đầu (lãng phí thời gian)
- Đọc 1 lần là xong (phải đọc lại khi code từng phase)

**NÊN đọc**:
- Đọc theo từng phase (đọc phần liên quan đến phase đó)
- Đọc overview trước, chi tiết sau
- Đọc doc tham chiếu **ngay trước** khi code task đó

---

## 📅 Lộ trình đọc theo 3 vòng

### Vòng 1: Overview (1 giờ) — ĐỌC TRƯỚC TIÊN

Mục tiêu: hiểu **bức tranh toàn cảnh**, không cần nhớ chi tiết.

| # | Doc | Phần đọc | Thời gian |
|---|---|---|---|
| 1 | `liveroom-business-requirements.md` | Tổng quan, Changelog v1.8, 3 entities chính (LiveRoom, Participant, JoinRequest) | 20 ph |
| 2 | `liveroom-data-model.md` | §1 Tổng quan, §2 Entity Overview, §1.3 Optimistic lock | 10 ph |
| 3 | `liveroom-screen-inventory.md` | §1 Tổng quan, §2 13 Screens | 5 ph |
| 4 | `liveroom-user-flow.md` | §1 Tổng quan, §5 Edge Cases (overview) | 5 ph |
| 5 | `liveroom-state-machines.md` | §1 Overview state transitions | 10 ph |
| 6 | `liveroom-ws-protocol.md` | §1 Overview, §2 Topic structure | 10 ph |

**Output vòng 1**: Hiểu Live Room có gì, các thực thể chính, bao nhiêu screens, bao nhiêu WS events.

---

### Vòng 2: Chi tiết theo Phase (mỗi phase 30-60 ph)

Mỗi khi bắt đầu 1 phase, đọc phase doc trong folder `planning/` TRƯỚC, sau đó đọc chi tiết các docs gốc tham chiếu.

**Phase 0**: Đọc `liveroom-data-model.md` §3 (11 entity) + `liveroom-concurrency.md` §1, §2.1

**Phase 1**: Đọc `liveroom-api-spec.md` §1 + §2.1-2.2 + `liveroom-state-machines.md` §1 (join flow) + `liveroom-permission-matrix.md` §1-2

**Phase 2**: Đọc `liveroom-api-spec.md` §2.3-2.8 + `liveroom-state-machines.md` §2 (leave) + §3 (reopen) + `liveroom-concurrency.md` §2 + `liveroom-jobs.md` §2-4

**Phase 3**: Đọc `liveroom-api-spec.md` §2.4-2.5 + `liveroom-ws-protocol.md` §4.2-4.4 + `liveroom-jobs.md` §4-5

**Phase 4**: Đọc `liveroom-api-spec.md` §2.5.1-2.5.2 + §2.6 + `liveroom-ws-protocol.md` §4.5-4.6 + `liveroom-concurrency.md` §3

**Phase 5**: Đọc `liveroom-api-spec.md` §2.7-2.9 + `liveroom-audit-logging.md` §2-6 + `liveroom-jobs.md` §9

---

### Vòng 3: Cross-reference (10 ph mỗi task)

Trước mỗi task, **grep** trong các docs để tìm thông tin liên quan:

```bash
# Ví dụ: Task code "POST /liverooms/{id}/join-requests"
grep -l "join-requests" docs/liveroom/*.md
grep -l "JoinRequest" docs/liveroom/*.md
grep -l "PENDING" docs/liveroom/*.md
grep -l "idempotency" docs/liveroom/*.md
```

Đọc **tất cả** các sections liên quan (không chỉ 1 doc).

---

## 📋 Bảng tra cứu nhanh: "Tôi cần biết X, đọc doc nào?"

| Cần biết | Đọc doc | Section |
|---|---|---|
| Có bao nhiêu entity, schema | `liveroom-data-model.md` | §2-3 |
| API endpoint nào tồn tại | `liveroom-api-spec.md` | §2 |
| WS event nào tồn tại | `liveroom-ws-protocol.md` | §4 |
| State transition cho participant | `liveroom-state-machines.md` | §1 |
| State transition cho LiveRoom | `liveroom-state-machines.md` | §2-3 |
| User có quyền gì | `liveroom-permission-matrix.md` | §1-2 |
| Screen có component gì | `liveroom-screen-inventory.md` | §5 |
| UI hiển thị gì ở state X | `liveroom-state-ui-mapping.md` | §2 |
| Race condition protection | `liveroom-concurrency.md` | §1-3 |
| Background job nào | `liveroom-jobs.md` | §1-9 |
| i18n key cho label | `liveroom-i18n-keys.md` | §2 |
| Audit log khi nào | `liveroom-audit-logging.md` | §5 |
| Business rule R-XXX | `liveroom-business-requirements.md` | Tìm bằng grep |
| Use case UC-XXX | `liveroom-business-requirements.md` / `liveroom-user-flow.md` | §7 |
| Edge case EC-XXX | `liveroom-business-requirements.md` / `liveroom-user-flow.md` | §4 hoặc §5 |

---

## 🧭 Workflow đọc khi bắt đầu 1 Task

```
1. Mở phase doc (02-07) trong planning/
2. Đọc task description + "Doc tham chiếu" + "Acceptance Criteria"
3. Mở doc tham chiếu được liệt kê
4. Đọc section tương ứng (theo bảng tra cứu)
5. Đọc thêm các docs liên quan (cross-reference)
6. Code theo template trong 10-codebase-templates.md
7. Self-review theo 08-self-review-checklist.md
```

---

## ⚠️ Lưu ý quan trọng

### 1. Đừng đọc hết 1 doc

Ví dụ `liveroom-ws-protocol.md` 1165 dòng — đọc hết sẽ tốn 2 giờ. Thay vào đó:
- Vòng 1: đọc §1, §2 (overview)
- Phase 1: đọc §4.1 (events cần cho join)
- Phase 3: đọc §4.2-4.4 (events cho chat + media)
- Phase 4: đọc §4.5-4.6 (events cho music + annotation)

### 2. Đừng tin tưởng "trí nhớ"

Khi code 1 endpoint, **mở lại doc** dù đã đọc trước đó. Đặc biệt là:
- Error codes
- Validation rules
- Permission matrix
- State machine side effects

### 3. Đừng bỏ qua "v1.8 CLARIFIED" notes

Mỗi note "v1.8 CLARIFIED" hoặc "v1.8 NEW" là rule mới thêm vào. Phải đọc kỹ vì các version cũ có thể sai.

### 4. Khi phát hiện mâu thuẫn

→ Sửa docs khác cho khớp BR (xem `08-self-review-checklist.md` §6).

### 5. Phase doc ở folder `planning/` là "task list"

Mỗi phase doc đã tóm tắt đầy đủ "cần đọc gì" + "cần code gì". Đọc phase doc trước khi mở docs gốc.

---

## 📊 Thống kê đọc

| Doc | Số dòng | Thời gian đọc toàn bộ | Đọc khi nào |
|---|---|---|---|
| `liveroom-business-requirements.md` | 2687 | 2 giờ | Vòng 1 (overview) + khi cần tra BR-XXX |
| `liveroom-data-model.md` | 568 | 30 ph | Vòng 2 Phase 0 (chi tiết) |
| `liveroom-api-spec.md` | ~1100 | 1 giờ | Vòng 2 từng phase (endpoint liên quan) |
| `liveroom-ws-protocol.md` | 1165 | 1 giờ | Vòng 2 từng phase (event liên quan) |
| `liveroom-permission-matrix.md` | 895 | 45 ph | Vòng 2 Phase 1 + khi cần check permission |
| `liveroom-state-machines.md` | 529 | 30 ph | Vòng 1 + Phase 2 |
| `liveroom-state-ui-mapping.md` | 1171 | 1 giờ | Khi code FE |
| `liveroom-screen-inventory.md` | 757 | 45 ph | Vòng 1 + khi code FE cho screen |
| `liveroom-user-flow.md` | ~1100 | 1 giờ | Khi cần hiểu flow |
| `liveroom-concurrency.md` | 777 | 45 ph | Vòng 2 Phase 0 + Phase 2 + Phase 4 |
| `liveroom-jobs.md` | ~700 | 30 ph | Vòng 2 Phase 2 + Phase 3 |
| `liveroom-i18n-keys.md` | 560 | 20 ph | Vòng 2 (tra key cần dùng) |
| `liveroom-audit-logging.md` | 822 | 45 ph | Vòng 2 Phase 5 |

**Tổng**: ~12 giờ đọc (nếu đọc hết). Nhưng với cách đọc theo phase, chỉ cần **4-6 giờ** cho lần đầu.

---

## ✅ Checklist đọc cho mỗi role

### Backend Dev
- [ ] Vòng 1: BR + data-model + state-machines + ws-protocol
- [ ] Phase đang làm: api-spec + concurrency + state-machines
- [ ] Khi cần: jobs + audit-logging

### Frontend Dev
- [ ] Vòng 1: BR + screen-inventory + state-ui-mapping
- [ ] Phase đang làm: screen-inventory + state-ui-mapping + user-flow
- [ ] Khi cần: ws-protocol + api-spec

### QA
- [ ] Vòng 1: BR + user-flow + state-machines
- [ ] Phase đang làm: user-flow (edge cases) + state-ui-mapping
- [ ] Khi cần: api-spec (error codes)

### AI Agent (Cursor)
- [ ] Đọc `README.md` của folder `planning/` TRƯỚC
- [ ] Đọc phase doc hiện tại TRƯỚC khi code
- [ ] Đọc doc tham chiếu được liệt kê trong task TRƯỚC khi code
- [ ] Đọc `08-self-review-checklist.md` TRƯỚC khi commit
- [ ] Đọc `10-codebase-templates.md` để copy template

---

**Cập nhật**: 2026-07-26
