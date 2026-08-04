# Live Room — Việc còn lại & quy ước (bàn giao)

**Cập nhật**: 2026-08-04 — backend xong (Phase 0–8), còn frontend.
**Mục đích**: đủ để một phiên làm việc mới tiếp tục module mà không cần đọc lại lịch sử hội thoại. Nghiệp vụ đọc ở [`liveroom-business-requirements.md`](./liveroom-business-requirements.md). Tài liệu này chỉ giữ **việc chưa làm, nợ chưa fix, và những quy ước không đọc ra được từ code**.

---

## 1. Hiện trạng

Module `Backend/modules/liveroom` (artifact `pwb-liveroom`, package `com.pwb.liveroom`).

Backend hoàn tất: vòng đời phòng · join/approve/reject/leave · STOMP realtime · kick/remote-mute/media · scheduler · chat · shared listening · WebRTC signaling. Còn lại **Phase 9 — frontend** (mục 4).

Migration đã chạy tới `V304`; **migration tiếp theo bắt đầu từ `V305`**.

Danh sách route REST, kênh STOMP và WS event **không chép lại ở đây** — đọc thẳng từ code, đó mới là bản đúng:

- REST: `api/controller/` (`RoomController`, `JoinRequestController`, `ParticipantController`, `ChatController`, `RtcController`)
- STOMP nhận: `api/realtime/` (`ChatStompController`, `MusicStompController`, `RtcStompController`)
- WS event: `application/event/LiveroomEventType` (25 loại), payload ở `RoomEvents`
- Mã lỗi: `application/exception/LiveroomErrorCode` (40 mã)

---

## 2. Quy ước đã chốt

> Đây là những chỗ code **cố ý khác** tài liệu nghiệp vụ, hoặc khác cách làm hiển nhiên. Đừng "sửa" chúng nếu chưa đọc lý do.

### 2.1 Mã lỗi dùng số, không dùng tên

Error code là `LR_001`…`LR_092` (theo chuẩn `IAM_xxx` / `AUDIO_xxx` của repo), **không** dùng key `LIVEROOM_ROOM_NOT_FOUND` như §8.3 tài liệu. Tên trong tài liệu trở thành **tên hằng của enum**. Key i18n cho lỗi là chính mã số; key cho message thành công/cảnh báo thì giữ nguyên tên như tài liệu.

Nhóm mã chừa khoảng trống giữa các cụm để luật mới chèn được cạnh hàng xóm của nó thay vì nối vào cuối. Thêm mã mới nhớ cập nhật **cả hai** file `liveroom/messages*.properties`.

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

> ⚠️ Method này là sửa đổi **local, chưa commit** ở shared-kernel. Nó đã từng bị mất một lần (file bị đưa về bản HEAD) làm cả module ngừng compile — 8 mapper đều gọi nó còn setter thì `protected`. Xem mục 5.

### 2.11 `RoomEvents` không dùng `Map.of`

`Map.of` ném NPE với giá trị null, mà nhiều field trong payload vắng mặt là chuyện bình thường (phòng chưa kết thúc thì không có `endedAt`). Dùng helper `map(...)` trong `RoomEvents`.

### 2.12 Throttle tra mã phòng là adapter riêng

Không dùng `HttpRateLimitFilter` chung, vì filter đó gộp mọi endpoint vào **một key Redis theo IP** (`pwb:ratelimit:global:{ip}`) — thêm rule cửa sổ 1 giờ sẽ kéo dài cửa sổ của toàn bộ API.

### 2.13 Client không được SEND thẳng vào broker

Simple broker của Spring **chuyển tiếp nguyên văn** frame `SEND` mà client gửi tới `/topic/**` hay `/queue/**`. Không chặn thì bất kỳ session đã đăng nhập nào cũng phát được event giả vào topic phòng — không qua use case, không kiểm tra thành viên, và với chat thì không có dòng nào trong DB. Lỗ này tồn tại từ Phase 3, chặn ở `StompSubscriptionScopeInterceptor` (`checkPublish`).

