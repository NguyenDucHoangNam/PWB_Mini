# Shared Music trong Live Room

Tài liệu này mô tả logic nghiệp vụ của tính năng nghe nhạc chung trong phòng liveroom. Mục tiêu là để các thành viên trong phòng live cùng nghe một bài hát và cùng điều khiển Play/Pause, đồng thời vẫn tôn trọng quyền sở hữu bài hát cá nhân của từng người dùng.

---

## 1. Bối cảnh

Hệ thống hiện có hai module liên quan:

- **Voice**: quản lý bài hát (Song) thuộc sở hữu của từng user. Mỗi bài hát có trạng thái xử lý riêng và có quy trình phát hành, cập nhật, xóa riêng theo user sở hữu.
- **LiveRoom**: quản lý phòng live, thành viên, sự kiện realtime, điều khiển mic/camera, duyệt yêu cầu vào phòng. Phòng có host, danh sách participant và luồng realtime qua STOMP.

Tính năng Shared Music bắc cầu giữa hai module. Phòng live cho phép một participant chọn bài hát của chính họ để phát cho cả phòng cùng nghe. Mọi participant đang hoạt động trong phòng đều có quyền điều khiển Play/Pause bài đang được chia sẻ.

---

## 2. Khái niệm cốt lõi

### 2.1. Quyền sở hữu bài hát (Song Ownership)

- Bài hát là tài sản riêng của user đã upload.
- Chỉ owner được xem, chỉnh sửa, xóa và chọn bài hát vào bất kỳ phòng live nào.
- Việc chọn bài vào phòng không thay đổi chủ sở hữu, không làm bài hát trở thành công khai và không cho phép người ngoài phòng truy cập bài.

### 2.2. Trạng thái phát nhạc của phòng (Room Playback State)

- Mỗi phòng có đúng một bài hát hiện tại (current song) hoặc không có bài hát nào.
- Trạng thái phát nhạc thuộc về phòng, không thuộc về từng participant.
- Trạng thái gồm: bài hiện tại, vị trí phát, trạng thái phát (trống, đang phát, đang tạm dừng, kết thúc), người vừa thay đổi và phiên bản (version).

### 2.3. Lệnh điều khiển và nguồn sự thật

- Backend là nguồn sự thật duy nhất về trạng thái phát nhạc.
- Client chỉ gửi ý định (Play, Pause, Chọn bài) và phản ánh lại trạng thái backend gửi về.
- Mỗi thay đổi được đánh số phiên bản để các client bỏ qua event cũ và đồng bộ về cùng một mốc.

---

## 3. Các quyền trong phòng

Phân biệt rõ ba loại quyền để tránh hiểu nhầm khi triển khai.

### 3.1. Quyền chọn bài (Select Song)

Một participant có thể chọn bài vào phòng khi thỏa đồng thời các điều kiện:

- Là participant đang hoạt động trong phòng.
- Bài hát thuộc sở hữu của chính participant đó.
- Bài hát chưa bị xóa và đang trong trạng thái có thể phát.

Không ai được chọn bài của người khác. Việc kiểm tra sở hữu phải do backend thực hiện; client không được tự xác nhận.

### 3.2. Quyền điều khiển phát (Playback Control)

Mọi participant đang hoạt động trong phòng đều có thể:

- Bấm Play để cả phòng cùng phát.
- Bấm Pause để cả phòng cùng tạm dừng.

Participant không có quyền đổi bài của người khác vì không sở hữu bài đó. Nhưng một participant có thể thay bài hiện tại bằng bài của chính họ.

Ở phiên bản đầu, khuyến nghị chưa cho phép tua bài (seek). Nếu cần tua, người tua sẽ gửi lệnh tương tự Play/Pause và toàn phòng tua theo để giữ đồng bộ.

### 3.3. Quyền nghe (Listen)

- Nghe bài hát trong thư viện cá nhân: chỉ owner.
- Nghe bài hát đang được chia sẻ trong phòng: mọi active participant.

Quyền nghe trong phòng có phạm vi giới hạn: chỉ áp dụng cho bài hiện đang được phòng phát. Khi bài đó bị gỡ khỏi phòng hoặc phòng kết thúc, quyền nghe cũng hết hiệu lực.

---

## 4. Vòng đời của một bài hát trong phòng

### 4.1. Trạng thái rỗng (Empty)

