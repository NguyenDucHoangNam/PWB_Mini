# 🎙️ Module LiveRoom — Hướng dẫn sử dụng

## Tổng quan

Module **LiveRoom** cung cấp tính năng phòng phát sóng trực tiếp (live room) cho PWB. Chủ phòng tạo phòng, mời người tham gia qua mã phòng, cùng nghe nhạc, trò chuyện, gọi video/audio realtime — tất cả đồng bộ trong thời gian thực qua WebSocket.

---

## 1. Tạo & Quản lý Phòng (Room)

### 1.1. Tạo phòng

Mỗi phòng được tạo bởi **chủ phòng (Owner)** với các thông tin:

| Tham số | Mô tả | Mặc định | Khoảng giá trị |
|---------|--------|----------|----------------|
| **Tên phòng** | Tên hiển thị của phòng | *(bắt buộc)* | 1 – 100 ký tự |
| **Số người tối đa** | Giới hạn số người tham gia cùng lúc | 7 | 1 – 7 |
| **Thời gian chờ chủ phòng (Grace)** | Khoảng thời gian hệ thống chờ chủ phòng quay lại trước khi tự đóng phòng | 60 giây | 30 – 1800 giây |

**Quy tắc tên phòng:**
- Cho phép chữ cái (mọi ngôn ngữ), số, dấu cách và các ký tự: `. , - _ ' " ! ?`
- Tối đa 100 ký tự.

### 1.2. Mã phòng (Room Code)

Khi tạo phòng, hệ thống tự sinh **mã phòng 6 ký tự** (A–Z, 0–9), ví dụ: `A3B-X7K`. Mã này dùng để mời người khác tham gia phòng.

### 1.3. Trạng thái phòng

| Trạng thái | Ý nghĩa |
|------------|---------|
| `ACTIVE` | Phòng đang hoạt động, có thể tham gia. |
| `ENDED` | Phòng đã kết thúc. |

### 1.4. Các cách kết thúc phòng

Phòng có thể kết thúc bằng nhiều cách:

| Lý do kết thúc | Mô tả |
|-----------------|-------|
| `MANUAL` | Chủ phòng bấm **Kết thúc phòng** thủ công. |
| `OWNER_GRACE_EXPIRED` | Chủ phòng rời phòng và không quay lại trong thời gian grace. |
| `EMPTY_TIMEOUT` | Phòng không còn ai trong một khoảng thời gian (cấu hình được). |
| `FORCE_ROLE_CHANGE` | Đổi quyền sở hữu buộc kết thúc phòng. |

### 1.5. Undo kết thúc & Mở lại phòng

- **Undo End**: Nếu chủ phòng vừa bấm kết thúc (lý do `MANUAL`), có thể **hoàn tác** trong một khoảng thời gian ngắn (cửa sổ undo) — phòng trở lại `ACTIVE`, tất cả người tham gia được phục hồi.
- **Reopen**: Sau khi phòng đã kết thúc hoàn toàn, chủ phòng có thể **mở lại** phòng. Phòng giữ nguyên mã phòng nhưng bắt đầu một **session cycle mới** — mọi dữ liệu phiên trước (người tham gia, chat, playback) được reset.

### 1.6. Session Cycle (Chu kỳ phiên)

Mỗi lần phòng hoạt động (từ tạo/mở lại đến kết thúc) là một **session cycle**. Dữ liệu của mỗi cycle (người tham gia, tin nhắn, trạng thái nhạc) được tách biệt. Khi mở lại phòng, cycle cũ được đóng và cycle mới bắt đầu.

---

## 2. Tham gia Phòng

### 2.1. Quy trình tham gia (Join Flow)

```
Nhập mã phòng (6 ký tự)
       │
       ▼
Tra cứu phòng (Room Lookup)
       │
       ├── Phòng không tồn tại hoặc đã kết thúc → Từ chối
       │
       ▼
Gửi yêu cầu tham gia (Join Request)
       │
       ▼
Chủ phòng duyệt/từ chối
       │
       ├── Duyệt → Vào phòng (Auto Join)
       ├── Từ chối → Thông báo lý do
       └── Hết chỗ → Tự động từ chối
```

