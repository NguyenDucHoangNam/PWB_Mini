# Live Room — Bốn scheduler chạy nền

> `EmptyRoomScheduler` · `OwnerGraceScheduler` · `ChatRetentionScheduler` · `IdempotencyKeyCleanupScheduler`
> Bối cảnh: [Live Room — Tour](liveroom-00-tour.md)

---

## 1. Bài toán

Ba module thì chỉ Live Room cần công việc chạy nền. Lý do: nó là module duy nhất có **trạng thái hết hạn theo thời gian mà không ai bấm gì cả**.

Một tài khoản IAM bị khoá thì tự hết khoá khi TTL Redis hết — không cần ai dọn. Một bài hát ở `PROCESSING` sẽ được worker đẩy tiếp. Nhưng một phòng mà **tất cả mọi người đã rời** sẽ nằm đó mãi mãi ở trạng thái `ACTIVE`, vì không có request nào tới để phát hiện điều đó.

Bốn scheduler xử lý bốn thứ như vậy, và chúng chia thành hai loại rất khác nhau.

---

## 2. Bốn cái, hai loại

| Scheduler | Chu kỳ | Loại | Hậu quả nếu không chạy |
|---|---|---|---|
| `OwnerGraceScheduler` | **10 giây** | Nghiệp vụ | Phòng không bao giờ đóng khi chủ bỏ đi |
| `EmptyRoomScheduler` | **60 giây** | Nghiệp vụ | Phòng rỗng treo vĩnh viễn |
| `ChatRetentionScheduler` | **4:00 hằng ngày** | Dọn dẹp | Bảng chat phình vô hạn |
| `IdempotencyKeyCleanupScheduler` | **3:30 hằng ngày** | Dọn dẹp | Khoá idempotency tích tụ |

Hai cái đầu **là một phần của nghiệp vụ** — người dùng thấy hậu quả trực tiếp. Hai cái sau chỉ giữ vệ sinh.

Khoảng cách chu kỳ nói lên độ khẩn: 10 giây so với mỗi ngày một lần.

### 2.1. Vì sao 10 giây và 60 giây

`owner-grace-interval-ms: 10000` phải **nhỏ hơn nhiều** so với thời gian chờ (60 giây mặc định). Nếu quét mỗi 60 giây thì một phòng có thời gian chờ 60 giây sẽ đóng ở đâu đó giữa 60 và 120 giây — sai số 100%. Quét mỗi 10 giây thì sai số tối đa còn 17%.

Quy tắc rút ra: **chu kỳ quét phải nhỏ hơn hẳn thời hạn nó theo dõi**, nếu không thời hạn cấu hình trở nên vô nghĩa.

`empty-room-interval-ms: 60000` với thời hạn 5 phút — cùng tỉ lệ, và độ trễ vài chục giây với một phòng rỗng thì không ai để ý.

### 2.2. Hai cái dọn dẹp chạy ban đêm, lệch nhau nửa tiếng

`3:30` và `4:00`. Cả hai đều quét bảng lớn và xoá theo lô; chạy cùng lúc là hai lần tải nặng chồng nhau. Lệch nửa tiếng là cách rẻ nhất để tách chúng ra.

Cả hai đều dùng cron **cấu hình được** — chỉnh sang giờ thấp điểm của từng môi trường mà không phải sửa code.

---

## 3. Hai scheduler nghiệp vụ

Cả hai đều đi qua **cùng một use case**, chỉ khác lý do:

```java
autoEndRoom.execute(roomId, EndedReason.EMPTY_TIMEOUT);
autoEndRoom.execute(room.getId(), EndedReason.OWNER_GRACE_EXPIRED);
```

`AutoEndRoomUseCase` → `RoomTermination.terminate` — **đúng thủ tục kết thúc mà chủ phòng bấm tay cũng dùng** ([liveroom-01 §4](liveroom-01-vong-doi-phong.md)). Không có đường kết thúc riêng cho scheduler.

Giá trị của việc này: mọi thứ phải xảy ra khi phòng đóng — đưa người ra, huỷ yêu cầu đang chờ, dừng nhạc, xoá bình luận, đóng phiên — được viết **một lần**. Thêm một việc mới thì cả ba đường (thủ công, rỗng, chủ vắng) đều được.

`ended_reason` là chỗ duy nhất phân biệt ba đường, và nó chỉ dùng để hiển thị.

### 3.1. Lọc ở đâu: hai cách khác nhau

```java
// EmptyRoomScheduler — lọc trong SQL
Instant threshold = Instant.now().minus(config.getRoom().getEmptyTimeout());
List<UUID> roomIds = liveRoomRepository.findEmptyRoomIds(threshold);
```

```java
// OwnerGraceScheduler — lọc trong Java
List<LiveRoom> absent = liveRoomRepository.findActiveWithAbsentOwner();
absent.stream()
        .filter(room -> room.ownerGraceExpiresAt() != null)
        .filter(room -> now.isAfter(room.ownerGraceExpiresAt()))
        .forEach(…);
```

