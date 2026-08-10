# Live Room — Chat

> STOMP `/app/liveroom/{roomId}/chat/send` → `/topic/liveroom/{roomId}/chat` · REST `GET /rooms/{id}/chat/messages`
> Bối cảnh: [Live Room — Tour](liveroom-00-tour.md) · Tầng vận chuyển: [13 — Realtime STOMP](13-realtime-stomp.md)

---

## 1. Bài toán

Chat trong phòng nghe qua đơn giản, nhưng bốn chi tiết dễ làm sai:

1. **Gửi đi thì lưu ở đâu, và ai nhận?** Kể cả người vừa gửi?
2. **Đếm độ dài tin nhắn thế nào** khi có emoji?
3. **Tải lịch sử** trong một luồng liên tục thì phân trang kiểu gì?
4. **Giữ tin nhắn bao lâu?**

Ba cái đầu đều có bẫy, và code xử lý cả ba đúng.

---

## 2. Gửi tin nhắn

```java
String content = ChatMessage.normalizeContent(command.content());
if (content.isEmpty())                                         throw … CHAT_EMPTY;
if (ChatMessage.lengthOf(content) > ChatMessage.MAX_CONTENT_LENGTH) throw … CHAT_TOO_LONG;

ChatMessage saved = chatMessageRepository.save(ChatMessage.send(
        room.getId(), room.getCurrentCycleId(),
        participant.getUserId(), participant.getUserEmail(), content, now));

participant.markInteraction(now);
participantRepository.save(participant);

eventPublisher.broadcastToRoomChannel(
        RoomEvents.chatMessageReceived(room, saved), LiveroomEventPublisher.CHAT_CHANNEL);
```

Chỉ người **đang ngồi trong phòng** gửi được (`NOT_IN_SESSION` nếu không). Tin nhắn gắn với **`cycleId`**, không gắn với `roomId` — phòng mở lại là một luồng chat mới.

`participant.markInteraction(now)` cập nhật `last_interaction_at`. Chat là một dấu hiệu "người này còn sống", tách khỏi việc kết nối WebSocket còn mở hay không.

---

## 3. Đếm độ dài bằng code point

```java
public static final int MAX_CONTENT_LENGTH = 500;

public static int lengthOf(String content) {
    return content.codePointCount(0, content.length());
}
```

Không dùng `String.length()`. Lý do rất cụ thể: Java lưu chuỗi bằng UTF-16, và một emoji ngoài mặt phẳng cơ bản (😀, 🎵, phần lớn emoji hiện đại) chiếm **hai** đơn vị `char`. `String.length()` đếm nó là 2.

PostgreSQL `VARCHAR(500)` đếm theo **ký tự** — tức là code point. Nên một tin nhắn 300 emoji:

| Cách đếm | Kết quả | Kết luận |
|---|---|---|
| `String.length()` | 600 | **Từ chối** |
| `codePointCount` | 300 | Chấp nhận |
| PostgreSQL | 300 | Chấp nhận |

Dùng `String.length()` sẽ từ chối những tin nhắn mà cột hoàn toàn chứa được. Không phải lỗi bảo mật, chỉ là hệ thống nói dối người dùng về giới hạn của chính nó — và với một sản phẩm mà người dùng gõ tiếng Việt kèm emoji thì nó xuất hiện thường xuyên.

`normalizeContent` chỉ `strip()` — cắt khoảng trắng hai đầu, không đụng gì bên trong. Chuỗi toàn khoảng trắng thành rỗng và bị từ chối.

Quy tắc nằm **hai chỗ**: `ChatMessage.send` ném `IllegalArgumentException`, và use case kiểm trước rồi ném `LiveroomBusinessException` với mã lỗi tử tế. Domain model là chốt chặn cuối, use case là chỗ cho ra thông báo đẹp — cùng khuôn với kiểm tên trùng ở [audio-02 §2.1](audio-02-voice-tag.md).

---

## 4. Người gửi cũng nhận lại tin nhắn của mình

Tin nhắn phát tới `/topic/liveroom/{roomId}/chat` — **cả phòng, kể cả người vừa gửi**.

