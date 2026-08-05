# Live Room — Việc còn lại & quy ước (bàn giao)

**Cập nhật**: 2026-08-04 — backend xong (Phase 0–8) + một lượt vá lỗ hổng/thiếu sót (mục 7), còn frontend.
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

Viết đủ hai file vẫn chưa đủ: bundle phải được **đăng ký** trong `MessageSourceConfig.setBasenames` (`classpath:liveroom/messages`). Thiếu dòng đó thì không có lỗi nào cả — `getMessage` lặng lẽ rơi về default, lỗi trả `defaultMessage` tiếng Anh hard-code trong enum còn message thành công trả về **đúng cái key thô** (`"LIVEROOM_ROOM_CREATED"`). Module liveroom chạy suốt Phase 1–8 trong tình trạng này mà không ai thấy, vì response vẫn 200 và vẫn có trường `message`. Thêm module mới có bundle riêng thì nhớ dòng basename.

Placeholder đếm số ở bản tiếng Anh dùng `ChoiceFormat` (`{0} {0,choice,1#minute|1<minutes}`) để không ra "1 minutes"; tiếng Việt không cần vì không chia số nhiều.

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

`/user/**` cũng nằm trong danh sách chặn, và đây là phần **dễ sót nhất**. `UserDestinationMessageHandler` đăng ký trên `clientInboundChannel`, và frame `SEND` của client mang `SimpMessageType.MESSAGE` — nhánh mà `DefaultUserDestinationResolver` đọc tên người nhận **từ chính destination**, không phải từ principal của session. Chặn mỗi `/topic/` + `/queue/` thì một client vẫn gửi được `/user/{userId người khác}/queue/liveroom` và Spring giao tận nơi: giả được `REQUEST_APPROVED`, `PARTICIPANT_KICKED`, cả offer RTC.

Chặn ở clientInbound **không** ảnh hưởng đường phát của server: `convertAndSendToUser` đi qua `brokerChannel`, còn `SUBSCRIBE` tới `/user/queue/**` là lệnh khác nên không bị đụng. Đã chạy lại chat, quyết định join request và signaling RTC sau khi thêm luật — vẫn nhận đủ.

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

Hệ quả cho client: người vừa được duyệt **đã ngồi sẵn trong phòng** trước khi `REQUEST_APPROVED` rời server (cùng transaction). Gọi `POST /participants/me` lúc đó sẽ ăn `LR_041`. Luật đúng cho FE, dùng chung cho mọi trường hợp:

> Vào màn phòng họp thì cứ gọi `POST /participants/me`, và coi **`LR_041` là thành công**. `LR_044` → đá về luồng xin vào; `LR_051` → màn cooldown bị kick; `LR_040` phòng đầy; `LR_002` phòng đã kết thúc.

Luật này đúng cho cả owner vào phòng mình, người vừa được duyệt, và người rời rồi quay lại.

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

Hai endpoint thêm ở lượt vá (mục 7), FE cần biết là có:

- `GET /rooms/{roomId}` giờ **participant đang trong phòng cũng đọc được**, không còn chỉ owner. Người ngoài vẫn nhận 404 (`LR_001`) chứ không phải 403 — cố ý, để không lộ phòng có tồn tại hay không.
- `GET /rooms/{roomId}/join-requests/me` trả yêu cầu **mới nhất** của chính mình (`PENDING` / `APPROVED` / `REJECTED_*`), 404 `LR_030` nếu chưa từng gửi. Đây là thứ để màn lobby dựng lại trạng thái sau F5 thay vì phải nhớ `requestId` trong `localStorage`.

Khuôn frame WS: `{ type, roomId, timestamp, data }` — payload nằm ở **`data`**, không phải `payload`.

---

## 5. Nợ chưa fix

Đã đối chiếu với code ngày 2026-08-04, **sau** lượt vá ở mục 6 — những gì mục 6 đã xử lý thì đã gỡ khỏi bảng này.

### 5.1 Phải xử lý trước khi lên production / scale

