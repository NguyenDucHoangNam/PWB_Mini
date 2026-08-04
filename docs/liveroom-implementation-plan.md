# Live Room — Kế hoạch hiện thực (bàn giao)

**Cập nhật**: 2026-08-04 (hết Phase 7)
**Mục đích**: Tài liệu này đủ để một phiên làm việc mới tiếp tục module Live Room mà không cần đọc lại lịch sử hội thoại. Nghiệp vụ đọc ở [`liveroom-business-requirements.md`](./liveroom-business-requirements.md); tài liệu này nói về **hiện trạng code, quy ước đã chốt, và việc còn lại**.

---

## 1. Hiện trạng

Module `Backend/modules/liveroom` (artifact `pwb-liveroom`, package `com.pwb.liveroom`).

| Phase | Nội dung | Trạng thái |
| ----- | -------- | ---------- |
| 0 | Scaffolding: pom, AutoConfiguration, error code, i18n | ✅ |
| 1 | Vòng đời phòng: create / get / list / by-code / end / undo-end / reopen | ✅ |
| 2 | Join request, approve, reject, join/leave, capacity lock | ✅ |
| 3 | STOMP realtime: xác thực, phạm vi subscribe, event publisher | ✅ |
| 4 | Kick, remote-mute, media state, audit log | ✅ |
| 5 | Scheduler grace/empty-room, debounce owner, dọn idempotency key | ✅ |
| 6 | Chat trong phòng | ✅ |
| 7 | Shared listening (nhạc) | ✅ |
| **8** | **WebRTC signaling** | ⬜ |
| **9** | **Frontend** | ⬜ |

Phase 9 nên làm sau cùng.

### Đã có gì

- **Migration**: `V300` (rooms, session cycles, ownership history) · `V301` (participants, room members, join requests) · `V302` (admin actions, cột mute cooldown) · `V303` (chat messages) · `V304` (playback state). **Migration tiếp theo bắt đầu từ `V305`.**
- **24 use case**, 4 REST controller + 2 STOMP controller, 37 error code, 22 loại WS event.
- **Realtime**: endpoint `/ws`, broker `/topic` + `/queue`, prefix `/app`. Client **chỉ được gửi vào `/app/**`** — xem §2.13.

### API hiện có

```
POST   /api/v1/liveroom/rooms                                  tạo phòng (PRO)
GET    /api/v1/liveroom/rooms                                  danh sách phòng của mình
GET    /api/v1/liveroom/rooms/{roomId}                         chi tiết (chỉ owner)
GET    /api/v1/liveroom/rooms/by-code/{roomCode}               tra mã (có throttle)
POST   /api/v1/liveroom/rooms/{roomId}/end
POST   /api/v1/liveroom/rooms/{roomId}/undo-end
POST   /api/v1/liveroom/rooms/{roomId}/reopen

POST   .../rooms/{roomId}/join-requests                        xin vào (kèm idempotencyKey)
GET    .../rooms/{roomId}/join-requests                        danh sách chờ (owner)
DELETE .../rooms/{roomId}/join-requests/{requestId}            tự huỷ
POST   .../rooms/{roomId}/join-requests/{requestId}/approve
POST   .../rooms/{roomId}/join-requests/{requestId}/reject

POST   .../rooms/{roomId}/participants/me                      vào phòng / vào lại
DELETE .../rooms/{roomId}/participants/me                      rời phòng
GET    .../rooms/{roomId}/participants                         danh sách người trong phòng
PATCH  .../rooms/{roomId}/participants/me/media                bật/tắt camera-mic
POST   .../rooms/{roomId}/participants/{targetUserId}/kick
POST   .../rooms/{roomId}/participants/{targetUserId}/mute

GET    .../rooms/{roomId}/chat/messages?cursor=&size=       lịch sử chat (cycle hiện tại)
```

### Kênh STOMP hiện có