Không nhất quán, nhưng có lý do: **thời gian chờ khác nhau theo từng phòng** (`owner_grace_seconds` là một cột). Điều kiện SQL sẽ thành `now > owner_left_at + owner_grace_seconds * interval '1 second'` — viết được nhưng không dùng được index, và quy tắc "khi nào thì hết hạn" sẽ bị nhân bản giữa SQL và `LiveRoom.ownerGraceExpiresAt()`.

Cách hiện tại giữ quy tắc ở **một chỗ duy nhất** trong domain model. Cái giá là kéo về mọi phòng có chủ vắng mặt — số lượng nhỏ vì đó là trạng thái tạm.

`EmptyRoomScheduler` thì ngược lại: điều kiện giống nhau cho mọi phòng, lọc trong SQL được và tránh kéo về cả bảng.

### 3.2. Một phòng hỏng không kéo theo phòng khác

```java
roomIds.forEach(roomId -> {
    try {
        autoEndRoom.execute(roomId, EndedReason.EMPTY_TIMEOUT);
    } catch (Exception ex) {
        log.error("Failed to end empty room {}", roomId, ex);
    }
});
```

`try/catch` **bên trong vòng lặp**, ở cả hai scheduler. Không có nó, một phòng gặp lỗi sẽ ném ra khỏi cả lượt quét và mọi phòng phía sau bị bỏ qua — rồi lượt sau lại vấp đúng phòng đó. Một bản ghi hỏng làm treo vĩnh viễn cả cơ chế.

Đây là khuôn mẫu bắt buộc cho mọi vòng lặp xử lý theo lô, và nó đúng ở đây.

Chú ý scheduler **không** có `@Transactional`, còn use case bên trong thì có. Nghĩa là mỗi phòng là một transaction riêng: phòng thứ ba hỏng không rollback hai phòng đầu.

Ngược lại, hai scheduler dọn dẹp **có** `@Transactional` ở mức phương thức — chúng xoá theo lô, và một lô là một đơn vị hợp lý.

---

## 4. Hai scheduler dọn dẹp

**`ChatRetentionScheduler`** — xoá tin nhắn quá `chat.retention` (90 ngày). Hệ quả đã nêu ở [liveroom-04 §6](liveroom-04-chat.md): tin nhắn sống theo thời gian tuyệt đối, nên một phòng hoạt động hơn 90 ngày sẽ mất dần lịch sử cũ nhất của chính nó.

**`IdempotencyKeyCleanupScheduler`** — giải phóng khoá idempotency của yêu cầu tham gia quá `room.idempotency-key-ttl` (24 giờ).

Chú ý tên phương thức: `releaseExpiredKeys`, không phải `deleteExpiredRequests`. Nó **không xoá yêu cầu** — chỉ xoá phần khoá. Bản ghi yêu cầu và lịch sử quyết định vẫn còn; chỉ mất khả năng phát lại cùng khoá đó.

24 giờ vì khoá idempotency chỉ có nghĩa trong một phiên làm việc. Client thử lại sau một ngày là một hành động mới, không phải một lần gửi lại.

---

## 5. Vấn đề lớn nhất: nhiều instance

Không scheduler nào có khoá phân tán. Chạy hai instance là **cả hai cùng quét**.

| Scheduler | Chạy trùng thì sao |
|---|---|
| `OwnerGraceScheduler` | Cả hai gọi `terminate` cho cùng phòng. Cái thứ hai gặp phòng đã `ENDED`; thiệt hại có thể chỉ là một exception được log |
| `EmptyRoomScheduler` | Như trên |
| `ChatRetentionScheduler` | Xoá là idempotent — vô hại, chỉ tốn công |
| `IdempotencyKeyCleanupScheduler` | Như trên |

Nghe có vẻ chịu được, nhưng chưa được kiểm chứng bằng test nào — và với hai scheduler nghiệp vụ, đường thất bại đi qua `RoomTermination`, nơi có nhiều tác dụng phụ (đưa người ra, phát sự kiện). Không rõ chạy trùng có phát sự kiện kết thúc **hai lần** tới client hay không.

Thực tế điều này chưa thành vấn đề vì hệ thống **hiện chỉ chạy được một instance** vì lý do khác — broker STOMP, bộ đếm frame và sổ session đều nằm trong bộ nhớ tiến trình ([13 §10](13-realtime-stomp.md)). Nhưng khi giải quyết những thứ đó thì đây là hạng mục tiếp theo trong danh sách, và cần `ShedLock` hoặc tương đương.

---

## 6. Quyết định & đánh đổi