| Vấn đề | Ảnh hưởng |
| ------ | --------- |
| **Broker STOMP in-memory** | `enableSimpleBroker` — đúng với 1 instance. Chạy nhiều node thì broadcast của node này không tới subscriber của node kia. Phải thay bằng broker ngoài (Redis/RabbitMQ) trước khi scale. |
| **Registry phiên WS in-memory** | `StompSessionRegistryAdapter` giữ `ConcurrentHashMap` trong process. Kick chỉ đóng được phiên gắn với node hiện tại. Cùng gốc với vấn đề trên. |
| **TURN credential tĩnh** | `username` / `credential` lấy thẳng từ `pwb.liveroom.rtc.ice-servers` rồi trả cho mọi client trong phòng. Lộ ra là ai cũng relay qua TURN server bằng băng thông của mình. Dựng TURN thật thì phải chuyển sang credential có hạn dùng (HMAC theo thời gian, RFC 5766 REST API). Hiện chưa có TURN nên chưa cháy. |
| **STUN mặc định là dịch vụ bên thứ ba** | Mặc định trỏ `stun:stun.l.google.com:19302` để chạy được ngay. Production nên tự dựng. |

### 5.2 Nhỏ hơn, hoặc cố ý để lại

| Vấn đề | Ảnh hưởng |
| ------ | --------- |
| **R-ROLE-06 chưa có gì cả** | PRO bị hạ quyền giữa phiên thì phải force-end phòng. Hiện **chỉ có mã `LR_010` nằm im, 0 chỗ dùng** — không có hook, không có nguồn kích hoạt, vì IAM chưa có API đổi role. |
| **Timer debounce trong process** | `OwnerPresenceAnnouncer` giữ `ScheduledFuture` trong bộ nhớ. Node chết giữa cửa sổ 3s thì mất thông báo `OWNER_LEFT`. Không ảnh hưởng tính đúng của grace — scheduler vẫn kết thúc phòng đúng hạn. |
| **Không có REST POST gửi chat** | Tài liệu (UC-10, EC-15) mô tả `POST /:id/chat/messages`. Cố ý không làm — gửi tin chỉ qua STOMP. Muốn thêm thì `SendChatMessageUseCase` dùng lại nguyên vẹn, chỉ cần thêm controller. |
| **Không có event peer rời (RTC)** | Client tự tháo peer connection theo `PARTICIPANT_LEFT` / `PARTICIPANT_KICKED`. Không có event RTC riêng. |
| **Mesh 7 người** | Mỗi client giữ tới 6 peer connection. Giới hạn của mesh; đông hơn phải chuyển SFU. |
| **Test IAM hỏng sẵn** | `AuthControllerTest`, `IamFacadeImplTest`, `AuthEndpointContractTest` tham chiếu class đã xoá → `-DskipTests` không build được. Ngoài phạm vi module này. |
| **Quét chat chạy một câu DELETE** | `ChatRetentionScheduler` xoá cả lượt trong một transaction. Dữ liệu còn nhỏ nên chưa sao; khi bảng lớn thì chia lô. |
| **Signaling RTC mở transaction mỗi frame** | `RelayRtcSignalUseCaseImpl` là `@Transactional(readOnly = true)` với 3 truy vấn cho **mỗi** ICE candidate. Đúng nhưng tốn; đông người thì cache membership. |

---

## 6. Lượt vá trước khi sang FE (2026-08-04)

Một lượt rà lại backend trước khi bắt đầu Phase 9. Tất cả đã kiểm chứng bằng script chạy API + STOMP thật trên DB tạm theo §3.3 (12 kiểm tra REST/WS + retention + regression RTC), script đã xoá sau khi xong.

| # | Sửa | Vì sao đáng sửa |
| - | --- | --------------- |
| 1 | Chặn client `SEND` tới `/user/**` (`StompSubscriptionScopeInterceptor`) | Lỗ bảo mật: giả được event riêng tư gửi cho người khác. Xem §2.13. |
| 2 | Đăng ký `classpath:liveroom/messages` trong `MessageSourceConfig` | Toàn bộ i18n của module chưa từng chạy — lỗi trả tiếng Anh hard-code, message thành công trả key thô. Xem §2.1. |
| 3 | 3 mapper của module audio gọi `restoreAuditTimestamps` | `SongMapper`, `VoiceTagMapper`, `SongTagConfigMapper` đều đánh rơi `createdAt`/`updatedAt`, và `non_null` làm hai trường đó biến mất khỏi mọi response audio. Xem §2.10. |
| 4 | `GET /rooms/{roomId}` cho participant trong phòng | FE không đọc được tên/sức chứa phòng mình đang ở. Người ngoài vẫn 404. |
| 5 | Thêm `GET /rooms/{roomId}/join-requests/me` | Màn lobby không có cách dựng lại trạng thái sau F5. |
| 6 | `ChatRetentionScheduler` (R-CHAT-06, mặc định 90 ngày) | Chat trước đó lưu vĩnh viễn. Chỉnh bằng `pwb.liveroom.chat.retention` + `pwb.liveroom.scheduler.chat-retention-cron`. |
| 7 | `ChoiceFormat` cho `LR_051` / `LR_052` bản tiếng Anh | Hết "1 minutes" / "1 seconds". |