```
SUB   /topic/liveroom/{roomId}               event của phòng
SUB   /topic/liveroom/{roomId}/chat          tin nhắn
SUB   /topic/liveroom/{roomId}/music         trạng thái player
SUB   /user/queue/liveroom                   quyết định join request + player lúc mới vào
SUB   /user/queue/liveroom/errors            lỗi của frame vừa gửi (xem §2.15)
SEND  /app/liveroom/{roomId}/chat/send       {"content": "..."}
SEND  /app/liveroom/{roomId}/music/play      {"songId": "..."} chọn bài — {} thì resume
SEND  /app/liveroom/{roomId}/music/pause     {}
SEND  /app/liveroom/{roomId}/music/seek      {"positionSeconds": 120}
SEND  /app/liveroom/{roomId}/music/volume    {"volumePercent": 80}
SEND  /app/liveroom/{roomId}/music/get-state {}
```

### WS event hiện có

`PARTICIPANT_JOINED` · `PARTICIPANT_LEFT` · `PARTICIPANT_KICKED` · `MEDIA_STATE_CHANGED` · `PARTICIPANT_MIC_MUTED_BY_OWNER` · `PARTICIPANT_MIC_UNMUTED` · `OWNER_LEFT` · `OWNER_REJOINED` · `ROOM_CAPACITY_CHANGED` · `CAPACITY_REACHED` · `JOIN_REQUEST_CREATED` · `JOIN_REQUEST_CANCELLED` · `REQUEST_APPROVED` · `REQUEST_REJECTED_BY_OWNER` · `REQUEST_REJECTED_BY_CAPACITY` · `REQUEST_LOCKED` · `ROOM_MANUAL_ENDED` · `ROOM_AUTO_ENDED` · `ROOM_REVIVED` · `CHAT_MESSAGE_RECEIVED` · `MUSIC_PLAYBACK_STATE_CHANGED` · `MUSIC_SONG_CHANGED`

---

## 2. Quy ước đã chốt

> Đây là những chỗ code **cố ý khác** tài liệu nghiệp vụ, hoặc khác cách làm hiển nhiên. Đừng "sửa" chúng nếu chưa đọc lý do.

### 2.1 Mã lỗi dùng số, không dùng tên

Error code là `LR_001`…`LR_080` (theo chuẩn `IAM_xxx` / `AUDIO_xxx` của repo), **không** dùng key `LIVEROOM_ROOM_NOT_FOUND` như §8.3 tài liệu. Tên trong tài liệu trở thành **tên hằng của enum**. Key i18n cho lỗi là chính mã số; key cho message thành công/cảnh báo thì giữ nguyên tên như tài liệu.

Mã đã đặt trước cho phase sau: **`LR_060`, `LR_061`** (chat) và **`LR_070`–`LR_077`** (nhạc) — đã có sẵn trong `LiveroomErrorCode` và cả 2 file i18n, chỉ việc dùng.

### 2.2 Gộp bảng `liveroom_room_members`

Kế hoạch ban đầu tách `reject_counters` thành bảng riêng. Thực tế gộp vào `room_members` cùng `was_approved` và kick cooldown — tất cả đều khoá `(room_id, user_id)` và đều được đọc trong cùng một lượt kiểm tra lúc vào phòng.

### 2.3 Duyệt phòng đầy trả **200**, không phải lỗi

`POST .../approve` khi phòng đã đầy trả HTTP 200 với `state = REJECTED_BY_CAPACITY`. Ném exception sẽ rollback đúng cái thay đổi trạng thái mà owner cần thấy. **Client phải đọc `state`, không đọc status code.**

### 2.4 Bị khoá sau 3 lần từ chối → 403, không ghi dòng nào

Tài liệu R-REJECT-05 nói tạo JoinRequest với state `LOCKED`. Code trả 403 `LR_032` và không ghi gì — ghi dòng `LOCKED` sẽ đẩy vào hàng chờ của owner những yêu cầu mà hệ thống đã tự quyết định.

### 2.5 Endpoint `/ws` đăng ký **hai lần**

```java
registry.addEndpoint("/ws").setAllowedOriginPatterns(origins);
registry.addEndpoint("/ws").setAllowedOriginPatterns(origins).withSockJS();
```

`withSockJS()` **thay thế** mapping WebSocket thường chứ không thêm vào. Đăng ký hai lần để client native dùng `ws://host/ws`, còn trình duyệt dùng fallback dưới `/ws/**`.

`/ws/**` nằm trong `pwb.iam.security.public-endpoints` vì trình duyệt không đặt được header `Authorization` lên handshake — token đi kèm frame STOMP `CONNECT`, và `StompAuthChannelInterceptor` xác thực ở đó.