### 2.2. Join Request (Yêu cầu tham gia)

Khi người dùng muốn vào phòng, hệ thống tạo một **Join Request** gửi đến chủ phòng.

**Các trạng thái Join Request:**

| Trạng thái | Ý nghĩa |
|------------|---------|
| `PENDING` | Đang chờ chủ phòng duyệt. |
| `APPROVED` | Đã được duyệt → người dùng tự động vào phòng. |
| `REJECTED_BY_OWNER` | Chủ phòng từ chối. |
| `REJECTED_BY_CAPACITY` | Phòng đã đầy. |
| `CANCELLED` | Người dùng tự huỷ yêu cầu. |
| `EXPIRED` | Phòng kết thúc khi request đang chờ. |
| `LOCKED` | Bị chủ phòng từ chối **3 lần** → khoá vĩnh viễn, không thể gửi lại. |

**Quy tắc Join:**
- Mỗi yêu cầu có **idempotency key** để chống gửi trùng.
- Bị chủ phòng từ chối **3 lần** → bị khoá khỏi phòng.
- Đã từng được duyệt → lần sau vào thẳng, không cần chờ duyệt lại (trừ khi bị kick).
- Tra cứu mã phòng có **rate limit** chống brute force.

### 2.3. Room Member (Thành viên phòng)

Hệ thống theo dõi mối quan hệ giữa người dùng và phòng qua **RoomMember**:
- Ghi nhận đã từng được duyệt (`wasApproved`).
- Đếm số lần bị từ chối bởi chủ phòng / hết chỗ.
- Theo dõi trạng thái kick (thời điểm kick, cooldown).
- Khi bị kick, quyền `wasApproved` bị thu hồi.

---

## 3. Người tham gia (Participant)

### 3.1. Vai trò

| Vai trò | Quyền hạn |
|---------|-----------|
| **OWNER** | Tạo phòng, duyệt/từ chối join request, kick người, mute mic từ xa, điều khiển nhạc, kết thúc phòng. |
| **PARTICIPANT** | Tham gia chat, bật/tắt camera & mic, nghe nhạc cùng, gọi video/audio. |

### 3.2. Trạng thái người tham gia

| Trạng thái | Ý nghĩa | Chiếm slot? |
|------------|---------|-------------|
| `ACTIVE` | Đang trong phòng, kết nối tốt. | ✅ |
| `RECONNECTING` | Đang kết nối lại (mất mạng tạm thời). | ✅ |
| `OFFLINE` | Đã mất kết nối. | ❌ |
| `LEFT` | Tự rời phòng. | ❌ |
| `KICKED` | Bị chủ phòng kick ra. | ❌ |
| `ENDED` | Phòng đã kết thúc. | ❌ |

### 3.3. Điều khiển Media (Camera & Mic)

Mỗi người tham gia có thể:
- **Bật/tắt camera**: Tự do bật tắt.
- **Bật/tắt microphone**: Có 3 trạng thái mic:

| Trạng thái mic | Mô tả |
|-----------------|-------|
| `UNMUTED` | Mic đang bật, phát tiếng. |
| `SELF_MUTED` | Tự tắt mic. |
| `MUTED_BY_OWNER` | Bị chủ phòng tắt mic từ xa — kèm **cooldown**, không thể tự bật lại cho đến khi hết cooldown. |

### 3.4. Quyền quản trị của chủ phòng

- **Kick**: Đuổi người ra khỏi phòng, kèm lý do và thời gian cooldown (không thể quay lại trong thời gian cooldown).
- **Remote Mute**: Tắt mic của bất kỳ người tham gia nào từ xa, kèm cooldown chống bật lại ngay.

Tất cả hành động quản trị được ghi log vào **RoomAdminAction** (ai làm, đối tượng, loại hành động, lý do, thời điểm).

---

## 4. Chủ phòng rời phòng (Owner Grace)

Khi chủ phòng rời phòng:

1. Phòng đánh dấu **Owner Absent** và bắt đầu đếm thời gian grace.
2. Một **slot được giữ chỗ** cho chủ phòng (không ai khác chiếm được).
3. Banner thông báo hiển thị cho tất cả người tham gia.
4. Nếu chủ phòng quay lại trước khi hết grace → phòng tiếp tục bình thường.
5. Nếu hết grace mà chủ phòng không quay lại → **phòng tự động kết thúc** (lý do `OWNER_GRACE_EXPIRED`).

**Scheduler kiểm tra mỗi 10 giây** (cấu hình được).

---

## 5. Chat (Trò chuyện)

### 5.1. Tin nhắn văn bản

- Mỗi tin nhắn tối đa **500 ký tự** (tính theo Unicode code point).
- Gửi qua **WebSocket (STOMP)** → realtime cho tất cả người trong phòng.
- Tin nhắn được lưu vào database, phân theo **session cycle**.

### 5.2. Lịch sử chat

- Khi vào phòng, load tối đa **200 tin nhắn gần nhất**.
- Hỗ trợ **cuộn lên xem thêm** (cursor-based pagination).
- Tin nhắn cũ được **tự động xoá** theo lịch trình retention (mặc định: mỗi ngày lúc 4h sáng).

### 5.3. Tính năng chat

- Hỗ trợ **linkify**: URL trong tin nhắn tự động trở thành link có thể click.
- Hiển thị email người gửi, thời gian gửi.
- Tin nhắn đang gửi có trạng thái **pending** (chưa confirm) và **failed** (gửi thất bại).

---

## 6. Nghe nhạc cùng (Music / Playback)

### 6.1. Playback đồng bộ

Chủ phòng điều khiển nhạc, tất cả người tham gia nghe **cùng một bài, cùng vị trí, cùng thời điểm**.

**Các thao tác:**

| Thao tác | Mô tả |
|----------|-------|
| **Chọn bài** | Chọn bài hát từ thư viện cá nhân của chủ phòng (Song Picker). |
| **Play / Pause** | Phát / tạm dừng nhạc. |
| **Seek** | Tua đến vị trí bất kỳ trong bài. |
| **Volume** | Điều chỉnh âm lượng (0 – 100%). |

### 6.2. Trạng thái Playback

Mỗi phòng có **một PlaybackState** duy nhất, lưu:
- Bài hát đang phát (ID, tiêu đề, nghệ sĩ, thời lượng).
- Trạng thái: `PLAYING` hoặc `PAUSED`.
- Vị trí hiện tại (giây).
- Âm lượng (mặc định 80%).
- **Sequence number** — đảm bảo thứ tự event, tránh race condition.
- Thời điểm bắt đầu phát (`startedAt`) — client dùng để tính vị trí realtime: `position = positionSeconds + (now - startedAt)`.

### 6.3. Track Comment (Bình luận theo timeline)

Người tham gia có thể **bình luận tại một thời điểm cụ thể** trong bài hát (giống comment trên SoundCloud):
- Gắn vào vị trí giây (positionSeconds) trên timeline.
- Tối đa **200 ký tự** mỗi comment.
- Hiển thị dưới dạng **lane** trên waveform, popup khi playback đi qua.
- Lưu **in-memory** (không persist vào database) — dữ liệu mất khi phiên kết thúc.
- Khi đổi bài → comments cũ được xoá, snapshot mới được gửi.

---

## 7. Video/Audio Call (WebRTC)

### 7.1. Kiến trúc Mesh

LiveRoom sử dụng **WebRTC Mesh** — mỗi cặp người tham gia kết nối trực tiếp peer-to-peer, không đi qua server.

### 7.2. Signaling qua STOMP

Các bước thiết lập kết nối WebRTC được relay qua server (STOMP WebSocket):
- **Offer/Answer** (SDP): Trao đổi thông tin kết nối.
- **ICE Candidate**: Trao đổi đường đi mạng.

### 7.3. ICE Server

Hệ thống cung cấp cấu hình **ICE Servers** (STUN/TURN) cho client:
- STUN: Giúp peer tìm được địa chỉ public.
- TURN: Relay traffic khi kết nối trực tiếp không thể thiết lập (firewall, NAT...).

### 7.4. Video Grid