- Phòng vừa được tạo hoặc vừa kết thúc bài trước.
- Không có bài hiện tại.
- Không có audio nào đang phát.
- Bất kỳ participant nào có bài hát đều có thể chọn bài để bắt đầu.

### 4.2. Chọn bài (Selected)

- Một participant chọn một bài thuộc sở hữu của họ.
- Bài mới được đặt làm bài hiện tại của phòng.
- Phòng chuyển sang trạng thái tạm dừng tại giây 0.
- Mọi client trong phòng tải metadata bài và nhận URL nghe.
- Không tự động phát nhạc ngay khi chọn bài, để tránh bật âm thanh đột ngột.

### 4.3. Đang phát (Playing)

- Có một bài hiện tại.
- Trạng thái phát nhạc đang ở chế độ đang phát.
- Mọi client duy trì phát theo một mốc thời gian chung do backend cung cấp.
- Tín hiệu đồng bộ phát sinh khi bất kỳ ai bấm Play hoặc Pause.

### 4.4. Tạm dừng (Paused)

- Bài vẫn là bài hiện tại nhưng không phát.
- Người dùng giữ nguyên vị trí phát cho đến khi có lệnh Play.

### 4.5. Kết thúc bài (Ended)

- Bài chạy hết thời lượng.
- Backend chuyển sang trạng thái kết thúc bài và vẫn giữ bài hiện tại.
- Phòng không tự chuyển sang bài khác.

### 4.6. Gỡ bài (Cleared)

- Bài bị gỡ vì owner rời phòng và yêu cầu gỡ, hoặc vì bài không còn khả dụng, hoặc vì phòng kết thúc.
- Phòng quay về trạng thái rỗng.

---

## 5. Quy tắc đồng bộ Play/Pause

### 5.1. Vì sao cần mốc thời gian chung

Mỗi client có độ trễ mạng khác nhau và nhận event lệch nhau vài chục đến vài trăm mili giây. Nếu chỉ broadcast lệnh "Play", các client sẽ bắt đầu phát tại các thời điểm khác nhau và lệch dần theo thời gian.

### 5.2. Trạng thái có thẩm quyền

Mỗi lần thay đổi phát nhạc, backend phát ra một trạng thái hoàn chỉnh với các thành phần:

- Bài hiện tại hoặc không có.
- Trạng thái phát.
- Vị trí phát tại thời điểm phát đi.
- Mốc thời gian hiệu lực do server đặt.
- Phiên bản tăng dần theo mỗi lần thay đổi.
- Người vừa phát sinh thay đổi.

Client khi nhận sẽ:

- Bỏ qua event có phiên bản nhỏ hơn trạng thái đang giữ.
- Đặt vị trí audio theo vị trí server cung cấp.
- Nếu đang phát: bù thêm thời gian trôi qua kể từ mốc hiệu lực rồi mới phát.
- Nếu đang tạm dừng: dừng audio tại vị trí chính xác.
- Nếu lệch vượt ngưỡng cho phép (ví dụ nửa giây đến một giây) thì tự đồng bộ lại.

Nhờ đó toàn phòng luôn nghe cùng một đoạn nhạc tại cùng một thời điểm.

### 5.3. Xử lý nhiều lệnh đồng thời

Vì cả phòng được Play/Pause nên có thể có hai người bấm gần như cùng lúc. Backend xử lý tuần tự bằng cách khóa logic trên phòng và đánh số phiên bản. Lệnh tới sau cùng sẽ là trạng thái cuối cùng. Client nên debounce nút khoảng vài trăm mili giây để giảm nhiễu, đồng thời backend áp dụng giới hạn tần suất theo user và theo phòng.

---

## 6. Sự kiện realtime cần có

Các sự kiện được phát qua kênh realtime nội bộ của phòng. Mô tả theo hướng logic, không phụ thuộc giao thức cụ thể.

### 6.1. Sự kiện khi phòng chọn bài

- Phát sinh khi một participant chọn bài của chính họ.
- Nội dung tối thiểu: mã phòng, thông tin bài hát (id, owner, tiêu đề, nghệ sĩ, thời lượng), trạng thái khởi đầu, người chọn, phiên bản.
- Hành vi phía client: tải metadata, lấy URL nghe, đặt vị trí về 0, dừng phát.

### 6.2. Sự kiện khi trạng thái phát thay đổi

- Phát sinh khi bất kỳ ai bấm Play hoặc Pause.
- Nội dung: trạng thái mới, vị trí, mốc hiệu lực, phiên bản, người phát sinh.
- Hành vi phía client: đồng bộ audio theo trạng thái mới.

