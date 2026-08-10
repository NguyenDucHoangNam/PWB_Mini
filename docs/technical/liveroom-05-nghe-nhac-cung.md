# Live Room — Nghe nhạc cùng & bình luận theo timeline

> STOMP `/app/liveroom/{id}/music/{play,pause,seek,volume,get-state}` · `/comments/{add,get}` → `/topic/liveroom/{id}/music`
> REST `GET /rooms/{id}/music/audio-url`
> Bối cảnh: [Live Room — Tour](liveroom-00-tour.md)

---

## 1. Bài toán

Bảy người trong bảy thành phố phải nghe **cùng một giây** của cùng một bài hát. Ai đó tua thì cả bảy người nhảy theo. Ai đó bình luận ở giây 1:23 thì bình luận ấy phải hiện ra đúng lúc bài hát chạy tới đó.

Ba điều làm bài toán này khó hơn vẻ ngoài:

1. **Vị trí phát là một giá trị thay đổi liên tục.** Lưu vào database kiểu gì khi mỗi giây nó lại khác?
2. **Đồng hồ mỗi máy lệch nhau.** "Đang ở giây 83" nghĩa gì khi máy bạn chạy nhanh hơn máy tôi 40 giây?
3. **Hai người bấm cùng lúc.** Ai thắng, và người thua có làm hỏng trạng thái không?

---

## 2. Anchor — vị trí phát không được lưu như một con số chạy

Đây là ý tưởng trung tâm của cả tính năng.

```java
public double positionAt(Instant now) {
    double position = positionSeconds;
    if (status == PlaybackStatus.PLAYING && startedAt != null && now.isAfter(startedAt)) {
        position += Duration.between(startedAt, now).toMillis() / 1000d;
    }
    return songDurationSeconds == null ? position : Math.min(position, songDurationSeconds);
}
```

Database **không** lưu "đang ở giây thứ mấy". Nó lưu hai thứ tĩnh:

| Cột | Nghĩa |
|---|---|
| `position_seconds` | Bài hát đang ở giây thứ mấy **tại thời điểm `started_at`** |
| `started_at` | Thời điểm bắt đầu chạy từ mốc đó (null khi đang tạm dừng) |

Vị trí thật = mốc + thời gian đã trôi, tính khi cần.

Cách khác — lưu giá trị đang chạy — cần **một tiến trình ghi database mỗi giây cho mỗi phòng**, và vẫn sai giữa hai lần ghi. Anchor không cần ai tick, và luôn chính xác tới mili giây.

Ba thao tác cập nhật anchor:

```java
pause  → positionSeconds = positionAt(now);  startedAt = null
resume → startedAt = now                     (positionSeconds giữ nguyên)
seek   → positionSeconds = vị trí mới;       startedAt = đang phát ? now : null
```

Tạm dừng là **kết tinh** vị trí đang chạy thành mốc tĩnh. Phát tiếp là đặt lại mốc thời gian.

`Math.min(position, songDurationSeconds)` chặn vị trí vượt quá độ dài bài — nếu không, một phòng để chạy qua đêm sẽ báo đang ở giây thứ 30000 của một bài 3 phút.

---

## 3. Đồng hồ: mỗi sự kiện là một lần đồng bộ

Anchor chỉ đúng nếu client và server **cùng hiểu "bây giờ" là lúc nào**. Máy người dùng lệch vài phút là chuyện thường.

Mọi `RoomEvent` mang `timestamp` của server, và client dùng nó chỉnh offset ([13 §9](13-realtime-stomp.md)):

```ts
offsetMs = serverMs - Date.now();
```

Không cần endpoint đồng bộ giờ riêng — trong một phòng đang hoạt động, sự kiện đến liên tục. Đánh đổi: đây là ước lượng thô, không trừ độ trễ đường truyền như NTP, nên lệch đúng bằng một chiều truyền — vài chục mili giây, không đáng kể với đơn vị giây của bài toán.

---

## 4. Ai được điều khiển

```java
public boolean canStartAudio(LiveRoom room, UUID actorId) {
    return !room.isOwnerAbsent() || room.isOwnedBy(actorId);
}
```