**Không** đụng tới, vẫn nằm ở mục 5: broker/registry in-memory, TURN credential tĩnh, R-ROLE-06 (IAM chưa có API đổi role nên không có nguồn kích hoạt), REST POST gửi chat (cố ý không làm).

### 6.1 Bổ sung khi bắt đầu frontend (Phase 0 của FE)

`GET /rooms/{roomId}/music/audio-url` — thêm vì phát hiện **nghe chung không chạy được cho người không sở hữu bài hát**: `SongUseCaseImpl.getAudioUrl` đi qua `requireOwnedSong`, còn `SongCatalogPort` chỉ trả metadata (`PlayableSong` không có S3 key). Mọi người nhận được tên bài và độ dài mà không có đường nào lấy file.

Endpoint chắn bằng `RoomSessions.requireInRoom` (ngoài phòng → `LR_042`), và **`songId` lấy từ `PlaybackState` của phòng chứ không nhận từ client** — nhận từ client thì nó thành oracle dò quyền sở hữu bài hát của người khác. Chưa có bài nào đang phát → `LR_072`. TTL chỉnh bằng `pwb.liveroom.music.audio-url-ttl` (mặc định 1h).

`SongCatalogPort` thêm `presignPlayback(songId, ttl)`; `AudioSongCatalogAdapter` gọi thẳng `StoragePort.presignDownload` — **cố ý không qua `SongUseCase`**, vì use case đó gắn liền với kiểm tra quyền sở hữu.

Đã kiểm chứng bằng API thật: chủ bài và người khác trong phòng đều nhận URL presign của cùng một `songId`; người ngoài phòng nhận `LR_042`.

Lưu ý khi test: `-Dspring-boot.run.jvmArguments` **không truyền được** giá trị có dấu cách (cron), dùng biến môi trường (`PWB_LIVEROOM_SCHEDULER_CHATRETENTIONCRON`). Và script STOMP phải `sleep` sau khi `subscribe` rồi mới publish — không thì frame tới trước lúc SUBSCRIBE được xử lý và trông y hệt một lỗi thật (đã mất công vì đúng chỗ này).

---

## 6.2 Phase 9 — frontend (đã làm)

`Frontend/src/features/liveroom/` (74 file) + route `(dashboard)/dashboard/liveroom/*` và route group full-screen `(liveroom)/liveroom/[roomId]`.

**Ba lỗi chỉ lộ ra khi chạy client thật** — đúng như kinh nghiệm các phase trước:

1. **Hàng chờ duyệt không tự xoá sau khi owner bấm Cho vào.** `REQUEST_APPROVED` chỉ gửi cho người xin vào (§2.6), owner không bao giờ nhận nên store không có gì để xoá. Owner phải tự gỡ dòng đó trong `onSuccess` của mutation — xem `removeJoinRequest` trong store.
2. **`myUserId` null lúc mount.** `RoomScreen` đọc user từ auth store; khi F5, token được khôi phục trước khi object `user` có, nên store bị `reset` với id rỗng và mọi so sánh "cái này của tôi" (owner, tile của mình, echo chat của mình) đều sai. Màn phòng giờ **chờ tới khi biết user** rồi mới mở phiên.
3. **Nhạc mất sau F5.** Backend chỉ đẩy bài đang phát cho người nó vừa xếp chỗ; người đã ngồi trong phòng F5 sẽ nhận `LR_041` nên không được đẩy gì. `use-room-session` publish `music/get-state` ngay sau khi nạp snapshot.