### 6.3. Sự kiện khi bài bị gỡ

- Phát sinh khi phòng kết thúc, owner chủ động gỡ hoặc bài không còn khả dụng.
- Nội dung: lý do gỡ, mã phòng, mốc thời gian.
- Hành vi phía client: dừng audio, xóa bài hiện tại, đóng trình phát.

### 6.4. Trạng thái hiện tại gửi riêng cho một người

- Gửi cho người vừa vào phòng.
- Gửi khi client yêu cầu đồng bộ lại, ví dụ sau khi reconnect.
- Nội dung: snapshot trạng thái phát nhạc của phòng.

---

## 7. Truy cập bài hát trong phòng

Việc cấp quyền nghe phải được tách rõ khỏi quyền nghe cá nhân.

### 7.1. Nghe bài trong thư viện cá nhân

- Chỉ chủ sở hữu bài hát mới được phép.
- Hệ thống giữ nguyên quy tắc hiện tại.

### 7.2. Nghe bài trong phòng live

- Áp dụng cho bài hiện đang được phòng phát.
- Người yêu cầu phải là active participant trong đúng phòng.
- Phòng phải đang hoạt động.
- Bài được yêu cầu phải đúng là bài hiện tại của phòng.
- Người gọi không nhất thiết là chủ sở hữu bài. Chủ sở hữu có thể không có mặt trong phòng.

URL nghe phải có thời hạn. Nếu thời lượng phát nhạc dài hơn thời hạn URL, hệ thống phải có cơ chế cấp lại mà không làm gián đoạn người nghe.

URL nghe không bao giờ được broadcast lên kênh chung của phòng. Kênh chung chỉ mang metadata và mã bài hát; mỗi client tự gọi một endpoint được bảo vệ để nhận URL riêng có thời hạn.

---

## 8. Quy tắc biên quan trọng

### 8.1. Owner rời phòng

Bài đang phát nên tiếp tục. Không nên cắt nhạc đột ngột khi owner rời. Khi owner rời, có hai lựa chọn:

- Giữ bài cho đến khi bài kết thúc hoặc một participant khác chọn bài mới của họ.
- Gỡ bài ngay khi owner rời.

Mặc định khuyến nghị là giữ bài. Nếu muốn rời nhưng gỡ bài, owner phải chủ động chọn gỡ trước khi rời.

### 8.2. Khi có người chọn bài mới

- Bài hiện tại dừng.
- Bài mới trở thành bài hiện tại ở trạng thái tạm dừng tại giây 0.
- Không tự động phát.
- Mọi participant sau đó có thể bấm Play.

### 8.3. Khi bài chạy hết

- Backend chuyển trạng thái sang kết thúc.
- Không tự chuyển sang bài khác.
- Không tự lặp trừ khi có cấu hình cho phép.

### 8.4. Khi người mới vào phòng

- Nhận metadata bài hiện tại.
- Nhận trạng thái phát và vị trí hiện tại.
- Lấy URL nghe qua endpoint được bảo vệ.
- Đồng bộ vị trí phát với phòng.

### 8.5. Khi reconnect hoặc mất kết nối

- Client không dùng state cũ trong bộ nhớ.
- Client yêu cầu backend gửi lại trạng thái hiện tại.
- Khi nhận được, client đặt lại vị trí và trạng thái.

---

## 9. An toàn và bảo mật

### 9.1. Định danh người dùng

- Mọi quyết định về quyền phải dựa vào định danh lấy từ phiên đăng nhập, không tin dữ liệu gửi từ client.
- Không tin người gửi tự nhận là owner. Ownership phải đối chiếu với module quản lý bài hát.

### 9.2. Đăng ký kênh phòng

- Khi đăng ký các kênh liên quan đến phòng, người đăng ký phải đang là active participant trong đúng phòng.
- Nếu hệ thống có kênh chat hoặc kênh khác trong tương lai, quy tắc này nên được áp dụng nhất quán.

### 9.3. Tần suất thao tác

- Backend nên giới hạn tần suất phát sinh lệnh Play/Pause/CHọn bài theo từng user và từng phòng để tránh spam.
- Việc giới hạn này không được ảnh hưởng đến khả năng khôi phục trạng thái khi reconnect.

### 9.4. Lỗi phát nhạc

- Khi backend gặp lỗi không thể cấp URL hoặc lỗi truy cập bài, cần phát thông báo về phòng để mọi client cập nhật lại.
- Bài hát không khả dụng nên được gỡ khỏi phòng thay vì để trạng thái treo.