Quy tắc: **bất kỳ ai trong phòng cũng điều khiển được nhạc, trừ khi chủ phòng đang vắng mặt.** Chủ vắng thì chỉ chủ mới bật được audio.

Điều này khớp với việc nhạc **tự động tạm dừng** khi chủ rời phòng ([liveroom-03 §3](liveroom-03-nguoi-tham-gia.md)): không có nó, một phòng mà chủ đã rớt mạng vẫn có người bật nhạc và phòng cứ chạy tiếp mà không ai chịu trách nhiệm.

Chú ý phép kiểm chỉ áp cho `resume` và `selectSong`. `pause`, `seek`, `volume` thì ai trong phòng cũng làm được kể cả khi chủ vắng — dừng lại luôn được phép.

**Chỉ chọn được bài của chính mình:**

```java
PlayableSong song = songCatalog.findById(command.songId())
        .filter(candidate -> candidate.ownerId().equals(command.actorId()))
        .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.MUSIC_NOT_OWN_SONG));
if (!song.ready()) throw … MUSIC_NOT_READY;
```

Không mượn nhạc của người khác trong phòng được. `song.ready()` chặn bài đang xử lý dở ([audio-05 §1](audio-05-phat-nhac-va-tim-kiem.md)).

`SongCatalogPort` là một trong hai chỗ duy nhất Live Room chạm vào module Audio ([01 §6](01-architecture-overview.md)).

---

## 5. Khoá lạc quan — và vì sao lỗi phải bắt ở tầng ngoài

Bảng `liveroom_playback_states` có cột `version`. Hai người bấm tua cùng lúc thì người thứ hai commit thất bại với `OptimisticLockingFailureException` → `LR_075 MUSIC_STATE_CONFLICT`.

Tương phản đáng chú ý với sức chứa phòng, dùng **khoá bi quan** (`findByIdForUpdate`):

| | Sức chứa phòng | Trạng thái phát nhạc |
|---|---|---|
| Khoá | Bi quan | **Lạc quan** |
| Vì sao | Vượt sức chứa là sai không sửa được | Xung đột hiếm, và thử lại thì vô hại |
| Người thua | Chờ | Nhận lỗi, bấm lại |

Hai bài toán đồng thời, hai lời giải, trong cùng một module.

**Điểm kỹ thuật quan trọng:** `OptimisticLockingFailureException` nổi lên lúc **flush/commit**, tức là *sau khi* thân use case đã chạy xong. `try/catch` bên trong use case **không bắt được**. Nó phải được xử lý ở `LiveroomStompExceptionHandler` ([13 §7](13-realtime-stomp.md)) — nơi duy nhất đứng ngoài transaction.

---

## 6. Một sự kiện chở cả trạng thái

Mọi thay đổi phát ra cùng một hình dạng qua `Playbacks.announce`, trên kênh `/topic/liveroom/{id}/music`. Loại sự kiện chỉ nói với client **một** điều:

| Loại | Client làm gì |
|---|---|
| `MUSIC_SONG_CHANGED` | Nạp lại nguồn audio |
| `MUSIC_PLAYBACK_STATE_CHANGED` | Chỉ chỉnh lại vị trí/trạng thái |

Payload thì giống nhau — **toàn bộ trạng thái**, không phải phần thay đổi. Client không cần tự dựng trạng thái từ chuỗi sự kiện; một frame là đủ để đồng bộ hoàn toàn. Frame bị mất cũng tự chữa ở frame kế tiếp.

`sequence_number` tăng mỗi lần đổi. Client bỏ qua frame có số nhỏ hơn số nó đã thấy — đây là thứ khiến sự kiện đến trái thứ tự không kéo trạng thái lùi lại.

Nhờ có sequence number mà `get-state` được phép trả lời **trên kênh chung của phòng** thay vì gửi riêng: những người khác nhận một frame trùng lặp, và quy tắc bỏ qua khiến nó vô hại.

`sendCurrentTo(userId, …)` là ngoại lệ — gửi riêng cho một người **vừa vào phòng**, và phải gọi ở **cả hai cửa vào** ([liveroom-02 §2](liveroom-02-vao-phong.md)). Quên một cửa là người vào bằng cửa đó ngồi trong phòng mà không nghe thấy gì.