Hệ quả: **mọi thứ client gửi phải đi qua `/app/**`** và có `@MessageMapping` xử lý.

### 2.14 Chat đi kênh con, và **gửi cả cho người viết**

Chat phát lên `/topic/liveroom/{roomId}/chat` chứ không lên topic phòng — client hiện danh sách người tham gia không phải thức dậy theo từng tin nhắn. Kênh con vẫn nằm dưới topic của phòng nên `StompSubscriptionScopeInterceptor` kiểm tra quyền y hệt (nó cắt `{roomId}` ở segment đầu).

Tài liệu R-CHAT-07 nói phát cho mọi người **trừ** người gửi. Code phát cho **tất cả**, kể cả người gửi: bản echo đó mang `messageId` + `sentAt` đã lưu, tức là thứ để client đối chiếu tin nhắn nó đã hiện lạc quan. Loại trừ người gửi lại phải gửi riêng từng người — đắt hơn mà lợi ít hơn. Client dedupe theo `messageId`.

Thêm kênh con mới thì dùng `LiveroomEventPublisher.broadcastToRoomChannel(event, channel)`.

### 2.15 Lỗi của frame STOMP đi `/user/queue/liveroom/errors`

`GlobalExceptionHandler` chỉ gắn vào đường dispatch của servlet, **không thấy** message frame. Không có `LiveroomStompExceptionHandler` (`@ControllerAdvice` + `@MessageExceptionHandler`) thì một frame bị từ chối sẽ chết im lặng phía server còn client ngồi chờ broadcast không bao giờ tới.

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

### 2.20 Signaling đi hàng đợi riêng của từng người, **không** đi topic con

Kế hoạch ban đầu định dùng `/topic/liveroom/{roomId}/rtc/{targetUserId}`. Không dùng được: `StompSubscriptionScopeInterceptor` chỉ đọc `{roomId}` ở segment đầu, nên **bất kỳ ai trong phòng cũng subscribe được topic RTC của người khác** và đọc trọn SDP/ICE của cặp peer đó. Muốn vá thì phải cho interceptor hiểu thêm segment cuối — tức là thêm một luật bảo mật nữa dễ quên.

Dùng `/user/queue/liveroom/rtc` (`convertAndSendToUser`) thì Spring định tuyến theo tên principal, **không có cách nào subscribe hàng đợi của người khác**. An toàn theo cấu trúc, không cần luật gì thêm.

Đã kiểm chứng khi test: cho người thứ ba subscribe `/topic/liveroom/{roomId}/rtc/{ownerId}` — subscribe **thành công** (đúng như dự đoán) nhưng không nhận được gì, vì không có gì được phát ra đó.

Thêm kênh con cho hàng đợi cá nhân thì dùng `sendToUserChannel(userId, event, channel)`.

### 2.21 `RoomSessions` là chỗ chung cho guard "phòng còn sống + người còn trong phòng"

Việc gì cần "phòng đang ACTIVE và người này đang ở trong đó" thì dùng `application/support/RoomSessions` (`requireActiveRoom` / `requireInRoom` / `requireTargetInRoom`), đừng chép lại. `Playbacks` cũng gọi lại nó.

### 2.22 Module liveroom **không viết comment**

190 file, không một dòng javadoc hay comment. Đây là lựa chọn có chủ đích, không phải sót — module `audio` và `iam` vẫn giữ comment của chúng. Lý do một quyết định được làm như vậy thì viết vào **mục 2 này**, không viết vào code.

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

Docker Desktop phải chạy trước (Postgres + Redis). Không có nó thì app không boot được.

### 3.3 Quy trình kiểm chứng (đã dùng suốt Phase 1–8)

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

Nhớ subscribe cả `/user/queue/liveroom/errors`: frame bị từ chối chỉ báo ở đó, còn `publish` của stompjs thì không bao giờ ném lỗi — không nghe hàng đợi này thì một frame bị chặn trông y hệt một frame bị mất.