---

## 10. Tổng kết logic nghiệp vụ

- Bài hát vẫn thuộc riêng từng người. Không ai có quyền sở hữu bài của người khác ngoài scope phòng.
- Chỉ chủ sở hữu được đưa một bài vào phòng để nghe chung.
- Mọi active participant đều có quyền Play và Pause bài hiện tại.
- Không ai được chọn bài của người khác. Người dùng có thể thay bài hiện tại bằng bài của chính họ.
- Trạng thái phát nhạc thuộc về phòng, do backend quản lý và là nguồn sự thật duy nhất.
- Quyền nghe bài của người khác chỉ tồn tại trong phòng và trong thời gian bài đó đang được chia sẻ.
- Việc chia sẻ vào phòng không chuyển quyền sở hữu và không làm bài hát trở thành công khai.
- Khi owner rời phòng hoặc bài không còn khả dụng, quyền nghe của các participant khác cũng kết thúc theo.

---

## 11. Trạng thái triển khai thực tế (Phase 1 → Phase 5)

Phần này mô tả những gì đã chốt và đã code trong codebase.

### 11.1. Phase 1 — Host-first MVP

- Host chọn bài của chính host. Quyền chọn dùng `SongFacade.getSong` để verify `song.userId == caller.userId`.
- Bài bắt đầu ở `PAUSED` tại giây 0; mọi participant được Play/Pause.
- Trạng thái phát server-authoritative, broadcast qua STOMP `/topic/room/{code}/playback` và `/queue/room/{code}/playback/state`.
- Migration `V20__create_live_room_playback.sql`.

### 11.2. Phase 2 — Endpoint stream + mở quyền chọn cho participant

- Endpoint mới `GET /live-rooms/{roomCode}/playback/songs/{songId}/stream` cấp URL presigned có TTL (mặc định 1 giờ).
- Quyền chọn bài mở cho bất kỳ active participant nào (không chỉ host), với điều kiện song ownership.
- Frontend dùng endpoint này thay vì gọi trực tiếp `/songs/{id}/stream` của voice.

### 11.3. Phase 3 — Ổn định realtime

- Backend rate-limit WS playback bằng in-memory token bucket (capacity 10 / 10 giây / key user+room+action).
- Frontend debounce nút Play/Pause 250 ms; auto-reconcile khi tab visible trở lại.
- Drift threshold 1500 ms — khi drift vượt ngưỡng, hook tự động request lại snapshot.
- Toast cho `CLEARED` (chủ sở hữu gỡ bài) và `ENDED` (bài kết thúc tự nhiên).

### 11.4. Phase 4 — Bảo mật & quan sát

- `StompAuthInterceptor` chặn SUBSCRIBE tới `/topic/room/{code}/playback*` và `/queue/room/{code}/playback/state` khi user không phải active participant của phòng đó.
- CORS WS được config qua `app.liveroom.ws.allowed-origins` (env `LIVEROOM_WS_ALLOWED_ORIGINS`). Mặc định dev: `http://localhost:3000,http://localhost:8080`.
- Audit log với prefix `AUDIT playback.{action}` cho `selectSong`, `play`, `pause`, `seek`, `setRate`, `setLoop`, `setShuffle`.
- In-memory metrics counters: `playback.transition.{select,play,pause,seek,rate,loop,shuffle}`, `playback.stream.request`.

### 11.5. Phase 5 — Tua, tốc độ, loop, shuffle

- Migration `V21__extend_live_room_playback.sql` thêm `playback_rate`, `loop_mode`, `shuffle_enabled`.
- Endpoint WS mới: `playback/seek`, `playback/rate`, `playback/loop`, `playback/shuffle`.
- Tốc độ hợp lệ: `1.00`, `1.50`, `2.00` (validate server-side, error `LIVEROOM_034`).
- Loop hợp lệ: `OFF`, `ONE` (validate server-side, error `LIVEROOM_035`).
- Tua là ±30 giây một lần, server-authoritative.
- **Chưa hỗ trợ**: shuffle thực sự tự động chuyển bài (cần playlist table — Phase sau).

---

## 12. Runbook vận hành

### 12.1. Khi participant không nghe được