---

## 7. Bình luận theo timeline — MVP không lưu trữ

Bình luận ghim vào giây thứ mấy của bài hát, kiểu SoundCloud.

```java
public interface TrackCommentStore {
    List<TrackComment> findBySong(UUID cycleId, UUID songId);
    void clearCycle(UUID cycleId);
}
```

Cài đặt là `InMemoryTrackCommentStore` — **`ConcurrentHashMap` trong bộ nhớ tiến trình, không có bảng nào**. Khoá `(cycleId, songId)`. Giới hạn: **200 ký tự**, **200 bình luận mỗi bài**.

Bốn quyết định trong một tính năng nhỏ:

**Không lưu trữ, có chủ đích.** Đây là MVP để thử phản ứng người dùng trước khi bỏ công thiết kế bảng, index và chính sách dọn dẹp. Khởi động lại là mất; undo-end **không** đem về ([liveroom-01 §4](liveroom-01-vong-doi-phong.md)).

**Dùng lại kênh `music`** thay vì mở kênh mới. Bình luận vốn gắn với bài đang phát, và mọi client quan tâm tới bình luận đều đã đăng ký kênh này.

**Client tự hỏi, server không tự đẩy.** Có `comments/get`, không có bước gửi snapshot khi ai đó vào phòng. Đây chính là cách tính năng này **né được cái bẫy hai cửa** ở [liveroom-02 §2](liveroom-02-vao-phong.md): không có gì phải móc vào lúc vào phòng thì không có gì để quên.

**Xoá theo phiên** — `clearCycle` được gọi trong `RoomTermination.terminate`, nên bộ nhớ không phình theo số phòng đã kết thúc.

---

## 8. URL phát nhạc

`GET /rooms/{id}/music/audio-url` là **REST, không phải STOMP** — nó cấp một URL ký sẵn S3, việc thuộc về đường đồng bộ.

Thời hạn **1 giờ** (`music.audio-url-ttl`), dài hơn hẳn 15 phút của ảnh đại diện ([iam-04 §2](iam-04-profile-va-avatar.md)): một buổi nghe nhạc kéo dài hơn một lượt xem trang rất nhiều, và URL hết hạn giữa buổi là nhạc đứt.

Con đường đầy đủ: `GetRoomAudioUrlUseCase` → `SongCatalogPort` → module Audio → `StoragePort` → S3. File nhạc **không đi qua backend**, đúng như [audio-01 §1](audio-01-tai-len-bai-hat.md).

---

## 9. Quyết định & đánh đổi

| Quyết định | Thay vì | Vì sao | Cái giá |
|---|---|---|---|
| Lưu vị trí dạng **anchor** | Lưu giá trị đang chạy | Không cần tiến trình tick mỗi giây; luôn chính xác | Đọc trạng thái phải tính, không đọc thẳng được |
| Đồng bộ đồng hồ từ mọi sự kiện | Endpoint đồng bộ giờ riêng | Miễn phí, và cập nhật liên tục | Ước lượng thô, không trừ độ trễ mạng |
| Ai trong phòng cũng điều khiển được | Chỉ chủ phòng | Nghe cùng nhau là hoạt động chung | Cần khoá lạc quan để xử lý bấm cùng lúc |
| Chủ vắng thì chỉ chủ bật được | Ai cũng bật được | Phòng không tự chạy khi không ai chịu trách nhiệm | Chủ rớt mạng là cả phòng phải chờ |
| Chỉ phát được nhạc của mình | Cho phát nhạc bất kỳ | Nhạc chưa phát hành là riêng tư | Không nghe chung nhạc của khách được |
| Khoá lạc quan | Bi quan như sức chứa | Xung đột hiếm, thử lại vô hại | Người thua nhận lỗi thay vì được xếp hàng |
| Một sự kiện chở toàn bộ trạng thái | Chỉ gửi phần thay đổi | Frame mất tự chữa ở frame sau | Payload lớn hơn |
| Số thứ tự để bỏ frame cũ | Tin thứ tự đến | Sự kiện trái thứ tự không kéo lùi trạng thái | Client phải nhớ số đã thấy |
| `get-state` trả trên kênh phòng | Gửi riêng người hỏi | Đơn giản hơn, và quy tắc bỏ qua làm frame thừa vô hại | Người khác nhận frame không cần |
| Bình luận **không lưu trữ** | Tạo bảng ngay | MVP thử phản ứng trước khi bỏ công | Mất khi khởi động lại; không sống qua undo |
| Bình luận đi kênh `music` | Kênh riêng | Đã gắn với bài đang phát | Kênh `music` chở hai loại việc |
| Client **hỏi** snapshot bình luận | Server đẩy khi có người vào | Né được bẫy hai cửa vào phòng | Client phải nhớ hỏi |
| URL nhạc 1 giờ | 15 phút như avatar | Buổi nghe dài hơn một lượt xem trang | Link rò ra sống lâu hơn |