### 2.6 Quyết định về join request gửi riêng cho từng người

`REQUEST_APPROVED` / `REQUEST_REJECTED_*` đi qua `/user/queue/liveroom`, **không** lên topic phòng. Việc ai đó bị từ chối là chuyện giữa họ và owner.

### 2.7 Không được hoãn hai lớp

`LiveroomEventPublisher` **đã tự** giữ event tới sau khi transaction commit. Nếu caller lại gói lệnh phát vào `afterCommit` của chính mình thì thành đăng ký callback từ bên trong callback — quá muộn để chạy, và **event biến mất không báo lỗi**. Spring vẫn báo transaction đang hoạt động bên trong `afterCommit` nên không runtime check nào bắt được.

Đã mất một lúc để tìm ra ở Phase 5 (`OWNER_REJOINED` im lặng). Xem `OwnerPresenceAnnouncer` để thấy cách xử lý đúng.

### 2.8 Cooldown mute xét theo mốc thời gian, không theo `micState`

Xét theo `micState` là một đường lách: bất kỳ thay đổi nào để mic ở trạng thái tắt (ví dụ tắt camera) đều đẩy state về `SELF_MUTED`, và từ đó bật mic lại được ngay. Mốc thời gian chưa hết hạn còn được **giữ qua leave-rejoin**.

### 2.9 Đóng phiên WS phải đóng `WebSocketSession`

Spring **bỏ qua** frame `DISCONNECT` gửi theo chiều server→client, nên mẹo "gửi DISCONNECT xuống client" hay thấy trên mạng không có tác dụng. `WebSocketConfig` đăng ký transport decorator giao socket cho `StompSessionRegistryAdapter`, adapter này đóng socket **sau commit ~2 giây** — độ trễ đó để người bị kick kịp nhận event giải thích lý do trước khi mất kết nối.

### 2.10 `restoreAuditTimestamps`

`DomainBaseEntity.restoreAuditTimestamps` tồn tại vì rehydrate domain object mà không khôi phục `createdAt`/`updatedAt` sẽ để chúng null — và do `default-property-inclusion: non_null`, hai field này **âm thầm biến mất khỏi mọi response**. Mọi mapper của liveroom đều phải gọi nó.

> ⚠️ **Module audio vẫn còn lỗi này** — `SongMapper.toDomain` chưa gọi, nên response bài hát không có timestamp.

### 2.11 `RoomEvents` không dùng `Map.of`

`Map.of` ném NPE với giá trị null, mà nhiều field trong payload vắng mặt là chuyện bình thường (phòng chưa kết thúc thì không có `endedAt`). Dùng helper `map(...)` trong `RoomEvents`.

### 2.12 Throttle tra mã phòng là adapter riêng

Không dùng `HttpRateLimitFilter` chung, vì filter đó gộp mọi endpoint vào **một key Redis theo IP** (`pwb:ratelimit:global:{ip}`) — thêm rule cửa sổ 1 giờ sẽ kéo dài cửa sổ của toàn bộ API.

### 2.13 Client không được SEND thẳng vào broker

Simple broker của Spring **chuyển tiếp nguyên văn** frame `SEND` mà client gửi tới `/topic/**` hay `/queue/**`. Không chặn thì bất kỳ session đã đăng nhập nào cũng phát được event giả vào topic phòng — không qua use case, không kiểm tra thành viên, và với chat thì không có dòng nào trong DB. Lỗ này tồn tại từ Phase 3; Phase 6 chặn ở `StompSubscriptionScopeInterceptor` (`checkPublish`).

Hệ quả: **mọi thứ client gửi phải đi qua `/app/**`** và có `@MessageMapping` xử lý. Phase 7 và 8 cứ theo khuôn đó.

### 2.14 Chat đi kênh con, và **gửi cả cho người viết**

Chat phát lên `/topic/liveroom/{roomId}/chat` chứ không lên topic phòng — client hiện danh sách người tham gia không phải thức dậy theo từng tin nhắn. Kênh con vẫn nằm dưới topic của phòng nên `StompSubscriptionScopeInterceptor` kiểm tra quyền y hệt (nó cắt `{roomId}` ở segment đầu).