Tài liệu yêu cầu nói ngược lại: không gửi lại cho tác giả. Cài đặt cố ý làm khác, và lý do là **hiển thị lạc quan**:

Client hiện tin nhắn ngay khi bấm gửi, chưa chờ server. Nhưng bản hiện ngay đó **chưa có `id` và chưa có `sentAt` thật** — hai thứ chỉ database mới cấp. Bản dội về chính là bản mang hai giá trị đó, và client dùng nó để thay thế bản tạm.

Không dội về thì client phải lấy `id` từ response của lời gọi STOMP — mà `@MessageMapping` không có kiểu response tự nhiên như REST, nên sẽ phải gửi riêng cho từng người. Gửi riêng cho tác giả **và** gửi chung cho phòng là hai lần gửi cho cùng một nội dung.

Đây là lựa chọn giữa "một lần phát cho tất cả" và "đúng chữ trong tài liệu yêu cầu".

---

## 5. Phân trang lịch sử bằng con trỏ

```java
List<ChatMessage> fetched = loadPage(cycleId, command.cursor(), size + 1);
boolean hasMore = fetched.size() > size;
List<ChatMessage> page = hasMore ? fetched.subList(0, size) : fetched;
UUID nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
```

**Lấy dư một bản ghi** để biết còn nữa hay không, rồi cắt bỏ. Rẻ hơn hẳn một câu `COUNT` riêng, và không bao giờ lệch giữa hai truy vấn.

Con trỏ là `id` của tin nhắn cuối trang. Truy vấn trang tiếp theo:

```java
chatMessageRepository.findOlderByCycleId(cycleId, anchor.getSentAt(), anchor.getId(), limit)
```

Dùng **cả `sentAt` lẫn `id`** làm mốc. Chỉ dùng thời gian thì hai tin nhắn cùng một mili giây sẽ hoặc bị lặp hoặc bị nhảy cóc — `id` là cái phá hoà.

Đây là lý do phân trang theo con trỏ phù hợp hơn `OFFSET` cho một luồng đang chạy: với `OFFSET`, mỗi tin nhắn mới đến sẽ đẩy toàn bộ trang cũ xuống một dòng, và cuộn lên sẽ thấy tin nhắn lặp lại.

Con trỏ trỏ vào một tin nhắn **không thuộc phiên hiện tại** thì không báo lỗi — nó lặng lẽ trả về trang mới nhất và ghi một dòng log debug. Con trỏ cũ từ phiên trước là chuyện có thể xảy ra bình thường sau khi phòng mở lại.

`DEFAULT_HISTORY_SIZE = 200`, và `clampSize` chặn client tự đặt số lớn.

---

## 6. Chống lụt và giữ tin nhắn

**Rate limit STOMP**: bucket `CHAT` — **15 frame trong 10 giây** ([13 §5.3](13-realtime-stomp.md)). Vượt thì frame bị **nuốt im lặng** và người gửi nhận đúng một cảnh báo `LR_081` cho mỗi cửa sổ. Con số 15 được chọn theo tốc độ gõ của người thật.

**Giữ 90 ngày** (`chat.retention`), do `ChatRetentionScheduler` chạy lúc 4 giờ sáng mỗi ngày dọn ([liveroom-07](liveroom-07-scheduler.md)).

Chú ý hệ quả: tin nhắn sống theo **thời gian tuyệt đối**, không theo vòng đời phòng. Một phòng còn hoạt động sau 90 ngày sẽ mất dần tin nhắn cũ nhất của chính nó.

---

## 7. Quyết định & đánh đổi