> Kinh nghiệm: viết script test kỹ có lợi. Phase 3/4/5/7 đều có lỗi thật chỉ lộ ra khi chạy client thật, không lộ khi build.

---

## 4. Phase 9 — Frontend (việc còn lại)

Thư mục `frontend/src/features/liveroom/` theo khuôn `features/voice/` (`api/ components/ hooks/ schemas/ types/`).

Màn hình: tạo phòng · nhập mã · pre-join (thử camera/mic) · lobby chờ duyệt · phòng họp · màn bị kick.

Hook `useLiveroomSocket` bọc `@stomp/stompjs` (đã cài sẵn cùng `sockjs-client`).

Hành vi phía client mà tài liệu yêu cầu nhưng backend **không** làm hộ:

- Chặn nhiều tab cùng phòng (R-JOIN-10) — dùng `localStorage` + `BroadcastChannel`
- Cache trạng thái bị kick (R-KICK-04) để F5 không nháy về màn "xin vào"
- Đếm ngược 3s trước khi tự vào phòng sau khi được duyệt (R-LEAVE-10)
- Hoãn 5s trước khi rời màn hình khi phòng kết thúc, để kịp nhận `ROOM_REVIVED` nếu owner hoàn tác (R-END-12.1)
- Sinh `idempotencyKey` (UUID v4) và lưu `localStorage`
- Escape nội dung chat — backend **không** escape HTML
- Dedupe chat theo `messageId` — người gửi cũng nhận lại event của chính mình (§2.14)
- Đọc `state` chứ không đọc HTTP status khi duyệt join request (§2.3)
- Nhớ `sequenceNumber` cuối đã apply, **bỏ mọi event music không lớn hơn nó** (§2.18). Không làm thì event đến trễ sẽ kéo player lùi lại.
- Tự chạy tiếp vị trí bài hát từ `positionSeconds` + `startedAt` khi `status = PLAYING` (§2.16), đừng hỏi lại server mỗi giây
- Nhận `LR_075` thì gửi `get-state` rồi apply state trả về, không retry mù
- Nút Play tắt khi `ownerAbsent = true` và mình không phải owner
- Sau `ROOM_REVIVED` thì gửi `get-state` để lấy lại player — backend không tự phát lại
- Quy ước mesh: **người đang ở trong phòng gọi người mới vào** — nhận `PARTICIPANT_JOINED` thì gửi offer cho người đó. Không có quy ước này thì hai bên cùng offer và va nhau.
- Tháo peer connection khi nhận `PARTICIPANT_LEFT` / `PARTICIPANT_KICKED` — backend không có event RTC riêng cho việc đó
- Đọc `GET /rtc/config` khi vào phòng, đừng hard-code ICE server hay giới hạn payload

---

## 5. Nợ chưa fix

Đã đối chiếu với code ngày 2026-08-04 — **tất cả các mục dưới đây vẫn còn nguyên**.

### 5.1 Phải xử lý trước khi lên production / scale

| Vấn đề | Ảnh hưởng |
| ------ | --------- |
| **`restoreAuditTimestamps` chưa commit** | `DomainBaseEntity.restoreAuditTimestamps` mới chỉ là sửa đổi local ở shared-kernel (`git status` vẫn báo `M`). Đã mất một lần và cả module ngừng compile. **Commit sớm.** |
| **Broker STOMP in-memory** | `enableSimpleBroker` — đúng với 1 instance. Chạy nhiều node thì broadcast của node này không tới subscriber của node kia. Phải thay bằng broker ngoài (Redis/RabbitMQ) trước khi scale. |
| **Registry phiên WS in-memory** | `StompSessionRegistryAdapter` giữ `ConcurrentHashMap` trong process. Kick chỉ đóng được phiên gắn với node hiện tại. Cùng gốc với vấn đề trên. |
| **TURN credential tĩnh** | `username` / `credential` lấy thẳng từ `pwb.liveroom.rtc.ice-servers` rồi trả cho mọi client trong phòng. Lộ ra là ai cũng relay qua TURN server bằng băng thông của mình. Dựng TURN thật thì phải chuyển sang credential có hạn dùng (HMAC theo thời gian, RFC 5766 REST API). Hiện chưa có TURN nên chưa cháy. |
| **STUN mặc định là dịch vụ bên thứ ba** | Mặc định trỏ `stun:stun.l.google.com:19302` để chạy được ngay. Production nên tự dựng. |