Tài liệu R-CHAT-07 nói phát cho mọi người **trừ** người gửi. Code phát cho **tất cả**, kể cả người gửi: bản echo đó mang `messageId` + `sentAt` đã lưu, tức là thứ để client đối chiếu tin nhắn nó đã hiện lạc quan. Loại trừ người gửi lại phải gửi riêng từng người — đắt hơn mà lợi ít hơn. Client dedupe theo `messageId`.

Thêm kênh con mới thì dùng `LiveroomEventPublisher.broadcastToRoomChannel(event, channel)`.

### 2.15 Lỗi của frame STOMP đi `/user/queue/liveroom/errors`

`GlobalExceptionHandler` chỉ gắn vào đường dispatch của servlet, **không thấy** message frame. Không có `LiveroomStompExceptionHandler` (`@ControllerAdvice` + `@MessageExceptionHandler`) thì một tin nhắn bị từ chối sẽ chết im lặng phía server còn client ngồi chờ broadcast không bao giờ tới.

Body giữ đúng khuôn `ApiResponse` như lỗi HTTP (`code` = `LR_xxx`), nhưng đi hàng đợi **riêng** — subscriber của `/user/queue/liveroom` đang hiểu mọi frame ở đó là `RoomEvent`. `broadcast = false` để lỗi chỉ về đúng session đã gửi, không nháy sang tab khác của cùng người.

### 2.16 Vị trí bài hát lưu theo **mốc**, không lưu giá trị đang chạy

`position_seconds` là vị trí **tại `started_at`**, không phải vị trí hiện tại. Khi `status = PLAYING` thì vị trí thật = `position_seconds + (now - started_at)`. Lưu giá trị đang chạy sẽ cần một writer tick mỗi giây mà chẳng được gì thêm — client vào giữa bài cũng tự tính được từ đúng hai con số đó.

`PlaybackState.positionAt(now)` là chỗ duy nhất tính giá trị này, và nó **kẹp theo `duration`** để một bài để chạy quá đoạn cuối không báo vị trí vượt độ dài. Event luôn phát giá trị đã tính, kèm `startedAt` + `status` để client tự chạy tiếp.

### 2.17 Nhạc dừng khi phòng kết thúc, nhưng **không xoá dòng**

R-MUSIC-07 nói xoá `PlaybackState` khi phòng ENDED. Code chỉ **pause** và giữ dòng lại; xoá thật nằm ở **reopen**.

Lý do: hoàn tác (undo-end) khôi phục nguyên cuộc họp đang dở — participant, cycle, chat đều quay lại. Xoá playback thì nhạc là thứ duy nhất không quay lại. Client vẫn dừng phát đúng lúc vì đã nhận `ROOM_*_ENDED`, nên phần nghiệp vụ quan trọng không đổi.

Reopen là cuộc họp mới nên xoá, giống hệt cách chat bắt đầu rỗng ở cycle mới.

### 2.18 Chỉ có một event mang **toàn bộ** state

Tài liệu (UC-12) nói phát cả `MUSIC_SONG_CHANGED` lẫn `MUSIC_PLAYBACK_STATE_CHANGED` khi đổi bài. Code phát **một** event: đổi bài thì `type = MUSIC_SONG_CHANGED`, còn lại là `MUSIC_PLAYBACK_STATE_CHANGED`. **Payload hai loại giống hệt nhau** — type chỉ để client biết có phải nạp lại audio source không. Phát hai frame cho một thay đổi thì thừa và sinh câu hỏi apply cái nào trước.

Event có thêm `ownerAbsent`: client cần nó để bật/tắt nút Play mà không phải tự đối chiếu với `OWNER_LEFT`.

`get-state` **phát lên topic phòng** chứ không trả riêng, để client chỉ phải hiểu một khuôn dữ liệu duy nhất. Người khác nhận rồi cũng bỏ qua vì `sequenceNumber` không tăng — luật "bỏ event không mới hơn cái đang hiện" của R-MUSIC-10 xử lý luôn phần dư thừa này.

### 2.19 Có **hai** đường vào phòng, đừng chỉ móc một

Duyệt join request (`ApproveJoinRequestUseCaseImpl`) gọi thẳng `admissions.admit` — người được duyệt vào phòng luôn, **không** đi qua `JoinRoomUseCase`. Lúc làm Phase 7 đã sót đúng chỗ này: người được duyệt giữa lúc đang phát nhạc là người duy nhất trong phòng không nghe thấy gì. Chỉ lộ ra khi chạy client thật.