| Quyết định | Thay vì | Vì sao | Cái giá |
|---|---|---|---|
| Đếm bằng code point | `String.length()` | Khớp cách PostgreSQL đếm `VARCHAR(500)` | Phải nhớ dùng ở mọi chỗ kiểm độ dài |
| Người gửi cũng nhận lại | Loại tác giả như tài liệu yêu cầu | Bản dội về mang `id` và `sentAt` thật để đối chiếu hiển thị lạc quan | Lệch tài liệu; client phải biết cách khớp |
| Chat gắn với `cycleId` | Gắn với `roomId` | Mở lại phòng là luồng chat mới | Không xem được chat buổi trước |
| Kênh chat riêng | Chung `/topic/liveroom/{id}` | Client tắt chat không phải giải mã frame chat | Thêm một subscription |
| Lấy dư một bản ghi | `COUNT` riêng | Một truy vấn, không lệch | Truy vấn lấy thừa một dòng |
| Con trỏ `(sentAt, id)` | `OFFSET` | Tin nhắn mới không làm trang cũ nhảy | Không nhảy thẳng tới trang N được |
| Con trỏ lạ → trả trang mới nhất | Ném lỗi | Con trỏ từ phiên cũ là chuyện bình thường | Client có thể không nhận ra mình đang xem lại từ đầu |
| Giới hạn ở cả domain và use case | Chỉ một chỗ | Domain là chốt cuối, use case cho thông báo đẹp | Cùng con số kiểm hai lần |
| Giữ 90 ngày theo thời gian tuyệt đối | Theo vòng đời phòng | Dọn dẹp đơn giản, một scheduler | Phòng sống lâu mất dần tin nhắn của chính nó |
| 15 frame / 10 giây | Cao hơn | Đúng tốc độ người gõ | Dán nhiều dòng liên tiếp có thể chạm trần |

---

## 8. Tự kiểm chứng

**Xem tin nhắn trong database:**

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT c.cycle_number, m.user_email, left(m.content, 40) AS noi_dung, m.sent_at FROM liveroom_chat_messages m JOIN liveroom_session_cycles c ON c.id = m.cycle_id ORDER BY m.sent_at DESC LIMIT 10;"
```

**Tải lịch sử qua REST:**

```bash
T=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"pro1@gmail.com","password":"@NamHoang511"}' | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
```

```bash
curl -s "http://localhost:8080/api/v1/liveroom/rooms/<roomId>/chat/messages?size=5" -H "Authorization: Bearer $T"
```

Lấy `nextCursor` từ response rồi gọi lại kèm `&cursor=<nextCursor>` để thấy trang cũ hơn.

**Thấy cách đếm code point** — đây là thí nghiệm rõ nhất của file. Gửi một tin nhắn gồm **300 emoji** qua STOMP:

- Nếu code dùng `String.length()`: bị từ chối `CHAT_TOO_LONG` (đếm ra 600)
- Code hiện tại: **chấp nhận**, và cột `VARCHAR(500)` chứa được

Kiểm lại độ dài Postgres nhìn thấy:

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT length(content) AS pg_dem, octet_length(content) AS so_byte FROM liveroom_chat_messages ORDER BY sent_at DESC LIMIT 3;"
```

`length()` của Postgres đếm ký tự, `octet_length()` đếm byte — chênh nhau ở tin nhắn có emoji hoặc tiếng Việt có dấu.

**Thấy người gửi nhận lại tin của mình** — mở DevTools, xem frame STOMP đến trên `/topic/liveroom/{id}/chat` ngay sau khi bấm gửi. Frame đó mang `id` mà client chưa từng biết.

**Thấy rate limit** — gửi hơn 15 tin nhắn trong 10 giây. Các frame thừa **biến mất không có phản hồi**, và đúng một `LR_081` về `/user/queue/liveroom/errors`.

---

## 9. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| Không sửa, không xoá tin nhắn | Không có endpoint nào |
| Không xem được chat của phiên trước | Gắn `cycleId`, và không có API chọn phiên |
| Không có tin nhắn riêng | Chỉ chat chung cả phòng |
| Không gửi được file hay ảnh | Chỉ văn bản |
| Không có "đang gõ…" hay dấu đã đọc | Không có sự kiện nào cho việc đó |
| Không nhắc tên ai được | Nội dung là văn bản thuần, không phân tích gì |
| Phòng sống quá 90 ngày mất tin nhắn cũ | Mục 6 |
| Chat vẫn ghi khi phòng sắp kết thúc | Không có trạng thái "đang đóng" để chặn |