**Môi trường**: `node_modules` của Frontend còn symlink trỏ về `frontend` viết thường (di sản commit `69a42ee` đổi tên thư mục). Node giải quyết được vì Windows không phân biệt hoa thường, nhưng **Turbopack thì không** — cả `next dev` lẫn `next build` chết với "inferred your workspace root ... couldn't find the Next.js package". Khắc phục: xoá `node_modules` và `pnpm install` lại. Đặt `turbopack.root` **không** giải quyết được.

---

## 6.3 Lượt vá WebRTC sau khi test hai máy (2026-08-05)

Test nhanh 2 tài khoản lộ ra: (a) guest chọn bật cam/mic ở pre-join nhưng vào phòng thì tắt hết, (b) hai bên không thấy/nghe được nhau. Truy ra 5 nguyên nhân gốc, tất cả nằm ở frontend.

1. **`ontrack` không bao giờ có stream.** `PeerConnectionManager` gắn track bằng `addTransceiver(kind)` rồi `replaceTrack()`. `replaceTrack` **không** gắn track vào MediaStream nào, nên SDP không có `msid` và `RTCTrackEvent.streams` rỗng ở đầu kia — `onRemoteStream` không bao giờ chạy. Đây là lỗi đủ để giết cả hình lẫn tiếng kể cả khi signaling chạy đúng. Giờ manager tự dựng một `MediaStream` cho mỗi peer và `addTrack(event.track)` vào đó.
2. **Không ai gọi ai.** Người mới chỉ gọi những participant có `joinedAt` **muộn hơn** mình — với người mới thì tập đó luôn rỗng. Chiều còn lại (người đang ở trong phòng gọi người mới qua `PARTICIPANT_JOINED`) thì bắn offer ngay khi `joinRoom` trả về, tức là **trước** khi mesh của người mới kịp dựng (`phase === "ready"` phải chờ xong cả snapshot), nên offer rơi mất và không có gì phát lại. Đổi quy ước: **ai vào sau thì gọi** (so `joinedAt`, hoà thì so `userId`), và gọi từ snapshot của chính mình — lúc đó manager chắc chắn đã tồn tại. Bên vào trước chỉ ngồi chờ offer.
3. **Không có đường hồi phục.** `restartIce()` được gọi nhưng không ai gửi offer mới nên ICE restart không bao giờ xảy ra. Giờ có `onnegotiationneeded` (chỉ bên chủ động mới bắn), perfect-negotiation cho glare, hàng đợi ICE cho candidate tới trước peer, và vòng heal 5s: peer nào đã gửi offer quá 8s mà chưa `connected` thì dựng lại, tối đa 3 lần.
4. **Tiếng chỉ nằm trên thẻ `<video>` bị `hidden` khi tắt cam.** Mỗi tile giờ có thẻ `<audio>` riêng cho peer, kèm retry `play()` ở cú click tiếp theo phòng khi autoplay bị chặn.
5. **Lựa chọn cam/mic ở pre-join bị vứt.** `JoinFlow` giữ `useLocalMedia` riêng, submit là `stopAll()`, rồi `RoomScreen` mount một `useLocalMedia` mới mặc định tắt. Giờ ghi ý định vào sessionStorage (`liveroom:mediaIntent:<roomId>`, xem `liveroom-storage.ts`) và `RoomScreen` áp dụng đúng một lần khi `phase === "ready"`, có PATCH lại media state cho phòng biết.

Tiện thể: owner mute người khác thì trước đây chỉ **khoá nút** chứ track vẫn phát — `RoomScreen` giờ tắt mic thật khi `micState === "MUTED_BY_OWNER"`.

Backend signaling không phải sửa gì: principal là `userId`, `/user/queue/liveroom/rtc` và `RelayRtcSignalUseCase` đều đúng.

### 6.3.1 Nguyên nhân thứ 6 — bên trả lời không bao giờ gửi media (đo bằng probe)

Sau lượt vá trên, test lại vẫn thấy: host hiện hình guest bình thường, guest chỉ thấy avatar của host, hai bên không nghe nhau, mà `connectionState` cả hai đều `connected`. Dựng hai `RTCPeerConnection` trong một trang trắng, tái hiện đúng trình tự code (track tổng hợp bằng `canvas.captureStream` + `AudioContext`, không cần webcam) thì ra ngay:

> `addTransceiver()` gọi **trước** `setRemoteDescription(offer)` ở bên trả lời **không** được ghép vào m-line của offer.