Việc gì cần chạy "khi có người vào phòng" thì phải móc **cả hai** chỗ.

---

## 3. Build, chạy, kiểm chứng

### 3.1 Build

```bash
cd Backend && mvn -q clean install -Dmaven.test.skip=true
```

**Phải dùng `-Dmaven.test.skip=true`**, không phải `-DskipTests`: `-DskipTests` vẫn compile test, và test của IAM đang hỏng sẵn (tham chiếu `IamFacade` / `AuthView` đã bị xoá ở commit `caaa5a1`).

**Không viết test tự động cho module này** — kiểm chứng bằng cách chạy API thật.

### 3.2 Chạy local

Ba rào cản đã biết trên máy Windows này:

1. **JDK không tạo được AF_UNIX socket** → mọi NIO `Selector` chết (Kafka, Netty/S3, có khi cả Tomcat) với `IOException: Unable to establish loopback connection`. Nguyên nhân nhiều khả năng là temp dir `C:\Users\HOANG NAM\...` có dấu cách. Khắc phục: `-Djdk.net.unixdomain.tmpdir=`
2. **`.env` ở gốc repo còn Maven chạy từ `Backend/`** → spring-dotenv không nạp được, mật khẩu Redis rơi về mặc định sai, mọi request auth trả `IAM_034`. Khắc phục: truyền `-Dspring.data.redis.password=...`
3. **Port 8080 thường đã có instance khác** — kiểm tra trước, dùng cổng khác thay vì kill.

Lệnh chạy được:

```bash
cd D:/Learning/Project/PWB_MiNi && mvn -f Backend/pom.xml -pl bootstrap spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8098 -Djdk.net.unixdomain.tmpdir= -Dspring.data.redis.password=@NamHoang511 -Dspring.main.lazy-initialization=true"
```

`-Dspring.main.lazy-initialization=true` để S3/Kafka không chặn khởi động khi chỉ cần thử API.

### 3.3 Quy trình kiểm chứng (đã dùng suốt Phase 1–5)

**Không đụng DB dev `pwb_db`.** Dựng DB tạm, chạy kịch bản, xoá sạch:

```bash
# 1. dựng DB tạm
docker exec pwb-postgres psql -U pwb_user -d pwb_db -c "DROP DATABASE IF EXISTS pwb_scratch;" -c "CREATE DATABASE pwb_scratch;"

# 2. boot trỏ vào DB tạm (thêm vào jvmArguments ở trên)
#    -Dspring.datasource.url=jdbc:postgresql://localhost:5433/pwb_scratch

# 3. chạy kịch bản, soi dữ liệu
docker exec pwb-postgres psql -U pwb_user -d pwb_scratch -c "select ... from liveroom_...;"

# 4. dọn: kill tiến trình theo port, drop DB tạm
docker exec pwb-postgres psql -U pwb_user -d pwb_db -c "DROP DATABASE IF EXISTS pwb_scratch;"
```

**User có sẵn** (seed từ `application-dev-users.yml`, mật khẩu đều là `@NamHoang511`):
`pro1@gmail.com`, `pro2@gmail.com` (role PRO — cần cho tạo phòng) · `user1@gmail.com`, `user2@gmail.com` · `admin1@gmail.com`, `admin2@gmail.com`

**Rút ngắn thời gian chờ khi test** bằng property, đừng sửa code:

```
-Dpwb.liveroom.moderation.mic-unmute-cooldown=6s
-Dpwb.liveroom.moderation.kick-cooldown=8s
-Dpwb.liveroom.scheduler.owner-grace-interval-ms=2000
-Dpwb.liveroom.room.empty-timeout=5s
```

Riêng `ownerGraceSeconds` tối thiểu 30s (ràng buộc nghiệp vụ, có CHECK constraint) — muốn test hết grace thì phải chờ thật.

**Test nhạc**: không upload được bài thật vì cần S3. Chèn thẳng vào DB tạm là đủ, module liveroom chỉ đọc `id / user_id / title / artist / duration_seconds / status`:

```sql
INSERT INTO audio_songs (id, created_at, updated_at, created_by, updated_by, user_id,
                         title, artist, original_s3_key, processed_s3_key, duration_seconds, status)
SELECT '11111111-1111-4111-8111-111111111111'::uuid, now(), now(), 'seed', 'seed', id,
       'Guest Track', 'Guest', 'orig/g1.mp3', 'proc/g1.mp3', 300, 'PROCESSED'
  FROM iam_users WHERE email = 'user1@gmail.com';
```

Nhớ ép kiểu `::uuid` — Postgres không tự ép literal text sang uuid ở đây.

**Test WebSocket**: dùng `@stomp/stompjs` đã cài sẵn ở `frontend/node_modules`. Viết script `.mjs` tạm trong `frontend/`, chạy `node script.mjs`, **xoá sau khi xong**. Kết nối `ws://localhost:8098/ws`, token đặt ở `connectHeaders.Authorization`.

Gói `ws` **không** có trong `node_modules` — đừng import. Node ở máy này là v24, đã có `WebSocket` toàn cục nên stompjs chạy thẳng.

Nhớ subscribe cả `/user/queue/liveroom/errors`: frame bị từ chối chỉ báo ở đó, còn `publish` của stompjs thì không bao giờ ném lỗi — không nghe hàng đợi này thì một tin nhắn bị chặn trông y hệt một tin nhắn bị mất.

> Kinh nghiệm: viết script test kỹ có lợi. Cả 3 phase 3/4/5 đều có lỗi thật chỉ lộ ra khi chạy client thật, không lộ khi build.

---

## 4. Phase 6 — Chat trong phòng ✅

**Nghiệp vụ**: §7.5 tài liệu (R-CHAT-01…08), §7.6 edge case, UC-10 / UC-11.

Migration `V303__create_liveroom_chat.sql` tạo `liveroom_chat_messages`, index `(cycle_id, sent_at DESC, id DESC)`. Chat gắn theo **cycle**, không theo phòng — reopen là mở cuộc trò chuyện mới, transcript cũ vẫn nằm nguyên trong DB. **Không xoá khi phòng kết thúc**; cleanup 90 ngày là POST-MVP.

Quy tắc đã hiện thực:

- Gửi được khi **đang ở trong phòng** (`isInRoom`, tức ACTIVE hoặc RECONNECTING) — owner không có đặc quyền gì thêm, người ở lobby không gửi được (`LR_042`). Phòng đã kết thúc → `LR_002`.
- Content `strip()` rồi kiểm tra rỗng (`LR_060`) và ≤ 500 (`LR_061`). Đếm bằng **code point**, khớp cách `VARCHAR(500)` của Postgres đếm — `String.length()` sẽ tính emoji ngoài BMP thành 2 và từ chối nhầm.
- Gửi xong cập nhật `Participant.markInteraction` — người chỉ chat mà không đụng camera/mic vẫn phải được coi là đang hoạt động.
- `user_email` lấy từ **dòng participant**, không lấy từ token: transcript hiện đúng cái tên mà cả phòng nhìn thấy.
- `ChatMessage` bất biến; `ChatMessageRepositoryImpl.save` ném lỗi nếu nhận object đã có id.
- Lịch sử: mặc định 200 tin mới nhất của cycle hiện tại, cursor phân trang theo cặp `(sentAt, id)` — chỉ theo timestamp thì hai tin trùng mốc sẽ bị lặp hoặc bị nhảy ở ranh giới trang. Đọc dư 1 dòng để biết `hasMore` mà không phải đếm cả cycle.
- Cursor không tra được trong cycle hiện tại (thường là client giữ trang từ trước lúc reopen) thì **coi như không có cursor** và trả trang mới nhất, để client tự đồng bộ lại thay vì kẹt.

Xem thêm §2.13, §2.14, §2.15 — ba quyết định của phase này nằm ở đó.

Backend **không** escape HTML; frontend phải escape khi render.

### Chỗ cố ý không làm

Tài liệu (UC-10, EC-15) mô tả `POST /:id/chat/messages`. Code **không có** route đó — gửi tin chỉ qua STOMP, vì client lúc đó đã nối sẵn và chính broadcast là phản hồi. Muốn thêm REST POST thì `SendChatMessageUseCase` dùng lại được nguyên vẹn, chỉ cần thêm controller.

---

## 5. Phase 7 — Shared listening (nhạc) ✅

