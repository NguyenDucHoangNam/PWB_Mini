# Live Room — Vòng đời phòng

> `POST /rooms` · `GET /rooms` · `GET /rooms/search` · `GET /rooms/{id}` · `GET /rooms/by-code/{code}` · `POST /rooms/{id}/end` · `undo-end` · `reopen`
> Bối cảnh: [Live Room — Tour](liveroom-00-tour.md)

---

## 1. Bài toán

Một phòng không phải bản ghi tạo ra rồi để đó. Nó có thể kết thúc, được cứu lại trong 5 giây, mở lại sau nhiều ngày, tự đóng khi không còn ai. Mỗi lần như vậy phải trả lời được: **cái gì đi theo phòng, cái gì chết theo buổi họp?**

Đó là lý do có khái niệm **session cycle**.

---

## 2. Tạo phòng

```java
if (!command.actor().isPro()) {
    throw new LiveroomBusinessException(LiveroomErrorCode.PRO_REQUIRED);
}
```

Chỉ **PRO** mới tạo được phòng — cùng ranh giới trả phí như tải nhạc lên ([audio-00 §4.5](audio-00-tour.md)). Nhưng ở đây nó là một phép kiểm nghiệp vụ trong use case, không phải `@PreAuthorize` như bên Audio. Hai cách cài cùng một quy tắc trong cùng một codebase.

Sáu bước, tất cả trong một transaction:

```java
1 · kiểm PRO
2 · kiểm tên không trùng — trong phạm vi một chủ phòng (existsByOwnerIdAndNormalizedName)
3 · cấp mã phòng
4 · lưu phòng
5 · mở phiên đầu tiên (sessionCycleStarter.open)
6 · xếp chỗ cho chính chủ phòng (admissions.admit … OWNER)
7 · ghi lịch sử sở hữu (INITIAL_CREATE)
```

Bước 6 đáng chú ý: **chủ phòng được ngồi vào phòng ngay lúc tạo**, đi qua đúng `Admissions.admit` như mọi người khác. Không có đường riêng cho chủ phòng.

Tên phòng được lưu hai lần: `room_name` (nguyên bản) và `normalized_name` (dùng để so trùng). Trùng tên chỉ bị chặn **trong phạm vi một chủ phòng** — hai người khác nhau đặt cùng tên là bình thường.

### 2.1. Mã phòng: thử tối đa 5 lần

```java
for (int attempt = 1; attempt <= attempts; attempt++) {
    RoomCode candidate = roomCodeGenerator.generate();
    if (!liveRoomRepository.existsByRoomCode(candidate.value())) { … }
}
```

Sinh ngẫu nhiên rồi kiểm trùng, tối đa **5 lần** (`code-generation-attempts`). Hết 5 lần thì ném lỗi.

Cách này đúng khi không gian mã còn rộng. Khi số phòng tiến gần không gian mã, xác suất 5 lần đều trùng tăng lên và **việc tạo phòng bắt đầu hỏng ngẫu nhiên** — một dạng suy giảm khó chẩn đoán vì nó phụ thuộc vào may rủi. Chưa có cảnh báo nào theo dõi tỉ lệ trùng.

Bộ sinh là `SecureRandomRoomCodeGeneratorAdapter` — dùng nguồn ngẫu nhiên an toàn, không phải `Random` thường. Đúng, vì mã phòng là **thứ duy nhất bảo vệ phòng** với người không được mời.

### 2.2. Chống dò mã phòng

`GET /rooms/by-code/{code}` là endpoint duy nhất cho phép đoán. Nó được bảo vệ bằng một throttle riêng trên Redis: **20 lần trong 15 phút mỗi IP** (`RoomCodeLookupThrottleAdapter`).

Vì sao không dùng `HttpRateLimitFilter` chung: filter đó đếm **mọi** endpoint vào cùng một khoá cho mỗi IP (`pwb:ratelimit:global:{ip}`). Đặt một cửa sổ 15 phút ở đó sẽ kéo dài cửa sổ cho toàn bộ API. Throttle này phải là của riêng module.