| Quyết định | Thay vì | Vì sao | Cái giá |
|---|---|---|---|
| Scheduler dùng chung `RoomTermination` | Đường kết thúc riêng | Thêm việc lúc đóng phòng thì cả ba đường đều có | Không tuỳ biến được theo từng lý do |
| Quét 10 giây cho thời gian chờ 60 giây | Bằng nhau | Sai số nhỏ so với thời hạn | 8640 lượt quét mỗi ngày, phần lớn không có việc |
| Lọc grace trong Java | Lọc trong SQL | Quy tắc hết hạn chỉ nằm một chỗ | Kéo về mọi phòng có chủ vắng |
| Lọc phòng rỗng trong SQL | Trong Java | Điều kiện giống nhau mọi phòng, dùng được index | Điều kiện nằm trong repository |
| `try/catch` trong vòng lặp | Để ném ra ngoài | Một bản ghi hỏng không treo cả cơ chế | Lỗi lặp lại mỗi lượt mà không ai chú ý |
| Transaction mỗi phòng | Một transaction cho cả lượt | Phòng sau không rollback phòng trước | Nhiều transaction ngắn |
| Dọn dẹp chạy 3:30 và 4:00 | Cùng lúc | Không chồng hai lượt tải nặng | Phải nhớ khi thêm job thứ ba |
| Cron cấu hình được | Cứng trong code | Mỗi môi trường có giờ thấp điểm riêng | Thêm cấu hình |
| Không có khoá phân tán | ShedLock | Chưa cần khi chỉ một instance | Chặn đường chạy nhiều instance |

---

## 7. Tự kiểm chứng

**Xem `owner grace` hết hạn** — thí nghiệm nhanh nhất:

1. Tạo phòng với thời gian chờ ngắn: `{"roomName":"Test grace","ownerGraceSeconds":15}`
2. Chủ phòng rời: `DELETE /rooms/{id}/participants/me`
3. Theo dõi:

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT room_code, status, owner_left_at, owner_grace_seconds, ended_reason FROM liveroom_rooms ORDER BY created_at DESC LIMIT 3;"
```

Sau khoảng 15–25 giây (15 giây chờ + tối đa 10 giây chu kỳ quét), `status` chuyển `ENDED` với `ended_reason = OWNER_GRACE_EXPIRED`. **Khoảng dao động 10 giây đó chính là chu kỳ scheduler**, quan sát được trực tiếp.

Vào lại trong vòng 15 giây thì `owner_left_at` trở về null và phòng sống.

**Xem ba lý do kết thúc:**

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT ended_reason, count(*) FROM liveroom_rooms WHERE status='ENDED' GROUP BY 1;"
```

**Xem phòng rỗng tự đóng** — cho mọi người rời một phòng rồi chờ 5 phút; `ended_reason = EMPTY_TIMEOUT`.

**Xem khoá idempotency:**

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT idempotency_key IS NOT NULL AS con_khoa, count(*) FROM liveroom_join_requests GROUP BY 1;"
```

Sau khi job 3:30 chạy, những yêu cầu quá 24 giờ chuyển sang `con_khoa = false` **nhưng hàng vẫn còn** — đúng như mục 4.

**Ép scheduler chạy ngay** — đặt chu kỳ rất ngắn khi khởi động:

```
-Dpwb.liveroom.scheduler.empty-room-interval-ms=5000
```

**Xem log** — cả hai job dọn dẹp chỉ log khi thực sự xoá được gì (`Purged {} chat message(s)…`, `Released {} expired … key(s)`). Không có dòng nào nghĩa là không có gì để dọn, không phải job chết.

---

## 8. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| **Không có khoá phân tán** | Nhiều instance là quét trùng; hành vi khi trùng chưa được kiểm chứng — mục 5 |
| Không có chỉ số theo dõi | Không biết một lượt quét mất bao lâu, xử lý bao nhiêu phòng, hỏng mấy cái |
| Lỗi lặp lại âm thầm | `try/catch` ghi log rồi đi tiếp; một phòng hỏng mãi mãi sẽ sinh một dòng error mỗi 10 giây mà không ai cảnh báo |
| Quét kể cả khi không có việc | 8640 lượt mỗi ngày cho `OwnerGraceScheduler`, phần lớn rỗng |
| Không có xoá cứng phòng đã kết thúc | Bảng `liveroom_rooms` chỉ lớn lên |
| Không dọn `liveroom_admin_actions` và `liveroom_ownership_history` | Chỉ chat có chính sách giữ; hai bảng này lớn vô hạn |
| Bình luận theo timeline không có scheduler | Chúng nằm trong bộ nhớ và chỉ xoá khi phòng kết thúc; tiến trình sống lâu với nhiều phòng chưa đóng sẽ tích tụ ([liveroom-05 §7](liveroom-05-nghe-nhac-cung.md)) |
| Chu kỳ và cron không được kiểm tra tính hợp lý | Đặt `owner-grace-interval-ms` lớn hơn thời gian chờ thì thời gian chờ mất ý nghĩa, và không có gì cảnh báo |