Chrome tạo transceiver `recvonly` **mới** cho từng m-line của offer, còn hai transceiver mang track của mình thì mồ côi (`mid: null`, `currentDirection: null`). Bên trả lời kết thúc với **4** transceiver, answer là `a=recvonly` cả audio lẫn video → **không gửi gì**, `ontrack` bên kia không bao giờ chạy. Chiều ngược lại vẫn chạy vì bên gọi tự sinh m-line từ transceiver của nó. Đúng y triệu chứng.

Sửa: bên trả lời **không** dựng transceiver trước. `setRemoteDescription(offer)` trước đã, rồi mới nhận lấy transceiver mà nó vừa tạo — ép `direction = "sendrecv"` và `replaceTrack()` vào sender — xong mới `setLocalDescription()`. Bên gọi vẫn `addTransceiver` như cũ. Cả hai vai dùng chung một hàm `applyLocalTracks(pc, create)`; `create` chỉ bật cho bên gọi để không đẻ thêm m-line mà answer không mang được.

Đo lại sau khi sửa: answer `sendrecv/sendrecv`, mỗi bên đúng 2 transceiver, byte chạy cả hai chiều cho cả audio lẫn video, bật/tắt cam sau đó **không** phát sinh renegotiation.

### 6.3.2 Còn "mic không nghe" — đã đo hết đường ống, code sạch

Sau khi hình chạy hai chiều, tiếng vẫn không nghe. Đo bằng probe từng khúc một, **không tìm thấy lỗi code nào nữa**:

| Khúc | Cách đo | Kết quả |
| ---- | ------- | ------- |
| Transport | hai `RTCPeerConnection`, `getStats()` | byte audio chạy cả hai chiều, `totalAudioEnergy` 9.06 |
| Phát lại | `<audio>` nhận stream gộp / stream chỉ audio / `<video>` không mute, đo bằng `captureStream()` + `AnalyserNode` | cả ba đều `audible`, `peakSpectrum` 255 |
| Manager thật | biên dịch `peer-connection-manager.ts` rồi cho hai instance nói chuyện đúng thứ tự thật (host bật cam+mic trước, guest gọi rồi mới gắn track) | hai bên nhận đủ `audio+video`, track `live`, `muted=false` |

Nghĩa là khúc duy nhất chưa quan sát được là **mic có thu được gì không** — và đúng chỗ đó app đang mù hoàn toàn:

1. **Lỗi thiết bị trong phòng bị nuốt sạch.** `DevicePermissionNotice` chỉ có ở pre-join. `getUserMedia` fail trong phòng (hay gặp nhất: `NotReadableError` khi hai profile Chrome trên cùng máy giành một mic) thì `enableMic()` trả `false`, `media.error` được set và **không hiển thị ở đâu cả** — nút mic lặng lẽ không bật lên. Giờ có toast dùng lại các key `liveroom.prejoin.*`.
2. **Không có chỉ báo mức âm.** Key `prejoin.micPreview` và `room.video.permissionBanner` đã nằm sẵn trong file dịch từ đầu nhưng chưa ai dựng. Giờ mỗi tile có meter 4 vạch + viền xanh khi đang phát ra tiếng (`use-audio-level.ts`), chạy cho cả tile của mình lẫn tile người khác. Đây vừa là tính năng vừa là thứ trả lời được câu "tiếng có tới không" mà không cần mở `webrtc-internals`.

Test tiếp theo cho kết quả quyết định: Chrome báo **"Microphone: Using now"** mà meter đứng yên ⇒ track mic sống nhưng **trả về im lặng**, và đó đúng là thứ peer kia nhận được. Không phải lỗi đường ống. Hai thủ phạm ngoài code, cùng đến từ việc test hai profile Chrome trên **một máy**: hai process giành một mic (process thứ hai nhận silence, tuỳ driver shared/exclusive mode), và AEC — hai cửa sổ loopback độ trễ thấp làm tín hiệu tham chiếu trùng gần khớp với tiếng nói trực tiếp nên bị trừ gần hết. Ảnh cũng cho thấy tile `pro1` đang là **mic gạch chéo**, tức host chưa bật mic — chiều đó không có gì để nghe ngay từ đầu.