Redis chết thì throttle **fail-open** (chỉ log warn) — ngược với IAM, nơi rate limit đăng nhập fail-closed ([02 §4.2](02-lat-cat-doc-dang-nhap.md)). Cân nhắc khác nhau: chặn đăng nhập khi Redis chết là an toàn; chặn tra mã phòng là làm hỏng sản phẩm cho một rủi ro nhỏ hơn nhiều.

### 2.3. Thời gian chờ chủ phòng

`ownerGraceSeconds` đặt được lúc tạo, có kiểm khoảng (`MIN_GRACE_SECONDS`…`MAX_GRACE_SECONDS`), mặc định `DEFAULT_GRACE_SECONDS`. Ý nghĩa ở [liveroom-03 §3](liveroom-03-nguoi-tham-gia.md).

---

## 3. Phiên (session cycle)

```
liveroom_session_cycles: room_id · cycle_number · started_at · ended_at · ended_reason
```

Phòng giữ `current_cycle_id`. Mọi truy vấn về "ai đang trong phòng" và "tin nhắn của buổi này" đều đi qua `cycle_id`, không qua `room_id`.

`RoomSessions.requireActiveRoom` là cửa kiểm chuẩn của module:

```java
if (!room.isActive() || room.getCurrentCycleId() == null) {
    throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_ENDED);
}
```

Hai điều kiện, không phải một. Phòng "đang hoạt động" mà không có phiên là trạng thái không hợp lệ, và kiểm cả hai là cách phòng thủ trước một bug ở chỗ khác.

---

## 4. Kết thúc phòng — và hai ngoại lệ trái ngược nhau

```java
public LiveRoom terminate(LiveRoom room, EndedReason reason, Instant at) {
    closeOccupants(cycleId, at);
    expirePendingRequests(room.getId(), at);
    playbacks.freezeOnRoomEnd(room, at);      // ← nhạc TẠM DỪNG
    trackCommentStore.clearCycle(cycleId);    // ← bình luận XOÁ SẠCH
    room.end(reason, at);
    sessionCycleStarter.close(room, reason, at);
    return liveRoomRepository.save(room);
}
```

Hai dòng ở giữa xử lý hai thứ theo hai cách ngược nhau, và cả hai đều có lý do:

**Nhạc chỉ tạm dừng, không xoá.** Yêu cầu ban đầu nói là xoá, nhưng nếu xoá thì undo-end (mục 5) sẽ khôi phục lại được mọi thứ **trừ nhạc** — biến nó thành thứ duy nhất không quay lại. Đường dọn dẹp thật nằm ở lúc mở lại phòng.

**Bình luận theo timeline thì bay hết.** Chúng nằm trong bộ nhớ tiến trình (`InMemoryTrackCommentStore`), không có bảng nào — nên không có gì để khôi phục. Undo-end **không** đem chúng về. Chi tiết ở [liveroom-05 §7](liveroom-05-nghe-nhac-cung.md).

Ba đường dẫn tới `terminate`: chủ phòng bấm kết thúc, phòng rỗng quá lâu, hoặc chủ phòng vắng quá thời gian chờ. Hai đường sau do scheduler ([liveroom-07](liveroom-07-scheduler.md)), và `ended_reason` ghi lại đường nào.

---

## 5. Undo: cửa sổ 5 giây

```java
if (!room.canUndoEnd(now, config.getRoom().getUndoEndWindow())) {
    throw new LiveroomBusinessException(LiveroomErrorCode.UNDO_WINDOW_EXPIRED);
}
Instant endedAt = room.getEndedAt();
UUID cycleId = room.getCurrentCycleId();

room.undoEnd(now, config.getRoom().getUndoEndWindow());
sessionCycleStarter.reviveCurrent(room);
room.restoreOccupancy(restoreOccupants(cycleId, endedAt));
```

Undo là **hồi sinh đúng phiên cũ**, không mở phiên mới. `cycle_number` không tăng, tin nhắn chat còn nguyên, người trong phòng được xếp lại chỗ.

Cách nhận diện ai cần khôi phục rất gọn:

```java
List<Participant> closed = participantRepository.findClosedWithRoomAt(cycleId, endedAt);
```

Lọc theo **đúng dấu thời gian kết thúc phòng**. Ai rời phòng tự nguyện trước đó có `left_at` khác nên không bị kéo về; chỉ những người bị `closeOccupants` đóng cùng một lúc mới được khôi phục. Một mốc thời gian làm việc của một cột "lý do rời".

Vì sao 5 giây: nó chỉ nhằm chữa cú bấm nhầm. Dài hơn thì phòng ở trạng thái nửa sống nửa chết lâu hơn, và người dùng đã thấy "phòng đã kết thúc" có thể bỗng dưng bị kéo lại.

Undo **chỉ áp dụng cho phòng do chủ bấm kết thúc**, vì đó là đường duy nhất có người ngồi trước màn hình để bấm undo.

---

## 6. Mở lại phòng

`POST /rooms/{id}/reopen` mở **một phiên mới**: `cycle_number` tăng, `reopened_count` tăng, `last_reopened_at` cập nhật, `previous_ended_at` giữ mốc lần trước.

Bảng so sánh với undo:

| | Undo (5 giây) | Reopen (bất cứ lúc nào) |
|---|---|---|
| Phiên | hồi sinh phiên cũ | **phiên mới** |
| Người trong phòng | khôi phục | không ai |
| Tin nhắn chat | còn (cùng phiên) | không thấy (phiên khác) |
| Nhạc | vẫn ở chỗ đã dừng | dọn sạch |
| Bình luận timeline | **mất** | mất |
| `room_members` | giữ | **giữ** |

Dòng cuối là điều đáng nhớ: người đã được duyệt ở buổi trước **vào thẳng buổi sau**, không phải xin lại. Vì `was_approved` nằm trong `room_members`, gắn với phòng chứ không gắn với phiên.

---

## 7. Xem và tìm phòng

`GET /rooms/{id}` **chỉ chủ phòng xem được**. Người trong phòng không gọi được endpoint này — họ lấy thông tin phòng từ sự kiện realtime. Đây là một hạn chế còn để đó chứ không phải thiết kế: mở rộng cho người trong phòng là việc đáng làm khi có client cần.

Chú ý cách trả lời khi không phải chủ phòng:

```java
if (!room.isOwnedBy(actorId)) {
    throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND);
}
```

Ném **`ROOM_NOT_FOUND`**, không phải `FORBIDDEN`. Cùng nguyên tắc "không rò rỉ sự tồn tại" như IAM ([iam-00 §4.1](iam-00-tour.md)): người ngoài không phân biệt được "phòng này không có" với "phòng này có nhưng không phải của bạn". Khuôn mẫu này lặp ở `approve`, `reject`, `end`, `undo-end`, `kick`.

`GET /rooms/search` chạy trên Elasticsearch qua `RoomSearchPort`, cùng khuôn `Optional.empty()` → ngã về Postgres như [iam-06](iam-06-tim-kiem-nguoi-dung.md).

---

## 8. Quyết định & đánh đổi