| Triệu chứng | Kiểm tra nhanh | Cách xử lý |
|---|---|---|
| Không load được audio | Network tab: response `/live-rooms/{code}/playback/songs/{id}/stream` | Nếu 401/403, refresh page để reconnect STOMP và gọi lại. Nếu 410/4xx, bài bị thu hồi — chọn bài khác. |
| Nghe lệch so với phòng | Position hiển thị khác | Bấm nút "Re-sync" (icon X màu vàng) để gọi `playback/state/request`. |
| URL hết hạn | Log console: `403 Forbidden` từ S3 | Frontend tự refresh qua React Query (TTL mặc định 1 giờ). Nếu vẫn lỗi, bấm chọn lại bài. |

### 12.2. Khi đồng hồ lệch

- Hook `use-shared-playback` tính `driftMs = |nowMs - effectiveAt|`. Nếu `> 1500ms`, set `needsReconcile = true` và người dùng được mời bấm nút "Re-sync".
- Nếu nhiều user cùng báo lệch đồng hồ → kiểm tra NTP của server backend (`date` command).

### 12.3. Khi spam lệnh

- Backend rate-limit đã có (10 lệnh / 10s / user+room+action). Khi vượt ngưỡng, log `Playback {action} rate-limited: roomCode={...}, userId={...}` ở level WARN.
- Nếu cần điều tra user cụ thể, tìm log `AUDIT playback.{action} roomCode={code} userId={id}` để biết user nào phát lệnh.

### 12.4. Khi có lỗi backend

- Các error code chính:
  - `LIVEROOM_030` — phòng chưa có bài.
  - `LIVEROOM_031` — không phải host, không được chọn bài (Phase 1).
  - `LIVEROOM_032` — chọn bài không phải của mình.
  - `LIVEROOM_033` — phòng không còn active.
  - `LIVEROOM_034` — playback rate không hợp lệ.
  - `LIVEROOM_035` — loop mode không hợp lệ.
- Mapping FE: `frontend/src/lib/error-code-to-i18n.ts` (key dạng `LIVEROOM_0xx` → `liveroom.errors.*`).

---

## 13. Tài liệu API & WS cho FE team

### 13.1. REST endpoint

| Method | Path | Mô tả |
|---|---|---|
| `GET` | `/api/v1/live-rooms/{roomCode}/playback` | Lấy snapshot hiện tại |
| `POST` | `/api/v1/live-rooms/{roomCode}/playback/songs` | Chọn bài (body: `{songId}`) |
| `GET` | `/api/v1/live-rooms/{roomCode}/playback/songs/{songId}/stream` | Lấy presigned URL stream |
| `POST` | `/api/v1/live-rooms/{roomCode}/playback/play` | REST play (WS khuyến nghị) |
| `POST` | `/api/v1/live-rooms/{roomCode}/playback/pause` | REST pause (WS khuyến nghị) |

### 13.2. WebSocket destination

| Direction | Destination | Payload |
|---|---|---|
| Client → Server | `/app/room/{code}/playback/play` | `{}` |
| Client → Server | `/app/room/{code}/playback/pause` | `{}` |
| Client → Server | `/app/room/{code}/playback/seek` | `{direction: 1 \| -1}` |
| Client → Server | `/app/room/{code}/playback/rate` | `{rate: "1.00" \| "1.50" \| "2.00"}` |
| Client → Server | `/app/room/{code}/playback/loop` | `{mode: "OFF" \| "ONE"}` |
| Client → Server | `/app/room/{code}/playback/shuffle` | `{enabled: boolean}` |
| Client → Server | `/app/room/{code}/playback/state/request` | `{}` |
| Server → Client (broadcast) | `/topic/room/{code}/playback` | `PLAYBACK_STATE_CHANGED` |
| Server → Client (private) | `/queue/room/{code}/playback/state` | `PLAYBACK_STATE` |

### 13.3. CORS WS

- Config qua env `LIVEROOM_WS_ALLOWED_ORIGINS` (comma-separated). Mặc định dev: `http://localhost:3000,http://localhost:8080`.
- KHÔNG dùng `*` trong production.

---

## 14. Câu hỏi mở (chưa giải quyết Phase sau)

- Có cần playlist do host quản lý trước (queue bài trước khi phát)? — Cần để hỗ trợ `LOOP_ROOM` và shuffle thực sự.
- Có cần hiển thị "đang phát bài này" trong dashboard liveroom ngoài phòng?
- Có cần lưu lịch sử bài đã phát trong phòng (cho replay/analytics)?
- Có cần nâng cấp rate-limit từ in-memory lên Redis khi chạy nhiều instance?