Giao diện hiển thị video dạng **grid** — tự động điều chỉnh layout theo số người:

| Số người | Layout |
|----------|--------|
| 1 | Full screen |
| 2 | 2 cột |
| 3–4 | 2×2 grid |
| 5–7 | 3×n grid tối ưu |

Mỗi video tile hiển thị:
- Video stream (hoặc avatar nếu tắt camera).
- Thanh hiển thị mức âm thanh (audio level).
- Badge trạng thái mic (muted / unmuted / muted by owner).
- Label email người tham gia.

---

## 8. Realtime (WebSocket)

### 8.1. Giao thức

Module sử dụng **STOMP over WebSocket** cho toàn bộ giao tiếp realtime:

| Kênh | Mô tả |
|------|-------|
| `/topic/room.{roomId}` | Event chung của phòng (join/leave, media state, capacity...) |
| `/topic/room.{roomId}.chat` | Tin nhắn chat |
| `/topic/room.{roomId}.music` | Sự kiện nhạc (playback state, song change) |
| `/topic/room.{roomId}.comments` | Track comments |
| `/user/queue/rtc` | RTC signals (offer/answer/ICE) — private cho từng user |

### 8.2. Bảo mật WebSocket

- **Authentication**: Token JWT được verify khi kết nối STOMP (handshake).
- **Subscription Scope**: Client chỉ được subscribe vào phòng mà mình đang tham gia — interceptor chặn subscribe ngoài phạm vi.
- **Rate Limiting**: Giới hạn số message gửi qua STOMP để chống spam.

### 8.3. Danh sách Event Realtime

Hệ thống phát ra **27 loại event** realtime:

| Nhóm | Events |
|------|--------|
| **Participant** | `PARTICIPANT_JOINED`, `PARTICIPANT_LEFT`, `PARTICIPANT_KICKED`, `MEDIA_STATE_CHANGED`, `PARTICIPANT_MIC_MUTED_BY_OWNER`, `PARTICIPANT_MIC_UNMUTED` |
| **Owner** | `OWNER_LEFT`, `OWNER_REJOINED` |
| **Room** | `ROOM_CAPACITY_CHANGED`, `CAPACITY_REACHED`, `ROOM_MANUAL_ENDED`, `ROOM_AUTO_ENDED`, `ROOM_REVIVED`, `ROOM_REOPENED` |
| **Join Request** | `JOIN_REQUEST_CREATED`, `JOIN_REQUEST_CANCELLED`, `REQUEST_APPROVED`, `REQUEST_REJECTED_BY_OWNER`, `REQUEST_REJECTED_BY_CAPACITY`, `REQUEST_LOCKED` |
| **Chat** | `CHAT_MESSAGE_RECEIVED` |
| **Music** | `MUSIC_PLAYBACK_STATE_CHANGED`, `MUSIC_SONG_CHANGED` |
| **Track Comment** | `TRACK_COMMENT_ADDED`, `TRACK_COMMENT_SNAPSHOT` |
| **WebRTC** | `RTC_OFFER`, `RTC_ANSWER`, `RTC_ICE_CANDIDATE` |

---

## 9. Giao diện người dùng (Frontend)

### 9.1. Danh sách phòng (`/dashboard/liverooms`)

- Danh sách phòng của bạn (đã tạo), phân trang.
- Mỗi phòng hiển thị: tên, mã phòng, trạng thái (Active/Ended), số người tham gia.
- Thao tác: Tạo phòng mới, Vào phòng, Kết thúc phòng, Mở lại phòng.

### 9.2. Tham gia phòng (Join Flow)

Quy trình tham gia qua giao diện gồm nhiều bước:

1. **Nhập mã phòng** — Form 6 ký tự, tự động format `XXX-XXX`.
2. **Room Lookup** — Hiển thị tên phòng, số người, trạng thái đầy/trống.
3. **Pre-Join Panel** — Kiểm tra quyền truy cập thiết bị (camera/mic), preview trước khi vào.
4. **Join Lobby** — Chờ chủ phòng duyệt, hiển thị countdown.
5. **Auto Join** — Được duyệt → tự động vào phòng.