| Quyết định | Thay vì | Vì sao | Cái giá |
|---|---|---|---|
| Tách phiên khỏi phòng | Mọi thứ gắn vào phòng | Mở lại không kéo theo người và tin nhắn cũ | Mọi truy vấn phải nhớ dùng `cycleId` |
| `was_approved` gắn với phòng | Gắn với phiên | Buổi sau không phải xin duyệt lại | Duyệt một lần là vào được mãi mãi |
| Chủ phòng ngồi qua `Admissions.admit` | Đường riêng cho chủ | Một chỗ duy nhất xếp chỗ | — |
| Kiểm PRO trong use case | `@PreAuthorize` như Audio | — | Hai cách cài cùng một quy tắc |
| Sinh mã rồi thử lại 5 lần | Sinh tuần tự / mã dài hơn | Đơn giản, mã ngắn dễ đọc qua điện thoại | Tỉ lệ trùng tăng thì tạo phòng hỏng ngẫu nhiên |
| Throttle mã phòng riêng, trên Redis | Dùng `HttpRateLimitFilter` | Cửa sổ 15 phút không lây sang toàn bộ API | Thêm một adapter |
| Throttle mã phòng fail-open | Fail-closed như IAM | Redis chết không làm hỏng cả sản phẩm | Redis chết là mất lớp chống dò |
| Nhạc **tạm dừng** khi kết thúc | Xoá theo yêu cầu | Undo khôi phục được mọi thứ, không chừa nhạc | Trái với tài liệu yêu cầu |
| Bình luận timeline bay hết | Lưu database | MVP không cần bảng mới | Undo không đem chúng về |
| Undo = hồi sinh phiên cũ | Mở phiên mới | Tin nhắn và người còn nguyên | Logic khôi phục dựa trên khớp `endedAt` |
| Cửa sổ undo 5 giây | Dài hơn | Chỉ để chữa bấm nhầm | Đổi ý sau 6 giây là phải reopen |
| `ROOM_NOT_FOUND` cho người không sở hữu | `FORBIDDEN` | Không xác nhận phòng có tồn tại | Chủ phòng gõ nhầm id cũng thấy cùng lỗi |
| `GET /rooms/{id}` chỉ chủ phòng | Mở cho người trong phòng | Ít bề mặt hơn | Client trong phòng phải dựng trạng thái từ sự kiện |

---

## 9. Tự kiểm chứng

```bash
T=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"pro1@gmail.com","password":"@NamHoang511"}' | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
```

**Tạo phòng và xem mã:**

```bash
curl -s -X POST http://localhost:8080/api/v1/liveroom/rooms -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"roomName":"Phong test vong doi"}'
```

**Thử tạo trùng tên** — gọi lại y hệt, nhận `ROOM_NAME_DUPLICATE`.

**Thử bằng tài khoản thường** — dùng token của `user1@gmail.com`, nhận `PRO_REQUIRED`.

**Xem phiên tăng lên khi mở lại:**

```bash
curl -s -X POST "http://localhost:8080/api/v1/liveroom/rooms/<roomId>/end" -H "Authorization: Bearer $T"
```

```bash
curl -s -X POST "http://localhost:8080/api/v1/liveroom/rooms/<roomId>/reopen" -H "Authorization: Bearer $T"
```

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT cycle_number, started_at, ended_at, ended_reason FROM liveroom_session_cycles WHERE room_id='<roomId>' ORDER BY cycle_number;"
```

**Thử undo quá 5 giây** — kết thúc phòng, đợi 6 giây rồi gọi `undo-end`. Nhận `UNDO_WINDOW_EXPIRED`. Làm lại nhanh trong 5 giây thì thành công và `cycle_number` **không tăng**.

**Xem throttle mã phòng** — gọi `GET /rooms/by-code/XXXXXX` với mã bịa hơn 20 lần trong 15 phút.

**Xem khoá throttle trong Redis:**

```bash
docker exec pwb-redis redis-cli -a "$(grep -E '^REDIS_PASSWORD=' .env | cut -d= -f2-)" --no-auth-warning --scan --pattern '*liveroom*'
```

---

## 10. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| Tạo phòng hỏng ngẫu nhiên khi mã gần cạn | 5 lần thử, không có cảnh báo tỉ lệ trùng — mục 2.1 |
| Duyệt một lần là vào được mãi mãi | `was_approved` không bao giờ hết hạn và không có API thu hồi |
| `GET /rooms/{id}` chỉ chủ phòng | Mục 7 |
| Undo chỉ dành cho kết thúc thủ công | Phòng bị scheduler đóng thì chỉ còn cách reopen |
| Bình luận timeline không sống sót qua undo | Mục 4 |
| Không giới hạn số phòng mỗi người | Một PRO tạo bao nhiêu phòng cũng được |
| Không xoá được phòng | Chỉ kết thúc; bản ghi và mã phòng giữ vĩnh viễn |
| `reopened_count` không có trần | Một phòng mở lại vô hạn lần, tích luỹ vô hạn phiên |