**Nghiệp vụ**: §7.9 tài liệu (R-MUSIC-01…11), §7.11 edge case, UC-12 / UC-13.

Migration `V304__create_liveroom_playback.sql` tạo `liveroom_playback_states`, unique theo `room_id` — một phòng một bài, không queue. **Không có FK sang `audio_songs`**: bảng đó thuộc module khác, và bài bị xoá khỏi thư viện không được phép làm hỏng phòng từng phát nó. Snapshot `song_title` / `song_artist` / `song_duration_seconds` là thứ giữ player vẫn đọc được khi đó.

`pwb-liveroom` đã thêm dependency `pwb-audio`, đi qua `SongCatalogPort` (`domain/service`) với adapter `AudioSongCatalogAdapter` — chỉ đọc, một chiều, không tạo vòng.

Quy tắc đã hiện thực:

- Điều khiển **chỉ qua STOMP** (R-MUSIC-09), không có REST nào cho play/pause/seek/volume. Danh sách bài để chọn dùng `GET /api/v1/songs` sẵn có của module audio.
- Ai đang ở trong phòng cũng điều khiển được. Volume là **một mức chung cho cả phòng**, không phải theo từng người.
- Chọn bài: chỉ bài **của chính mình** (`LR_070`) và phải `PROCESSED` (`LR_071`). Bài của người khác trả cùng mã với bài không tồn tại, để không lộ thư viện người khác qua việc dò id.
- Owner vắng mặt → không ai resume được (`LR_076`), nhưng pause/seek/volume vẫn được, và **chọn bài mới vẫn được** — bài nạp vào ở trạng thái PAUSED chờ owner về, thay vì từ chối và làm mất lựa chọn đó.
- Owner rời → nhạc pause **ngay**, không đi qua debounce của `OwnerPresenceAnnouncer` (debounce là để banner khỏi nháy; nhạc vẫn chạy trong phòng vắng owner mới là thứ cần chặn).
- Optimistic lock qua cột `version` của `LiveroomJpaBaseEntity`. Xung đột nổ lúc **commit**, tức là sau khi use case đã return — nên không bắt được trong use case; `LiveroomStompExceptionHandler` map `OptimisticLockingFailureException` → `LR_075`. Hai người cùng chọn bài đầu tiên của phòng thì đụng unique constraint thay vì version, `PlaybackStateRepositoryImpl` `saveAndFlush` rồi dịch `DataIntegrityViolationException` sang cùng mã.
- Phòng chưa ai bật nhạc thì **không có dòng nào** trong DB — `get-state` trả state rỗng dựng tại chỗ, không ghi.

Xem thêm §2.16, §2.17, §2.18, §2.19 — bốn quyết định của phase này nằm ở đó.

### Lưu ý

- Owner vắng mặt hay không đọc từ `room.isOwnerAbsent()` — có sẵn ở `LiveRoom`.
- `Playbacks` (`application/support`) gom toàn bộ guard + lifecycle của player. Bốn lệnh điều khiển chỉ khác nhau đúng dòng đổi state, nên guard để chung một chỗ; tách ra bốn use case là cách để một hôm nào đó một lệnh thiếu mất một guard.
- Sau `undo-end` client nên gọi `get-state` để đồng bộ lại — backend **không** tự phát lại state khi hồi sinh phòng.

---

## 6. Phase 8 — WebRTC signaling

**Nghiệp vụ**: §1.1, §9.2, mesh tối đa 7 người.

- Trung chuyển SDP offer/answer và ICE candidate qua STOMP:
  `/app/liveroom/{roomId}/rtc/offer|answer|ice` → `/topic/liveroom/{roomId}/rtc/{targetUserId}`.
  Lưu ý: client **không** được SEND thẳng vào `/topic/**` (§2.13), nên mọi frame RTC phải qua `@MessageMapping`.
- Backend **không đụng vào media**, chỉ chuyển tiếp. Chỉ participant `ACTIVE` mới được gửi/nhận.
- Cần cấu hình STUN/TURN (thêm vào `LiveroomConfig`, trả về cho client qua REST khi vào phòng).
- Mesh 7 người ⇒ mỗi client giữ tới 6 peer connection. Đây là giới hạn của mesh, muốn đông hơn phải chuyển sang SFU.

---

## 7. Phase 9 — Frontend