---

## 10. Tự kiểm chứng

**Xem anchor thay vì vị trí chạy:**

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT song_title, status, position_seconds, started_at, sequence_number, now() - started_at AS da_troi FROM liveroom_playback_states;"
```

Đây là quan sát thuyết phục nhất của cả file: khi `status = PLAYING`, `position_seconds` **đứng yên** trong khi nhạc vẫn chạy. Vị trí thật = `position_seconds + da_troi`. Chạy lại truy vấn vài lần để thấy `position_seconds` không đổi.

Bấm tạm dừng rồi chạy lại: `position_seconds` **nhảy lên** giá trị mới và `started_at` thành null. Đó là lúc anchor được kết tinh.

**Xem số thứ tự tăng** — mỗi lần bấm play/pause/seek/volume, `sequence_number` tăng đúng 1.

**Ép xung đột khoá lạc quan** — hai client cùng gửi `music/seek` trong cùng một khoảnh khắc. Một bên nhận `LR_075` trên `/user/queue/liveroom/errors`.

**Thấy quy tắc chủ phòng vắng mặt:**

1. Chủ rời phòng → nhạc tự chuyển `PAUSED`
2. Người khác gửi `music/play` → `LR_076 MUSIC_OWNER_ABSENT`
3. Người đó gửi `music/pause` hoặc `music/seek` → **được**, vì chỉ `resume` mới bị chặn

**Thấy bẫy hai cửa được xử lý** — cho một người vào phòng bằng cửa trước và một người được duyệt vào, khi nhạc đang chạy. **Cả hai** phải nhận được frame trạng thái nhạc ngay khi vào. Đây chính là ca mà một phiên bản trước đã bỏ sót.

**Thấy bình luận không lưu trữ** — thêm vài bình luận, rồi tìm chúng trong database:

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "\dt liveroom_*"
```

Không có bảng nào cho bình luận. Khởi động lại backend rồi gửi `comments/get`: danh sách rỗng.

**Lấy URL nhạc:**

```bash
curl -s "http://localhost:8080/api/v1/liveroom/rooms/<roomId>/music/audio-url" -H "Authorization: Bearer $T"
```

`expiresAt` cách hiện tại đúng 1 giờ.

---

## 11. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| **Bình luận mất khi khởi động lại** | Trong bộ nhớ tiến trình, không có bảng — mục 7 |
| Bình luận không sống qua undo-end | `clearCycle` chạy lúc kết thúc, undo không khôi phục |
| Không sửa, không xoá bình luận | Chỉ thêm và đọc |
| Chỉ phát được nhạc của chính mình | Mục 4 |
| Không có hàng chờ phát | Một bài một lúc, hết bài thì phải chọn tay |
| Không tự chuyển bài khi hết | `hasReachedEnd()` tồn tại nhưng không ai theo dõi để chuyển tiếp |
| Chủ rớt mạng là cả phòng không nghe được | Mục 4 |
| Anchor không bù độ trễ mạng | Vị trí lệch đúng bằng thời gian truyền frame, vài chục ms |
| URL nhạc rò ra dùng được 1 giờ | Không thu hồi được |
| Trạng thái phát gắn với **phòng**, không gắn phiên | Khác chat và participants; mở lại phòng phải dọn thủ công ở đường reopen |