### 9.3. Trong phòng (Room Screen)

Giao diện phòng bao gồm:

- **Room Header**: Tên phòng, mã phòng (copy), badge kết nối (connected/reconnecting/disconnected).
- **Video Grid**: Lưới video các participant (xem phần 7.4).
- **Room Control Bar**: Nút bật/tắt camera, mic, rời phòng, mở side panel.
- **Side Panel** (trượt ra bên phải) chứa các tab:
  - **Participants**: Danh sách người trong phòng + hàng đợi Join Request (chỉ Owner thấy).
  - **Chat**: Khung chat realtime.
  - **Music**: Music player + Song picker + Track comments.

### 9.4. Music Player

- Hiển thị bài đang phát (tiêu đề, nghệ sĩ).
- Thanh tiến trình (progress bar) cập nhật realtime.
- Nút Play/Pause, thanh âm lượng.
- **Track Waveform**: Hiển thị sóng âm thanh.
- **Track Comment Lane**: Comment hiển thị dọc theo timeline dưới dạng pin nhỏ, popup khi playback đi qua.
- **Song Picker Dialog**: Tìm kiếm và chọn bài từ thư viện.

### 9.5. Các màn hình đặc biệt

- **Kicked Screen**: Hiển thị khi bị kick, kèm lý do và thời gian cooldown.
- **Owner Absent Banner**: Banner cảnh báo khi chủ phòng rời phòng, hiển thị countdown grace.
- **Room Ending Overlay**: Overlay chuyển tiếp khi phòng kết thúc.
- **Tab Conflict Screen**: Ngăn mở cùng phòng trên nhiều tab (Tab Lock).
- **Leave Room Dialog**: Xác nhận trước khi rời phòng.

---

## 10. Tìm kiếm Phòng

- **Full-text search** theo tên phòng, hỗ trợ bởi **Elasticsearch**.
- Chịu lỗi chính tả, hỗ trợ tìm không dấu tiếng Việt.
- Lọc theo trạng thái (`ACTIVE`, `ENDED`).
- **Fallback** database khi Elasticsearch không khả dụng.

---

## 11. Background Schedulers

Hệ thống chạy các tác vụ nền tự động:

| Scheduler | Chức năng | Tần suất |
|-----------|----------|----------|
| **OwnerGraceScheduler** | Kết thúc phòng khi chủ phòng vắng quá grace | Mỗi 10 giây |
| **EmptyRoomScheduler** | Kết thúc phòng trống quá lâu | Mỗi 60 giây |
| **ChatRetentionScheduler** | Xoá tin nhắn chat cũ | Mỗi ngày lúc 4h sáng |
| **IdempotencyKeyCleanupScheduler** | Dọn dẹp idempotency key hết hạn | Định kỳ |

---

## 12. Bảo mật & Quyền truy cập

- Mọi API REST và WebSocket đều yêu cầu **xác thực JWT**.
- **Subscription Scope Interceptor**: Client chỉ subscribe được vào phòng đang tham gia.
- **Rate Limit Interceptor**: Giới hạn tần suất gửi message qua STOMP.
- **Tra cứu mã phòng** có rate limit chống brute force.
- **Ownership check**: Hầu hết hành động quản trị chỉ dành cho Owner.
- Tất cả hành động quản trị (kick, mute) được **ghi audit log**.

---

## 13. Tóm tắt kiến trúc kỹ thuật

| Thành phần | Công nghệ |
|------------|-----------|
| Backend | Java 21, Spring Boot 3.x |
| Realtime | STOMP over WebSocket (Spring WebSocket) |
| Video/Audio | WebRTC (Mesh topology, peer-to-peer) |
| Signaling | STOMP relay (Offer/Answer/ICE) |
| ICE Servers | STUN / TURN (cấu hình sẵn) |
| Tìm kiếm | Elasticsearch |
| Frontend | Next.js, TypeScript, Zustand (state), TanStack Query |
| State Management | Zustand store (event-driven reducer) |
| Form Validation | React Hook Form + Zod |
| Tab Lock | BroadcastChannel API (chống mở trùng tab) |