Thư mục `frontend/src/features/liveroom/` theo khuôn `features/voice/` (`api/ components/ hooks/ schemas/ types/`).

Màn hình: tạo phòng · nhập mã · pre-join (thử camera/mic) · lobby chờ duyệt · phòng họp · màn bị kick.

Hook `useLiveroomSocket` bọc `@stomp/stompjs` (đã cài sẵn cùng `sockjs-client`).

Hành vi phía client mà tài liệu yêu cầu nhưng backend **không** làm hộ:

- Chặn nhiều tab cùng phòng (R-JOIN-10) — dùng `localStorage` + `BroadcastChannel`
- Cache trạng thái bị kick (R-KICK-04) để F5 không nháy về màn "xin vào"
- Đếm ngược 3s trước khi tự vào phòng sau khi được duyệt (R-LEAVE-10)
- Hoãn 5s trước khi rời màn hình khi phòng kết thúc, để kịp nhận `ROOM_REVIVED` nếu owner hoàn tác (R-END-12.1)
- Sinh `idempotencyKey` (UUID v4) và lưu `localStorage`
- Escape nội dung chat
- Dedupe chat theo `messageId` — người gửi cũng nhận lại event của chính mình (§2.14)
- Nhớ `sequenceNumber` cuối đã apply, **bỏ mọi event music không lớn hơn nó** (§2.18). Không làm thì event đến trễ sẽ kéo player lùi lại.
- Tự chạy tiếp vị trí bài hát từ `positionSeconds` + `startedAt` khi `status = PLAYING` (§2.16), đừng hỏi lại server mỗi giây
- Nhận `LR_075` thì gửi `get-state` rồi apply state trả về, không retry mù
- Nút Play tắt khi `ownerAbsent = true` và mình không phải owner
- Sau `ROOM_REVIVED` thì gửi `get-state` để lấy lại player

---

## 8. Nợ kỹ thuật đã biết

| Vấn đề | Ảnh hưởng |
| ------ | --------- |
| **Broker STOMP in-memory** | Đúng với 1 instance. Chạy nhiều node thì broadcast của node này không tới subscriber của node kia — phải thay bằng broker ngoài (Redis/RabbitMQ) trước khi scale. |
| **Registry phiên WS in-memory** | Kick chỉ đóng được phiên đang gắn với node hiện tại. Cùng gốc với vấn đề trên. |
| **Timer debounce trong process** | Node chết giữa cửa sổ 3s thì mất thông báo `OWNER_LEFT`. Không ảnh hưởng tính đúng của grace (scheduler vẫn kết thúc phòng đúng hạn). |
| `GET /rooms/{roomId}` chỉ owner đọc được | Participant đang ở trong phòng chưa đọc được chi tiết. Mở rộng khi client thật sự cần. |
| **R-ROLE-06 chưa có nguồn kích hoạt** | PRO bị hạ quyền giữa phiên thì phải force-end phòng, nhưng IAM chưa có API đổi role. Thiết kế sẵn hook, **không** làm phần admin đổi role (ngoài phạm vi Live Room). |
| Message `LR_051` ghi "1 minutes" | Template `{0} phút` / `{0} minutes` do §8.3 quy định; tiếng Việt không dính, tiếng Anh sai số ít/nhiều khi còn dưới 1 phút. |
| **Test IAM hỏng sẵn** | `-DskipTests` không build được. Không thuộc phạm vi module này. |
| **`SongMapper` thiếu `restoreAuditTimestamps`** | Response bài hát của module audio không có `createdAt`/`updatedAt`. |

---

## 9. Bắt đầu một phiên mới thế nào

Nói thẳng phase muốn làm, ví dụ *"làm phase 6"*. Trước khi viết code nên:

1. Đọc mục **2 (quy ước đã chốt)** của tài liệu này — quan trọng nhất, tránh "sửa" những thứ cố ý.
2. Đọc phần nghiệp vụ tương ứng trong `liveroom-business-requirements.md`.
   Tài liệu đó **cũ hơn code** ở vài chỗ — mục 2 mới là thứ đang chạy.
3. Xem 2–3 file cùng loại đã có trong `modules/liveroom` để bắt khuôn (đặt tên, tách lớp, cách viết comment).
4. Làm xong **một phase thì dừng lại** để review, đừng gộp nhiều phase.