### 5.2 Nhỏ hơn, hoặc cố ý để lại

| Vấn đề | Ảnh hưởng |
| ------ | --------- |
| **R-ROLE-06 chưa có gì cả** | PRO bị hạ quyền giữa phiên thì phải force-end phòng. Hiện **chỉ có mã `LR_010` nằm im, 0 chỗ dùng** — không có hook, không có nguồn kích hoạt, vì IAM chưa có API đổi role. |
| **Timer debounce trong process** | `OwnerPresenceAnnouncer` giữ `ScheduledFuture` trong bộ nhớ. Node chết giữa cửa sổ 3s thì mất thông báo `OWNER_LEFT`. Không ảnh hưởng tính đúng của grace — scheduler vẫn kết thúc phòng đúng hạn. |
| `GET /rooms/{roomId}` chỉ owner đọc được | `GetRoomUseCaseImpl` gọi `requireOwned`. Participant đang ở trong phòng chưa đọc được chi tiết. Mở rộng khi client thật sự cần. |
| **Chưa có cleanup chat 90 ngày** | R-CHAT-06 nói xoá sau 90 ngày. `infrastructure/scheduler/` mới có empty-room, owner-grace, idempotency-key. Chat lưu vĩnh viễn. POST-MVP. |
| **Không có REST POST gửi chat** | Tài liệu (UC-10, EC-15) mô tả `POST /:id/chat/messages`. Cố ý không làm — gửi tin chỉ qua STOMP. Muốn thêm thì `SendChatMessageUseCase` dùng lại nguyên vẹn, chỉ cần thêm controller. |
| **Không có event peer rời (RTC)** | Client tự tháo peer connection theo `PARTICIPANT_LEFT` / `PARTICIPANT_KICKED`. Không có event RTC riêng. |
| **Mesh 7 người** | Mỗi client giữ tới 6 peer connection. Giới hạn của mesh; đông hơn phải chuyển SFU. |
| Message `LR_051` ghi "1 minutes" | Template `{0} phút` / `{0} minutes` do §8.3 quy định; tiếng Việt không dính, tiếng Anh sai số ít/nhiều khi còn dưới 1 phút. |
| **Test IAM hỏng sẵn** | `AuthControllerTest`, `IamFacadeImplTest`, `AuthEndpointContractTest` tham chiếu class đã xoá → `-DskipTests` không build được. Ngoài phạm vi module này. |
| **`SongMapper` thiếu `restoreAuditTimestamps`** | Response bài hát của module audio không có `createdAt`/`updatedAt`. Ngoài phạm vi module này nhưng dễ fix (§2.10). |

---

## 6. Bắt đầu một phiên mới thế nào

Backend đã xong hết; còn lại là Phase 9 (frontend, mục 4). Trước khi viết code nên:

1. Đọc mục **2 (quy ước đã chốt)** — quan trọng nhất, tránh "sửa" những thứ cố ý.
2. Đọc phần nghiệp vụ tương ứng trong `liveroom-business-requirements.md`.
   Tài liệu đó **cũ hơn code** ở vài chỗ — mục 2 mới là thứ đang chạy.
3. Xem 2–3 file cùng loại đã có để bắt khuôn (đặt tên, tách lớp). **Không viết comment** (§2.22).
4. Làm xong một mảng thì dừng lại để review, đừng gộp nhiều mảng.