Nên phần bổ sung tập trung vào việc làm tình trạng này **nhìn thấy được**, thay vì vá mù:
- Meter đo đúng cái track sẽ được gửi đi (sau AEC), nên "vạch không nhảy" = "peer sẽ không nghe thấy gì". Đây là câu trả lời trực tiếp, không cần mở `webrtc-internals`.
- Banner cảnh báo khi mic bật mà im lặng quá 6s, kèm `audioTrack.label` để lộ ngay trường hợp Chrome chọn nhầm thiết bị đầu vào.
- Meter cũng được gắn vào pre-join (key `micPreview` có sẵn từ đầu) — chỗ test mic **một cửa sổ, tách biệt**, trước khi vào phòng.

Lưu ý khi đọc `use-audio-level.ts`: sự kiện `addtrack` **không** phát khi gọi `stream.addTrack()` từ script (spec chỉ phát khi user agent tự thêm) — mà cả stream preview local lẫn `remoteStream` của peer đều được thêm track bằng script. Nên hook dò track theo nhịp 100ms chứ không nghe event. Đã đo: chưa có track → 0, script thêm track → đầy vạch, `track.enabled = false` → 0, bật lại → đầy vạch, gỡ track → 0.

Probe cũng lộ hai thứ nữa, đã tính vào code:
- `RTCTrackEvent.streams` **luôn rỗng** khi gắn track bằng `replaceTrack` (xác nhận lỗi số 1 ở trên) — bắt buộc phải tự dựng `MediaStream` cho mỗi peer.
- `replaceTrack(null)` lúc tắt cam **không** làm track phía kia `mute`, nên chỉ nghe track là không đủ: tile đứng hình frame cuối. Điều kiện hiện hình phải là `videoActive && participant.cameraOn` — track lo "media đã chạy chưa", server lo "người ta có bật cam không".

---

## 6.4 Lượt vá trước khi deploy 1 VPS (2026-08-05)

Bốn thứ chỉ vỡ khi rời localhost. Kèm quyết định bỏ MinIO.

### 6.4.1 Giới hạn bitrate cho sender

Mesh nghĩa là mỗi client **upload N bản sao** luồng của mình, mà trước đó không có `setParameters` ở đâu cả — `VIDEO_CONSTRAINTS` cho tới 720p. Phòng 5 người là 4 luồng đồng thời, cần 6–10 Mbps đường lên; nghẽn thì **cả phòng** giật chứ không riêng người yếu mạng. Localhost không bao giờ lộ vì loopback coi như vô hạn băng thông.

`rebalanceEncodings()` chia ngân sách `VIDEO_BUDGET_BPS` (1.2 Mbps) cho số peer, kẹp trong [120k, 600k], và hạ `scaleResolutionDownBy` khi bitrate xuống thấp. Gọi lại mỗi khi số peer đổi.

Đo bằng probe (canvas nhiễu ngẫu nhiên — trường hợp xấu nhất cho encoder): đặt cap 100 kbps → đo 91; nới 1.5 Mbps → 409; siết 150 kbps + scale 2 → 119 kbps và độ phân giải tự tụt 320×180 → 160×90.

**Bẫy đã dính:** đặt `setParameters` **trước** `setLocalDescription()` ở bên trả lời thì Chrome từ chối im lặng (transceiver do `setRemoteDescription` tạo ra chưa có encoding), và `.catch()` nuốt mất. Probe cho thấy bên trả lời không nhận cap nào trong khi bên gọi thì có. Phải gọi `rebalanceEncodings()` **sau** `setLocalDescription()` / `setRemoteDescription(answer)`, cộng thêm một lần khi `connectionState === "connected"`. Sau khi sửa: tổng video của host với 2 peer là 951 kbps, trước đó 1632.

### 6.4.2 Rate limit cho STOMP

`HttpRateLimitFilter` là servlet filter, **không chạm WebSocket**. Chat gửi qua STOMP đi thẳng vào DB không qua hạn mức nào — deploy public là mở sẵn cửa spam.

`StompRateLimitInterceptor` chia ba rổ theo destination vì lưu lượng khác nhau hẳn: `rtc` 400 frame/10s (ICE candidate vốn bùng nổ, nhất là khi có TURN), `chat` 15, còn lại 60. Hai lựa chọn thiết kế:

- **Thả frame (`return null`) chứ không ném exception.** `StompSubscriptionScopeInterceptor` ném `MessageDeliveryException` và việc đó **đóng luôn kết nối** — đúng với vi phạm phân quyền, nhưng quá tay với rate limit: một cú bùng phát hợp lệ sẽ đá người dùng ra khỏi phòng.
- **Cảnh báo tối đa một lần mỗi cửa sổ.** Báo cho từng frame bị thả sẽ tự khuếch đại thành chính cái spam mình đang chặn.

Dọn theo `SessionDisconnectEvent`, nếu không map rò theo từng phiên.

### 6.4.3 Cache membership cho relay RTC

`RelayRtcSignalUseCaseImpl` mở transaction + 3 query cho **mỗi** ICE candidate. Localhost chỉ có host candidate nên vài cái; có STUN/TURN thật thì mỗi peer sinh host + srflx + relay cho từng interface, phòng 5 người vào cùng lúc là hàng trăm transaction dồn trong vài giây.

`RtcRelayGuard` cache **kết luận** theo bộ ba `(roomId, actor, target)` với TTL 5s — đúng bằng thứ mà mọi candidate trong một đợt bùng phát kiểm tra đi kiểm tra lại. Một cặp peer chỉ còn 1–2 lần chạm DB thay vì vài trăm.

Hai điểm phải để ý:

- **`@Transactional` phải nằm ở đường trượt cache, không nằm ở usecase.** Để nguyên `@Transactional` trên usecase thì transaction vẫn mở mỗi frame kể cả khi cache trúng — mất sạch ý nghĩa. Nên usecase bỏ `@Transactional`, còn `RoomSessions.requireRelayAllowed` mới mang nó (bean khác nên proxy mới ăn).
- **Không cần hook eviction.** Người bị kick bị `RealtimeSessionEvictor` đóng phiên nên không gửi được nữa; còn tín hiệu lỡ relay tới người vừa rời trong 5s thì bên nhận không có peer, `handleIce` xếp vào hàng đợi mồ côi (giới hạn 30) rồi thôi. Đổi lấy sự đơn giản.

Bỏ `@Transactional` khỏi usecase còn có lợi phụ: `StompLiveroomEventPublisherAdapter.afterCommit` thấy không có transaction thì gửi ngay, không phải chờ commit.

### 6.4.4 Bỏ MinIO, dùng AWS S3 thật

Hoá ra `Backend/.env` đã để `STORAGE_S3_ENDPOINT=` rỗng từ trước — tức đang trỏ AWS thật rồi, container MinIO chạy không mà không ai dùng. Lớp storage vốn dựng trên AWS SDK, MinIO chỉ là `endpointOverride`. Nên chỉ là dọn: gỡ service + volume khỏi compose, xoá `MINIO_ROOT_*` khỏi hai file `.env`, sửa ghi chú ở `shared-development-standards.md` và comment trong `next.config.ts`.

Frontend không phải đụng: `next.config.ts` đã tự dựng origin `https://{bucket}.s3.{region}.amazonaws.com` cho `connect-src`/`media-src` từ `NEXT_PUBLIC_STORAGE_BUCKET_NAME` + `NEXT_PUBLIC_STORAGE_REGION`.

**Còn sót có chủ ý:** `docs/test/SHARED_TEST_PLAN.md` và `TEST_GENERATION_RULES.md` vẫn đề xuất Testcontainers MinIO cho integration test. Đó là S3 giả dùng trong test chứ không phải hạ tầng phải dựng, và các test đó **chưa tồn tại** — nên chưa sửa, để quyết khi nào thật sự viết test.

---

## 7. Bắt đầu một phiên mới thế nào

Backend đã xong hết và đã qua một lượt vá (mục 6); còn lại là Phase 9 (frontend, mục 4). Trước khi viết code nên:

1. Đọc mục **2 (quy ước đã chốt)** — quan trọng nhất, tránh "sửa" những thứ cố ý.
2. Đọc phần nghiệp vụ tương ứng trong `liveroom-business-requirements.md`.
   Tài liệu đó **cũ hơn code** ở vài chỗ — mục 2 mới là thứ đang chạy.
3. Xem 2–3 file cùng loại đã có để bắt khuôn (đặt tên, tách lớp). **Không viết comment** (§2.22).
4. Làm xong một mảng thì dừng lại để review, đừng gộp nhiều mảng.
