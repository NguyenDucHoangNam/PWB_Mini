# Giai đoạn 4: Realtime Trọng Tâm — Module Live Room (STOMP & WebRTC)

> 🧭 **Điều hướng**: [⬅️ Quay lại Mục Lục Tổng Quan](../notes.md) | [⬅️ Giai đoạn 3: Module Audio](03-giai-doan-3-module-audio.md) | [Giai đoạn 5: Ops & Security ➡️](05-giai-doan-5-ops-security.md)

---

- **Domain**:
  - `LiveRoom.java` (`com.pwb.liveroom.domain.model`): Vòng đời phòng, mã phòng, cơ chế gia hạn khi chủ phòng vắng mặt.
  - `Participant.java` (`com.pwb.liveroom.domain.model`): Quản lý người tham gia và trạng thái mic/camera.
  - `PlaybackState.java` (`com.pwb.liveroom.domain.model`): Trạng thái phát nhạc gồm `positionSeconds`, `startedAt` (anchor timestamp) và `sequenceNumber`.
- **Hạ Tầng STOMP WebSocket (Backend)**:
  - `StompAuthChannelInterceptor.java` (`com.pwb.liveroom.infrastructure.realtime`): Xác thực JWT ngay khi client kết nối STOMP.
  - `StompSubscriptionScopeInterceptor.java` (`com.pwb.liveroom.infrastructure.realtime`): Chặn client subscribe trái phép vào topic của phòng.
  - `StompLiveroomEventPublisherAdapter.java` (`com.pwb.liveroom.infrastructure.realtime`): Broadcast sự kiện STOMP sau khi DB commit (`afterCommit`).
- **Điều Khiển Phát Nhạc & Signaling WebRTC**:
  - `MusicStompController.java` (`com.pwb.liveroom.api.realtime`) & `ControlPlaybackUseCaseImpl.java` (`com.pwb.liveroom.application.usecase.impl`): Nhận và xử lý lệnh play, pause, seek, volume.
  - `RtcStompController.java` (`com.pwb.liveroom.api.realtime`): Máy chủ Signaling relay Offer/Answer và ICE Candidate cho WebRTC.
- **Frontend Live Room Client**:
  - `liveroom-socket.ts` (`Frontend/src/features/liveroom/lib`): Quản lý kết nối STOMP client qua SockJS.
  - `server-clock.ts` (`Frontend/src/features/liveroom/lib`): Tính toán bù trừ độ lệch đồng hồ giữa server và client (`offsetMs`).
  - `use-liveroom-store.ts` (`Frontend/src/features/liveroom/stores`): Zustand store trung tâm lưu toàn bộ state của phòng nghe.
  - `use-playback-position.ts` (`Frontend/src/features/liveroom/hooks`): Web Audio API hook tự động tua bù lệch nhịp (drift compensation).
  - `peer-connection-manager.ts` (`Frontend/src/features/liveroom/lib`): Thiết lập mạng WebRTC Full-Mesh cho voice/video chat.
  - `room-screen.tsx` (`Frontend/src/features/liveroom/components/room`): Component màn hình phòng nghe trực tuyến hoàn chỉnh.

- **Chi Tiết Phần 1: Hạ Tầng Kết Nối STOMP WebSocket & Cơ Chế Bảo Mật Kênh (STOMP Transport & Channel Security)**:

  Khi phỏng vấn về việc vận hành hệ thống Realtime qua WebSocket/STOMP, các câu hỏi hóc búa nhất thường xoay quanh **rào cản bảo mật khi bắt tay (handshake authentication), tương thích môi trường mạng (proxy/firewall bypass), phân quyền kênh truy cập (channel authorization), tính nhất quán giao dịch (transactional event publishing) và khả năng chống chịu của client (reconnection resilience)**. Sếp có thể tự tin dẫn dắt qua **5 luận điểm kỹ thuật cốt lõi**:

  ---

  #### 1. Lý Thuyết STOMP Trên Nền WebSocket & Chiến Lược Xác Thực Trên Frame `CONNECT`

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **WebSocket là gì?**: Khác với HTTP truyền thống (Client gửi request thì Server mới trả lời response rồi đóng kết nối), WebSocket là giao thức mở ra một **đường ống hai chiều liên tục (Full-Duplex)** trên một kết nối TCP duy nhất. Cả Client và Server đều có thể chủ động bắn dữ liệu cho nhau bất kỳ lúc nào mà không cần ai hỏi trước.
    - **Tại sao có WebSocket rồi vẫn cần STOMP?**: 
      - WebSocket thuần chỉ giống như một **sợi dây đồng truyền tín hiệu thô** — nó chỉ truyền các chuỗi byte hoặc text vô định hình, không có cấu trúc. Nếu chỉ dùng WebSocket thuần, lập trình viên phải tự chế ra quy ước: tin nhắn này là chat hay lệnh tua nhạc? Gửi cho ai? Định dạng thế nào?
      - **STOMP (Simple Text Oriented Messaging Protocol)** là một giao thức tầng ứng dụng chạy đè lên trên WebSocket. Nó định nghĩa cấu trúc dữ liệu thành các **Frame (Khung thông điệp)** chuẩn mực y hệt như HTTP, gồm 3 phần:
        1. **Command**: Lệnh thao tác (`CONNECT`, `SUBSCRIBE`, `SEND`, `MESSAGE`, `DISCONNECT`).
        2. **Headers**: Các cặp key-value chứa metadata (như `destination: /topic/...`, `Authorization: Bearer ...`).
        3. **Body**: Nội dung dữ liệu thực tế (thường là chuỗi JSON).
    - **Rào cản Handshake của trình duyệt**: Để thiết lập WebSocket, trình duyệt ban đầu gửi một request HTTP bình thường kèm header `Upgrade: websocket` (gọi là **HTTP Handshake**). Tuy nhiên, theo tiêu chuẩn bảo mật W3C của các trình duyệt Web (Chrome, Firefox, Safari), hàm JavaScript `new WebSocket(url)` **hoàn toàn không cho phép lập trình viên thêm custom headers (như `Authorization: Bearer <JWT>`) vào request Handshake**.
  * **Vấn đề / Thách thức kỹ thuật**:
    - Nếu không thể truyền JWT trong request Handshake, làm sao hệ thống biết ai đang kết nối để xác thực?
    - Nếu chọn cách truyền token qua URL Query Parameter (ví dụ: `/ws?token=eyJhbG...`), token sẽ bị ghi thẳng vào Access Log của Nginx, lưu vết trong lịch sử trình duyệt và bị rò rỉ qua các proxy trung gian.
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Chiến lược bắt tay công khai (Public Handshake)**: 
      - Ta đưa endpoint `/ws/**` vào danh sách ngoại lệ bảo mật của Spring Security (`public-endpoints`). Quá trình bắt tay nâng cấp từ HTTP lên WebSocket diễn ra tự do mà không đòi hỏi Token. Nhưng lúc này, socket chỉ vừa được mở kết nối vật lý, hoàn toàn chưa được phép gửi/nhận bất kỳ dữ liệu nghiệp vụ nào.
    - **Chặn đầu tại cổng STOMP bằng Interceptor (`StompAuthChannelInterceptor`)**:
      - Ngay sau khi đường ống WebSocket thông suốt, Client bắt buộc phải gửi một frame STOMP đầu tiên mang lệnh: **`CONNECT`**.
      - Lúc này, vì STOMP hỗ trợ header tùy biến, Client sẽ đính kèm token vào native header: `Authorization: Bearer <JWT>`.
      - `StompAuthChannelInterceptor` đóng vai trò như một người gác cổng, chặn đứng frame `CONNECT` này lại, rút token ra, gọi `AccessTokenAuthenticator` để giải mã chữ ký và kiểm tra hạn sử dụng.
    - **Gán định danh phiên (`StompUserPrincipal`)**:
      - Sau khi xác thực thành công, interceptor tạo ra đối tượng `StompUserPrincipal` (chứa `userId`) và gán vào phiên kết nối (`accessor.setUser(...)`).
      - **Ý nghĩa sống còn**: Toàn bộ hệ thống phía sau (từ việc phân quyền vào phòng đến việc gửi tin nhắn riêng qua `/user/**`) đều dựa vào Principal này. Nếu token giả mạo hoặc hết hạn, Interceptor lập tức ném ngoại lệ `MessageDeliveryException("WS_UNAUTHENTICATED")`, Spring sẽ bắn trả một frame `ERROR` và **ngắt đứt socket ngay lập tức**.

  ---

  #### 2. Lý Thuyết Về Môi Trường Mạng Bị Chặn & Kỹ Thuật Đăng Ký Kép (WebSocket + SockJS)

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Mặt trái của WebSocket**: WebSocket sử dụng cổng và giao thức nâng cấp đặc thù. Trong thực tế, rất nhiều người dùng ngồi trong mạng công ty, trường học, bệnh viện hoặc sau các thiết bị tường lửa (Firewall) và proxy nghiêm ngặt sẽ **bị chặn đứng giao thức WebSocket**.
    - **SockJS là gì?**: SockJS là một giải pháp dự phòng (Fallback). Nó cung cấp một API giả lập WebSocket: nó sẽ thử kết nối WebSocket thuần trước; nếu bị mạng chặn, nó sẽ tự động hạ cấp xuống các kỹ thuật truyền thống chạy trên nền HTTP chuẩn mà không tường lửa nào chặn được (như **HTTP Long-Polling** hoặc **HTTP Streaming / XHR-Streaming**).
    - **Cơ chế xung đột đường dẫn trong Spring MVC**:
      - Khi ta cấu hình `registry.addEndpoint("/ws")`, Spring sẽ gán URL `/ws` cho bộ xử lý WebSocket thuần (`WebSocketHttpRequestHandler`).
      - Nếu ta gọi `registry.addEndpoint("/ws").withSockJS()`, Spring **không phải** chỉ bật thêm tính năng, mà nó sẽ **thay thế hoàn toàn** bộ xử lý gốc bằng `SockJsHttpRequestHandler`. Khi đó, các Client chuẩn không dùng thư viện SockJS (như Postman, ứng dụng Mobile iOS/Android) sẽ không thể kết nối trực tiếp vào `/ws` được nữa!
  * **Vấn đề / Thách thức kỹ thuật**:
    - Làm thế nào để một hệ thống vừa hỗ trợ kết nối WebSocket chuẩn hiệu năng cao, ít tốn băng thông cho người dùng bình thường, vừa sẵn sàng hạ cấp sang SockJS cho những người dùng bị tường lửa chặn?
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Kỹ thuật Dual-Registration (Đăng ký kép)**:
      - Trong `WebSocketConfig`, ta đăng ký endpoint `/ws` đúng 2 lần liên tiếp:
        1. `registry.addEndpoint("/ws").setAllowedOriginPatterns(origins);` (Đăng ký thuần)
        2. `registry.addEndpoint("/ws").setAllowedOriginPatterns(origins).withSockJS();` (Đăng ký kèm SockJS)
    - **Bản chất định tuyến ngầm**:
      - Nhìn qua tưởng chừng như dòng sau ghi đè dòng trước, nhưng thực tế Spring xử lý rất thông minh:
        - Đăng ký thuần tạo ra một route chính xác cho đường dẫn: `/ws`.
        - Đăng ký SockJS tạo ra một route dạng tiền tố thư mục: `/ws/**` (để phục vụ các endpoint nội bộ của SockJS như `/ws/info`, `/ws/.../xhr_streaming`).
      - Nhờ vậy, cả hai cơ chế cùng tồn tại song song trong bộ nhớ: Client dùng WebSocket thuần thì bắt tay tại `/ws`, Client cần SockJS fallback thì bắt tay tại `/ws/**`. Không bao giờ bị xung đột!

  ---

  #### 3. Lý Thuyết Mô Hình Pub/Sub & Kiểm Soát Phân Quyền Kênh Đa Tầng

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Mô hình Pub/Sub (Publish / Subscribe)**: Thay vì Client A gửi trực tiếp tin nhắn cho Client B, hệ thống sử dụng một trạm trung chuyển gọi là **Message Broker**:
      - **Subscriber (Người nghe)**: Đăng ký quan tâm đến một chủ đề (gọi lệnh `SUBSCRIBE` vào một địa chỉ `destination`).
      - **Publisher (Người nói)**: Bắn một tin nhắn vào chủ đề đó (gọi lệnh `SEND`).
      - **Broker (Bộ điều phối)**: Nhận tin từ Publisher và tự động nhân bản, phân phối tin đó tới tất cả những ai đã `SUBSCRIBE`.
    - **Quy ước 4 tiền tố (Prefix) trong hệ thống**:
      - `/topic`: Kênh phát thanh công cộng (Broadcast) — 1 người nói, cả phòng cùng nghe (ví dụ: phát nhạc, chat chung).
      - `/queue`: Kênh riêng biệt (Unicast) — 1 người nhận (ví dụ: thông báo cá nhân).
      - `/app`: Kênh dẫn vào mã nguồn xử lý nghiệp vụ của Backend (các hàm có gắn `@MessageMapping` trong Spring Controller).
      - `/user`: Kênh ảo của Spring dành riêng cho từng cá nhân (User Destination).
  * **Vấn đề / Thách thức kỹ thuật**:
    - **Lỗ hổng gửi tin lậu (Direct Injection)**: Nếu để mặc định, một Client có thể gọi lệnh `SEND` thẳng vào `/topic/liveroom/123` để chèn tin nhắn giả mạo danh nghĩa Server mà không qua bất kỳ lớp kiểm tra nào của Backend!
    - **Lỗ hổng nghe lén (Eavesdropping)**: Một người ngoài phòng, chỉ cần đoán được mã `roomId`, có thể gọi lệnh `SUBSCRIBE` vào `/topic/liveroom/{roomId}` để nghe lén chat và nhạc của phòng người khác.
    - **Lỗ hổng lộ thông tin nhạy cảm WebRTC**: Nếu các thông tin như SDP (mô tả thiết bị) hay ICE Candidate (địa chỉ IP và cổng mạng thực tế của người dùng) phát tán qua topic chung, ai trong phòng cũng đọc được thông số mạng của người khác.
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - Triển khai `StompSubscriptionScopeInterceptor` để kiểm soát mọi frame đi vào hệ thống:
      1. **Bịt kín Broker đối với lệnh `SEND`**: 
         - Cấm tuyệt đối Client gửi lệnh `SEND` vào bất kỳ destination nào bắt đầu bằng `/topic`, `/queue` hay `/user`.
         - Mọi tin nhắn từ Client gửi lên **bắt buộc phải có prefix `/app`** (ví dụ: `/app/liveroom/{id}/chat/send`). Nhờ đó, 100% dữ liệu phải đi qua Controller, được kiểm tra quyền hạn, lọc nội dung rồi mới được Server phát ra.
      2. **Kiểm tra quyền hạn khi `SUBSCRIBE` vào phòng**:
         - Khi Client gửi frame `SUBSCRIBE` vào `/topic/liveroom/{roomId}/**`, Interceptor chặn lại, trích xuất `roomId` từ URL và lấy `userId` từ `StompUserPrincipal`.
         - Hệ thống truy vấn kiểm tra: Người dùng này có đang là thành viên hợp lệ ngồi trong phòng này không? Nếu là người ngoài hoặc đã bị đuổi (kicked), Interceptor từ chối ngay lập tức bằng frame `ERROR`.
      3. **Cô lập kênh riêng tư bằng User Destination (`/user/queue/**`)**:
         - Tín hiệu WebRTC, thông báo bị từ chối vào phòng, hay lỗi cú pháp **tuyệt đối không gửi vào topic phòng**.
         - Chúng được gửi tới `/user/queue/liveroom/rtc`. Cơ chế nội tại của Spring sẽ tự động dịch tiền tố `/user` thành một hàng đợi bí mật gắn liền với danh tính Principal của chính người nhận (ví dụ: `/queue/liveroom/rtc-user123`). Người dùng B dù ở cùng phòng cũng không có bất kỳ cách nào đăng ký nghe lén được hàng đợi của người dùng A. Đây là sự bảo mật an toàn theo cấu tạo kiến trúc (Secure by Design).

  ---

  #### 4. Lý Thuyết Tính Nhất Quán Giao Dịch & Cơ Chế Phát Sự Kiện Sau Commit (`afterCommit`)

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Bài toán ghi kép bất đồng bộ (Dual-Write Consistency)**: Trong một ứng dụng thời gian thực, một hành động của người dùng (ví dụ: Đuổi một người ra khỏi phòng - Kick) bao gồm 2 việc:
      1. Ghi dữ liệu vào Database: Đổi trạng thái `kicked = true` trong một Transaction (`@Transactional`).
      2. Phát thông báo ra WebSocket: Bắn frame STOMP `PARTICIPANT_KICKED` tới mọi người trong phòng.
    - **Rủi ro Rollback**: Một Database Transaction chỉ thực sự được lưu vĩnh viễn khi câu lệnh `COMMIT` hoàn tất ở cuối hàm. Nếu trong lúc đang chạy dở hoặc lúc commit, Database bị lỗi (Deadlock, đứt mạng, vi phạm ràng buộc dữ liệu), toàn bộ Transaction sẽ bị **`ROLLBACK`** (hủy bỏ hoàn toàn, dữ liệu quay về trạng thái cũ).
  * **Vấn đề / Thách thức kỹ thuật**:
    - Nếu Use Case vừa gọi lệnh cập nhật dữ liệu xong mà tiện tay gọi lệnh gửi STOMP ngay lập tức: Frame STOMP sẽ bay qua mạng tới trình duyệt của người dùng trong vòng vài mili-giây.
    - Nhưng sau đó Database Transaction bị Rollback!
    - **Hậu quả thảm họa**: Trình duyệt của mọi người đã hiện thông báo *"User X đã bị đuổi khỏi phòng và biến mất khỏi danh sách"*, nhưng trong Database User X vẫn là thành viên hợp lệ. Giao diện và dữ liệu thực tế bị lệch pha hoàn toàn — hệ thống đã "nói dối" Client.
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Kỹ thuật đồng bộ giao dịch (Transaction Synchronization)**:
      - Tại `StompLiveroomEventPublisherAdapter`, khi Use Case gọi lệnh phát sự kiện, Adapter **không bao giờ gửi đi ngay lập tức**.
      - Thay vào đó, nó kiểm tra xem luồng xử lý hiện tại có đang nằm trong một Database Transaction hay không thông qua công cụ của Spring: `TransactionSynchronizationManager.isActualTransactionActive()`.
      - Nếu có, Adapter sẽ gói sự kiện lại và đăng ký một Hook treo vào bộ điều phối giao dịch:
        ```
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // Chỉ kích hoạt gửi STOMP khi Database đã COMMIT THÀNH CÔNG 100%!
                messagingTemplate.convertAndSend(...);
            }
        });
        ```
    - **Bảo toàn dữ liệu tuyệt đối**: Nếu Transaction bị lỗi và Rollback, hook `afterCommit` vĩnh viễn không bao giờ được gọi. Không có một frame thông báo rác hay thông báo sai sự thật nào bị lọt xuống Client.
    - **Nguyên tắc Clean Code**: Tầng Use Case hoàn toàn không cần viết các lệnh callback phức tạp, cứ gọi phát sự kiện bình thường. Tầng Infrastructure tự động lo trọn vẹn việc đồng bộ giao dịch.

  ---

  #### 5. Lý Thuyết Khả Năng Chống Chịu Phía Client: Token Tươi & Chống Bão Kết Nối (Thundering Herd)

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Vấn đề Access Token hết hạn khi máy ngủ (Sleep Mode)**: Người dùng mở phòng nghe nhạc trên Laptop, gập màn hình lại đi ăn trưa (30 phút). Trong thời gian đó, Access Token JWT (có thời hạn sống thường là 15 phút) đã hết hạn. Khi mở máy ra, kết nối WebSocket bị đứt và Client cố gắng kết nối lại bằng chính token đã chết đó $\rightarrow$ Server từ chối và giao diện bị văng ra màn hình đăng nhập một cách ức chế.
    - **Hiện tượng Bão kết nối (Thundering Herd Problem)**: 
      - Giả sử máy chủ Server cần bảo trì và Restart lại trong 5 giây. Ngay khi Server ngắt, 1.000 người dùng trong các phòng đồng loạt bị ngắt kết nối.
      - Nếu mã nguồn Client được viết ngây thơ theo kiểu: *"Cứ đứt kết nối là lập tức gọi hàm kết nối lại sau 1 giây"*, thì đúng 1 giây sau khi Server vừa mở cổng, **toàn bộ 1.000 Client sẽ ùa vào cùng một mili-giây**!
      - Lượng truy cập đột biến này làm tê liệt CPU, nghẽn hàng đợi TCP và khiến Server vừa khởi động xong đã lập tức bị sập lại lần nữa (Reboot Loop).
    - **Thuật toán Exponential Backoff kết hợp Jitter**:
      - **Exponential Backoff (Lùi theo cấp số nhân)**: Thay vì thử lại liên tục, ta giãn dần thời gian giữa các lần thử: $1s \rightarrow 2s \rightarrow 4s \rightarrow 8s \rightarrow 15s$.
      - **Jitter (Độ trễ ngẫu nhiên)**: Nếu 1.000 người đều chờ 1s rồi cùng vào, thảm họa vẫn xảy ra. Jitter là việc cộng thêm một khoảng thời gian ngẫu nhiên nhỏ (ví dụ từ $0$ đến $400ms$). Khi đó, người thử lại lúc 1.05s, người lúc 1.23s, người lúc 1.38s... Dòng lưu lượng được làm loãng và phân tán đều theo thời gian.
  * **Cơ chế kỹ thuật chúng ta xử lý (tại `liveroom-socket.ts`)**:
    - **Cơ chế làm tươi token chủ động (`freshToken`)**:
      - Trong cấu hình `@stomp/stompjs`, ta can thiệp vào hook `beforeConnect`.
      - Trước khi gửi frame `CONNECT`, Client kiểm tra thời gian sống còn lại của JWT. Nếu token còn dưới 30 giây (hoặc đã hết hạn), Client sẽ tạm dừng, gọi API Refresh Token âm thầm lấy một JWT mới tinh, cập nhật vào Header rồi mới thực hiện kết nối. Người dùng mở máy ra là kết nối lại mượt mà, không bao giờ bị văng.
    - **Cơ chế chuyển hướng thích ứng (Adaptive Fallback)**:
      - Client ưu tiên tuyệt đối cho WebSocket thuần để đạt tốc độ phản hồi tính bằng mili-giây.
      - Bộ đếm ghi nhận: nếu kết nối bị đóng 2 lần liên tiếp mà chưa từng thành công (dấu hiệu chắc chắn của tường lửa mạng đang chặn WebSocket), Client sẽ tự động chuyển cờ sang sử dụng SockJS để đảm bảo người dùng vẫn vào được phòng nghe nhạc.
    - **Vòng lặp Backoff + Jitter tự trị**:
      - Tắt hoàn toàn tính năng reconnect tự động của thư viện (`reconnectDelay: 0`) để tránh xung đột hai luồng thử lại.
      - Tự cài đặt công thức: `Thời gian chờ = min(15s, 1s * 2^(lần_thử - 1)) + random(0, 400ms)`. Tối đa 8 lần thử. Jitter 400ms triệt tiêu hoàn toàn hiện tượng Thundering Herd, bảo vệ Server sống sót an toàn sau mỗi đợt bảo trì.

  ---

  #### Tóm Tắt Bỏ Túi Để Trả Lời Phỏng Vấn (Elevator Pitch)

  Nếu người phỏng vấn hỏi: *"Làm thế nào bạn thiết kế hạ tầng WebSocket/STOMP vừa bảo mật vừa chịu tải cao trong môi trường trình duyệt?"*, sếp có thể trả lời đầy đẳng cấp và tự tin như sau:

  > *"Tại Module Live Room của PWB_MiNi, hạ tầng STOMP WebSocket được thiết kế dựa trên 5 nguyên lý kỹ thuật cốt lõi:
  > 
  > 1. **Xác thực phi chuẩn (Token over STOMP Frame)**: Do trình duyệt cấm chèn header vào HTTP Handshake, bọn em mở handshake công khai và đón lõng xác thực Bearer JWT ngay tại frame `CONNECT` của STOMP qua `StompAuthChannelInterceptor`, gắn `StompUserPrincipal` vào session để quản lý danh tính xuyên suốt.
  > 2. **Đăng ký kép (Dual-Registration)**: Đăng ký song song cả WebSocket thuần và SockJS trên endpoint `/ws` giúp tối ưu hiệu năng tối đa cho client chuẩn, đồng thời tự động dự phòng (fallback) qua HTTP Long-Polling khi người dùng ở sau tường lửa doanh nghiệp bị chặn WebSocket.
  > 3. **Kiểm soát bảo mật đa tầng**: Cấm tuyệt đối client `SEND` trực tiếp vào Broker mà bắt buộc phải đi qua `/app/**`; phân quyền chặt chẽ khi `SUBSCRIBE` kênh phòng; và cô lập toàn bộ thông tin nhạy cảm (như WebRTC signaling) vào kênh cá nhân `/user/queue/**` an toàn theo cấu tạo kiến trúc.
  > 4. **Nhất quán dữ liệu (Transactional Safety)**: Áp dụng cơ chế hoãn phát sự kiện, chỉ broadcast các frame STOMP sau khi Database Transaction nhận tín hiệu `afterCommit` thành công, triệt tiêu hoàn toàn rủi ro hiển thị dữ liệu ảo khi transaction bị rollback.
  > 5. **Khả năng tự phục hồi phía Client**: Tự động làm tươi JWT trước khi kết nối nếu token sắp hết hạn, kết hợp thuật toán Exponential Backoff kèm Jitter ngẫu nhiên 400ms để chống hiện tượng sập server vì bão kết nối (Thundering Herd) sau khi khởi động lại."*

- **Chi Tiết Phần 2: Mô Hình Session Cycle (Phiên) & Cơ Chế Đóng/Mở Phòng, Hoàn Tác (Room Lifecycle & Session Cycle)**:

  Khi phỏng vấn về kiến trúc vòng đời phòng trong các hệ thống cộng tác thời gian thực, câu hỏi trọng tâm thường xoay quanh **sự phân định giữa thực thể dài hạn và ngắn hạn (session boundaries), tính liên tục của lịch sử thành viên (membership persistence), tính toàn vẹn khi đóng phòng (atomic teardown) và cơ chế hoàn tác sai sót của người dùng (undo window)**. Sếp có thể tự tin dẫn dắt qua **5 luận điểm kỹ thuật cốt lõi**:

  ---

  #### 1. Lý Thuyết Thực Thể Vĩnh Viễn vs Thực Thể Theo Phiên: Tách Rời `LiveRoom` & `SessionCycle`

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Bài toán vòng đời trong các hệ thống cộng tác (Collaboration Systems)**:
      - Trong các ứng dụng như Zoom, Google Meet hay Discord Voice Channel, một "Phòng Họp" có hai tầng tồn tại hoàn toàn khác nhau về mặt thời gian:
        1. **Tầng nhận diện dài hạn (Persistent Identity)**: Mã phòng (6 ký tự), tên phòng, chủ phòng, cấu hình sức chứa và danh sách thành viên quen thuộc. Tầng này có thể tồn tại nhiều tháng, thậm chí nhiều năm.
        2. **Tầng phiên làm việc ngắn hạn (Ephemeral Session / Session Cycle)**: Một buổi nghe nhạc cụ thể diễn ra từ 20:00 đến 22:00 tối thứ Bảy.
    - **Thảm họa nếu gộp chung dữ liệu**:
      - Nếu lập trình viên thiết kế ngây thơ: Gắn danh sách người tham gia (`participants`) và tin nhắn chat (`chat_messages`) trực tiếp vào khóa ngoại `room_id`.
      - Khi chủ phòng kết thúc buổi nghe nhạc tối thứ Bảy, rồi đến tối thứ Tư tuần sau bấm "Mở lại phòng" (Reopen) để họp với một nhóm khách hàng mới: Toàn bộ lịch sử chat nhạy cảm, tin nhắn riêng tư và danh sách người ngồi của buổi thứ Bảy tuần trước **vẫn hiện lù lù trên màn hình**! Điều này phá hủy hoàn toàn tính riêng tư giữa các buổi họp.
  * **Vấn đề / Thách thức kỹ thuật**:
    - Làm thế nào để tái sử dụng lại mã phòng và thương hiệu của phòng cho nhiều buổi họp khác nhau, nhưng mỗi buổi họp mới đều phải bắt đầu từ một "trang giấy trắng tinh khiết" mà không bị lẫn lộn dữ liệu cũ?
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Tách rời thực thể: Một Phòng chứa nhiều Phiên (`1 Room - N Cycles`)**:
      - Bảng `liveroom_rooms` lưu giữ các thông tin vĩnh viễn: `id`, `room_code`, `room_name`, `owner_id`, `capacity`, và con trỏ trỏ tới phiên hiện tại `current_cycle_id`.
      - Bảng `liveroom_session_cycles` đại diện cho một buổi họp cụ thể, gồm: `id`, `room_id`, `cycle_number` (tự tăng: $1, 2, 3...$), `started_at`, `ended_at`, `ended_reason`.
    - **Khóa ngoại chỉ trỏ vào `cycle_id`**:
      - Toàn bộ các bảng dữ liệu phát sinh theo thời gian thực: `liveroom_participants` (người ngồi) và `liveroom_chat_messages` (tin nhắn) **bắt buộc phải gắn với `cycle_id`**, tuyệt đối không gắn với `room_id`.
      - Khi chủ phòng mở lại phòng cũ (`ReopenRoomUseCase`): Hệ thống sinh ra một bản ghi `SessionCycle` hoàn toàn mới với `cycle_number` tăng lên 1, cập nhật `current_cycle_id` của phòng sang ID mới. 
      - Kết quả: Khách vào phòng mới được đón chào bằng một phòng trống hoàn toàn sạch sẽ, lịch sử chat cũ nằm yên ở phiên trước mà không bị xóa mất trong cơ sở dữ liệu.

  ---

  #### 2. Lý Thuyết Lịch Sử Thành Viên & Phân Biệt 3 Bảng Dữ Liệu Dễ Nhầm Lẫn Nhất

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - Để quản lý người dùng trong một phòng nghe nhạc trực tuyến, hệ thống phải trả lời được **3 câu hỏi với 3 phạm vi thời gian khác nhau**:
      1. *Ngay lúc này (Right Now)*: Ai đang thực sự ngồi trong phòng?
      2. *Trong lần xin này (Single Attempt)*: Lời xin vào phòng này đã được chủ duyệt hay từ chối?
      3. *Xuyên suốt cả cuộc đời của phòng (Across All Sessions)*: Người này đã từng được chủ phòng duyệt chưa? Có đang bị phạt cấm vào phòng không?
  * **Vấn đề / Thách thức kỹ thuật**:
    - Nếu không phân định ranh giới rõ ràng, hệ thống sẽ bị rối loạn logic kiểm tra: Người vừa bị chủ phòng Kick 30 giây trước có thể bấm F5 xin vào lại ngay; hoặc một người bạn thân tuần nào cũng vào phòng lại bắt chủ phòng phải bấm nút "Duyệt" lại từ đầu mỗi khi mở phiên mới.
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - Hệ thống chuẩn hóa thành 3 bảng với trách nhiệm độc lập:
      1. `liveroom_participants` (**Phạm vi một phiên - `cycle_id`**): Trả lời câu hỏi *"Ai đang ngồi trong phòng lúc này?"*. Khi ai đó rời phòng hoặc bị kick, cột `left_at` được đóng dấu thời gian.
      2. `liveroom_join_requests` (**Phạm vi một lần xin**): Lưu trạng thái `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED` của một lần gửi yêu cầu tham gia.
      3. `liveroom_room_members` (**Phạm vi cả cuộc đời của phòng - `room_id`**):
         - Bảng này dùng khóa chính phức hợp: `(room_id, user_id)`.
         - Nó giải quyết bài toán trải nghiệm và an ninh bằng 4 thông tin then chốt:
           - `was_approved`: Đánh dấu người này đã từng được chủ phòng tin tưởng duyệt vào phòng trong quá khứ. Nếu cờ này là `true`, ở các phiên sau, người dùng này **được phép đi thẳng vào phòng qua cửa trước mà không cần chờ chủ phòng duyệt lại**!
           - `kicked_at` + `kicked_cooldown_until`: Thời điểm bị đuổi và thời hạn hết án phạt (mặc định 5 phút). Ngăn chặn kẻ quậy phá vừa bị kick đã lập tức gửi yêu cầu xin vào lại.
           - `reject_count_by_owner` & `reject_count_by_capacity`: Đếm số lần bị từ chối để phát hiện hành vi spam xin vào phòng.
    - **Triết lý gộp bảng thông minh**: Bốn thông tin trên lẽ ra có thể tách thành 4 bảng riêng, nhưng vì chúng **cùng được đọc đồng thời bởi một phép kiểm duy nhất** ngay tại cửa vào phòng, nên việc gộp vào `room_members` giúp truy vấn cực nhanh, triệt tiêu các phép JOIN đắt đỏ trong cơ sở dữ liệu.

  ---

  #### 3. Lý Thuyết Thủ Tục Dọn Dẹp Khi Đóng Phòng & Hai Hành Vi Đối Lập Có Chủ Đích

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Thủ tục giải tỏa tài nguyên (Teardown Procedure)**:
      - Đóng một phòng họp trực tuyến không đơn giản là đổi trạng thái một dòng trong DB (`status = ENDED`).
      - Khi phòng đóng, hệ thống phải giải quyết hàng loạt tài nguyên liên đới: hàng chục kết nối WebSocket đang nghe, các yêu cầu tham gia đang treo, bản nhạc đang phát dở và các bộ đệm dữ liệu tạm thời.
  * **Vấn đề / Thách thức kỹ thuật**:
    - Nếu không có một quy trình dọn dẹp nguyên khối (Atomic Termination), hệ thống sẽ để lại dữ liệu rác: Người dùng bị treo ở màn hình chờ duyệt vĩnh viễn, nhạc vẫn tiếp tục chạy ngầm làm tốn CPU, hoặc làm hỏng khả năng "Hoàn tác" nếu lỡ tay bấm nhầm.
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - Khi phòng kết thúc (do chủ phòng chủ động bấm đóng, hoặc do scheduler kích hoạt khi phòng rỗng / chủ phòng vắng mặt), use case `RoomTermination.terminate` thực thi **một thủ tục 6 bước nghiêm ngặt trong cùng 1 Transaction**:
      1. `closeOccupants(cycleId, at)`: Đóng phiên toàn bộ những người đang ngồi trong phòng (gán `left_at = now`).
      2. `expirePendingRequests(roomId, at)`: Quét sạch mọi yêu cầu xin vào đang chờ và chuyển sang trạng thái hết hạn (`EXPIRED`).
      3. `playbacks.freezeOnRoomEnd(room, at)`: **Nhạc chỉ TẠM DỪNG (`PAUSED`), tuyệt đối KHÔNG XÓA**.
      4. `trackCommentStore.clearCycle(cycleId)`: **Bình luận ghim trên timeline nhạc bị XÓA SẠCH HOÀN TOÀN**.
      5. `room.end(reason, at)`: Đánh dấu phòng kết thúc kèm lý do cụ thể (`OWNER_CLOSED`, `EMPTY_TIMEOUT`, `OWNER_ABSENT_TIMEOUT`).
      6. `sessionCycleStarter.close(...)`: Đóng phiên làm việc hiện tại của phòng.
    - **Giải mã nghịch lý kiến trúc: Tại sao bước 3 và bước 4 lại làm ngược nhau?**:
      - *Vì sao Nhạc không xóa mà chỉ Tạm dừng?*: Yêu cầu nghiệp vụ ban đầu từng muốn xóa bài nhạc đang phát. Nhưng các kiến trúc sư nhận ra: nếu xóa nhạc, khi chủ phòng bấm "Hoàn tác" (Undo-End) vì lỡ tay bấm nhầm, cả phòng sẽ hồi sinh nhưng bài nhạc bị mất tích! Do đó, nhạc chỉ được đóng băng lại mốc thời gian tạm dừng để sẵn sàng hồi sinh nếu có lệnh hoàn tác.
      - *Vì sao Bình luận timeline bị xóa sạch?*: Vì tính năng bình luận theo giây của bài hát hiện đang lưu trữ trên bộ nhớ RAM (`InMemoryTrackCommentStore`), gắn theo `cycle_id`. Khi phiên đóng lại, toàn bộ bộ nhớ RAM này được giải phóng ngay lập tức để chống tràn bộ nhớ (Memory Leak) cho máy chủ.

  ---

  #### 4. Lý Thuyết Cửa Sổ Hoàn Tác & Kỹ Thuật Hồi Sinh Bằng Dấu Thời Gian (`ended_at`)

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Bài toán bấm nhầm (Fat-Finger Problem)**: Trên thiết bị di động hoặc khi thao tác nhanh, việc chủ phòng bấm nhầm vào nút "Kết thúc phòng" thay vì nút "Cài đặt" là tai nạn rất hay xảy ra.
    - Một hệ thống thông minh không bao giờ "giết chết" phòng ngay tức khắc mà luôn thiết kế một **Cửa sổ hoàn tác ngắn (Undo Window)** để chủ phòng có cơ hội sửa sai.
  * **Vấn đề / Thách thức kỹ thuật**:
    - Nếu hoàn tác bằng cách mở một phiên mới (`reopen`), người dùng sẽ bị xóa sạch tin nhắn chat vừa nói và bài nhạc vừa nghe.
    - Làm thế nào để phân biệt được: **Ai là người vừa bị hệ thống đá ra do phòng kết thúc (cần được hồi sinh kéo về phòng)**, và **Ai là người đã chủ động bấm nút "Rời phòng" từ 10 phút trước đó (tuyệt đối không được tự ý lôi họ quay trở lại)**?
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Cửa sổ vàng 5 giây (`undoEndWindow = 5s`)**:
      - Hệ thống chỉ chấp nhận lệnh `UndoEndRoomUseCase` nếu thời gian trôi qua kể từ lúc kết thúc phòng nhỏ hơn hoặc bằng 5 giây (`now - endedAt <= 5s`).
      - *Vì sao chỉ 5 giây?*: 5 giây là vừa đủ cho một phản xạ bấm "Hoàn tác" khi nhận ra mình bấm nhầm. Nếu để quá lâu (ví dụ vài phút), người tham gia đã đóng tab hoặc chuyển sang làm việc khác; việc đột ngột kéo họ vào phòng lại sẽ gây sốc và phá vỡ trải nghiệm người dùng.
    - **Hồi sinh đúng phiên cũ (Revive Current Cycle)**:
      - Khi hoàn tác thành công, `cycle_number` không tăng, `current_cycle_id` được giữ nguyên. Toàn bộ tin nhắn chat trong phiên vẫn nguyên vẹn.
    - **Kỹ thuật lọc người thông minh bằng mốc thời gian `endedAt`**:
      - Thay vì phải tạo thêm một cột cờ phức tạp để ghi nhớ lý do rời phòng của từng người, hệ thống chỉ dùng đúng một câu truy vấn thời gian:
        `participantRepository.findClosedWithRoomAt(cycleId, endedAt);`
      - **Bản chất**: Những người bị đuổi ra do phòng đóng đều có cùng một dấu thời gian `left_at` bằng đúng chính xác mốc `endedAt` của phòng! Còn những người tự giác rời phòng trước đó đều có `left_at < endedAt`.
      - Nhờ so khớp mốc thời gian, hệ thống tự động lọc và khôi phục chỗ ngồi (`restoreOccupants`) cho đúng những người vừa bị đẩy ra oan uổng, khôi phục lại sức chứa và phát sự kiện hồi sinh phòng ra WebSocket.

  ---

  #### 5. Lý Thuyết Mã Phòng Ngẫu Nhiên An Toàn & Bộ Điều Tiết Chống Đoán Mò (Anti-Brute Force Throttle)

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Bản chất của Mã phòng (Room Code)**: Thay vì bắt người dùng chia sẻ những đường link URL loằng ngoằng, hệ thống sử dụng một mã 6 ký tự viết hoa (như `ABC-XYZ` hoặc `A1B2C3`) dễ đọc, dễ nhớ để mời bạn bè qua tin nhắn hay đọc miệng.
    - **Rủi ro tấn công dò mã (Brute-Force Attack)**:
      - Vì mã phòng chỉ có 6 ký tự, không gian tổ hợp của nó là hữu hạn. Kẻ xấu có thể viết bot tự động gửi hàng triệu request lên API tra cứu phòng `GET /rooms/by-code/{code}` để quét xem mã nào đang mở và đột nhập vào nghe lén các bài hát chưa ra mắt của nghệ sĩ.
  * **Vấn đề / Thách thức kỹ thuật**:
    - Làm thế nào để sinh mã ngẫu nhiên tuyệt đối an toàn không thể bị đoán trước thuật toán, và chặn đứng hoàn toàn các cuộc tấn công dò quét mã từ bên ngoài?
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Bộ sinh mã an toàn mật mã học (`SecureRandomRoomCodeGeneratorAdapter`)**:
      - Hệ thống tuyệt đối không dùng hàm `Math.random()` hay `java.util.Random` (vốn là các bộ sinh số giả ngẫu nhiên có thể bị dịch ngược chuỗi số nếu biết hạt giống seed).
      - Thay vào đó, hệ thống bắt buộc sử dụng `java.security.SecureRandom` — lấy nguồn entropy ngẫu nhiên thực tế từ phần cứng và hệ điều hành, đảm bảo mã phòng sinh ra hoàn toàn ngẫu nhiên và không thể đoán trước.
  * **Thuật toán giải quyết xung đột mã (Collision Retry)**:
    - Khi tạo phòng, hệ thống sinh candidate và kiểm tra xem mã đã tồn tại chưa (`existsByRoomCode`). Nếu trùng, hệ thống tự động thử lại tối đa 5 lần (`code-generation-attempts = 5`).
  * **Bộ điều tiết chống dò mã riêng biệt trên Redis (`RoomCodeLookupThrottle`)**:
    - Tại endpoint tra cứu mã phòng, hệ thống triển khai một chốt chặn giới hạn tần suất: **Tối đa 20 lần tra cứu trong 15 phút cho mỗi địa chỉ IP**.
    - *Vì sao không dùng Rate Limit chung của toàn Server?*: Filter Rate Limit chung của hệ thống tính trên cửa sổ ngắn (ví dụ 100 req/phút). Nếu áp cửa sổ 15 phút vào đó sẽ làm ảnh hưởng đến các API thông thường khác. Do đó, tra cứu mã phòng phải có một Key độc lập trên Redis: `pwb:throttle:room-code-lookup:{ip}`.
    - *Triết lý Fail-Open khi Redis gặp sự cố*: Nếu cụm Redis bị sập, bộ throttle sẽ chỉ ghi log cảnh báo và **cho phép request đi qua (Fail-Open)**, không làm tê liệt tính năng tra cứu phòng của người dùng bình thường (ngược lại hoàn toàn với nghiệp vụ Đăng nhập ở Module IAM là Fail-Closed để bảo vệ tài khoản). Đây là sự đánh đổi có chủ đích giữa bảo mật và tính sẵn sàng của sản phẩm.

  ---

  #### Tóm Tắt Bỏ Túi Để Trả Lời Phỏng Vấn (Elevator Pitch)

  Nếu người phỏng vấn hỏi: *"Làm thế nào bạn thiết kế vòng đời phòng trong hệ thống Live Room vừa linh hoạt, bảo mật vừa đảm bảo trải nghiệm người dùng?"*, sếp có thể trả lời đầy đẳng cấp và khúc chiết như sau:

  > *"Tại Module Live Room của PWB_MiNi, vòng đời phòng được thiết kế dựa trên 5 nguyên lý kiến trúc chuyên sâu:
  > 
  > 1. **Tách biệt Phòng vs Phiên (Room vs Session Cycle)**: Thực thể `LiveRoom` đại diện cho danh tính dài hạn (mã phòng, chủ phòng), trong khi `SessionCycle` đại diện cho từng buổi họp cụ thể. Mọi dữ liệu thời gian thực như người ngồi và chat đều gắn với `cycle_id`, giúp việc mở lại phòng cũ luôn có một phiên mới sạch sẽ 100% mà không lẫn dữ liệu cũ.
  > 2. **Chuẩn hóa lịch sử thành viên (`room_members`)**: Dùng khóa phức hợp `(room_id, user_id)` lưu lại cờ `was_approved` để người từng được duyệt có thể vào thẳng ở các phiên sau, kết hợp lưu thời hạn phạt `kicked_cooldown_until` để chống người bị kick quay lại quấy phá.
  > 3. **Quy trình đóng phòng 6 bước**: Điều phối việc đẩy người ra khỏi phòng, hủy các request chờ duyệt, tạm dừng nhạc (chứ không xóa) để sẵn sàng hoàn tác, và xóa bộ nhớ đệm bình luận timeline trên RAM để chống tràn bộ nhớ.
  > 4. **Cửa sổ hoàn tác 5 giây (Undo-End)**: Cho phép hồi sinh đúng phiên cũ nếu chủ phòng lỡ tay bấm nhầm, sử dụng kỹ thuật so khớp mốc thời gian `left_at == endedAt` để chỉ kéo đúng những người bị ngắt oan quay lại phòng.
  > 5. **Bảo mật mã phòng hai lớp**: Sử dụng `SecureRandom` sinh mã 6 ký tự an toàn mật mã học, kết hợp bộ điều tiết trên Redis (Throttle 20 lần/15 phút/IP) với cơ chế Fail-Open để chặn đứng hoàn toàn các cuộc tấn công Brute-Force dò quét mã phòng."*

- **Chi Tiết Phần 3: Kiểm Soát Vào Phòng, Quản Trị Sức Chứa & Hình Phạt (Admission, Pessimistic Lock & Moderation)**:

  Khi phỏng vấn về các khía cạnh kiểm soát truy cập và điều phối tài nguyên thời gian thực, nhà tuyển dụng thường tập trung vào **xử lý xung đột đồng thời (concurrency race conditions), quản trị sức chứa (capacity enforcement), tính đối xứng của luồng dữ liệu (event symmetry) và cơ chế xử phạt tự động (moderation mechanics)**. Sếp có thể tự tin dẫn dắt qua **5 luận điểm kỹ thuật cốt lõi**:

  ---

  #### 1. Lý Thuyết Khóa Bi Quan (`SELECT FOR UPDATE`) & Chống Race Condition Vượt Sức Chứa Phòng

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Khóa bi quan (Pessimistic Locking) là gì?**:
      - Trong quản trị cơ sở dữ liệu, khi nhiều người cùng muốn thay đổi một dòng dữ liệu tại một thời điểm, có 2 trường phái giải quyết:
        1. *Khóa lạc quan (Optimistic Locking)*: Giả định rằng rất ít khi xảy ra đụng độ. Ai cũng được đọc dữ liệu kèm một số `version`. Khi ghi, nếu thấy số `version` đã bị người khác tăng lên thì báo lỗi thất bại.
        2. *Khóa bi quan (Pessimistic Locking)*: Giả định rằng **chắc chắn sẽ có xung đột tranh giành dữ liệu gay gắt**. Do đó, ngay từ lúc đọc dòng dữ liệu đó lên, hệ thống phát lệnh `SELECT ... FOR UPDATE` để **khóa chặt dòng đó lại ở mức Database**. Bất kỳ ai khác muốn đọc hay sửa dòng đó đều bắt buộc phải đứng xếp hàng chờ cho đến khi giao dịch đầu tiên hoàn tất (`COMMIT` hoặc `ROLLBACK`).
    - **Bài toán Race Condition vượt sức chứa phòng**:
      - Giả sử phòng Live Room có sức chứa tối đa là **7 người**, hiện tại đã có 6 người ngồi bên trong (nghĩa là **chỉ còn đúng 1 chỗ trống duy nhất**).
      - Cùng một tích tắc mili-giây, có 2 người dùng A và B cùng bấm "Vào phòng" (hoặc chủ phòng bấm duyệt cho cả 2 người cùng lúc):
        - *Nếu không dùng khóa hoặc dùng khóa lạc quan*: Cả 2 luồng xử lý cùng đọc Database và cùng thấy `current_participant_count = 6 < 7` (còn chỗ!). Cả 2 luồng cùng thực hiện lệnh tăng số người lên 7 và nhét cả A và B vào phòng.
        - **Hậu quả thảm họa**: Phòng bị vọt lên **8 người**, vượt quá ngưỡng chịu đựng băng thông của mạng WebRTC Full-Mesh, kéo sập chất lượng video/audio của cả phòng mà không có cách nào sửa chữa!
  * **Vấn đề / Thách thức kỹ thuật**:
    - Làm thế nào để đảm bảo tính toàn vẹn tuyệt đối của sức chứa phòng trong môi trường có hàng trăm request xin vào phòng đồng thời?
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Áp dụng Khóa bi quan ở mọi cửa ngõ thay đổi sức chứa**:
      - Trong phương thức kiểm tra sức chứa, hệ thống không gọi `findById` thông thường mà bắt buộc gọi: `liveRoomRepository.findByIdForUpdate(roomId)`.
      - Lệnh này sinh ra câu lệnh SQL có khóa độc quyền: `SELECT * FROM liveroom_rooms WHERE id = ? FOR UPDATE`.
      - Khi luồng của khách A đến trước: Dòng của phòng đó bị khóa cứng. Khách A kiểm tra thấy $6 < 7$, tăng số lượng lên 7, thêm A vào phòng và commit.
      - Lúc này khóa mới nhả ra cho luồng của khách B: Luồng B đọc lại dữ liệu tươi từ Database và thấy số người đã là $7 \ge 7$ (hết chỗ!).
      - Hệ thống lập tức ném ngoại lệ `LiveroomBusinessException(CAPACITY_REACHED)`, từ chối khách B một cách an toàn và dứt khoát.

  ---

  #### 2. Lý Thuyết Hai Cửa Vào Phòng & Cái Bẫy Bỏ Sót Đồng Bộ Âm Thanh (Two Entry Points)

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Khái niệm đa cổng nhập (Multiple Entry Points)**: Trong thiết kế phần mềm, một trạng thái nghiệp vụ (ở đây là trạng thái *"Người dùng chính thức có mặt trong phòng"*) có thể được kích hoạt từ nhiều luồng hành vi khác nhau của người dùng.
  * **Vấn đề / Thách thức kỹ thuật**:
    - Trong Live Room, người dùng bước chân vào phòng qua **2 con đường hoàn toàn độc lập**:
      1. **Cửa trước (`JoinRoomUseCase`)**: Dành cho Chủ phòng hoặc những "Khách quen" đã từng được duyệt vào phòng trong quá khứ (`was_approved = true`). Họ tự bấm nút "Vào phòng" trên giao diện và bước thẳng vào.
      2. **Cửa sau (`ApproveJoinRequestUseCase`)**: Dành cho khách lạ. Họ phải bấm "Xin vào phòng" và ngồi chờ ở sảnh. Chủ phòng nhìn thấy thông báo và bấm nút "Duyệt". Luồng use case duyệt sẽ trực tiếp kéo người đó vào phòng (`admissions.admit`) **mà hoàn toàn không hề đi qua `JoinRoomUseCase`**!
    - **Cái bẫy kiến trúc "Khách bị điếc"**:
      - Khi một người bước vào phòng, họ cần được biết ngay lập tức: Ai đang ngồi trong phòng? Và đặc biệt nhất: **Bài hát nào đang phát, và đang chạy tới giây thứ mấy?**
      - Nếu lập trình viên mắc bẫy, chỉ viết hàm gửi dữ liệu bài hát hiện tại (`playbacks.sendCurrentTo(userId)`) ở `JoinRoomUseCase`, thì những người đi bằng "Cửa sau" (được chủ duyệt vào giữa chừng) sẽ **ngồi trong phòng trong im lặng tuyệt đối, không nghe thấy bất kỳ tiếng nhạc nào** vì không ai gửi mốc thời gian bài hát cho họ!
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Đảm bảo tính đối xứng hoàn hảo (Symmetric Event Dispatching)**:
      - Kiến trúc quy định: Bất kỳ logic nào cần kích hoạt khi có người vào phòng đều phải được đóng gói và gọi đồng thời ở **CẢ HAI USE CASE**:
        1. `admissions.admit(...)`: Xếp chỗ ngồi, tăng số lượng người tham gia trong phiên.
        2. `playbacks.sendCurrentTo(userId, ...)`: Bắn gói tin trạng thái âm thanh hiện tại (bài hát, mốc giây, trạng thái play/pause) qua kênh riêng `/topic/liveroom/{id}/music` cho riêng người mới.
        3. `eventPublisher.publish(RoomEvents.participantJoined(...))`: Bắn sự kiện báo cho cả phòng biết có người mới vào để WebRTC kích hoạt bắt tay P2P.
        4. `eventPublisher.publish(capacityChanged / capacityReached)`: Cập nhật sĩ số phòng theo thời gian thực.
      - Nhờ sự đối xứng này, dù đi cửa trước hay cửa sau, người dùng đều nhận được trải nghiệm mượt mà, bài hát tự động vang lên đúng giây đồng bộ với mọi người.

  ---

  #### 3. Lý Thuyết Hình Phạt Bằng Mốc Thời Gian (Deadline Timestamp) vs Cờ Trạng Thái (Boolean Flags)

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Hai trường phái thiết kế hình phạt kiểm duyệt (Moderation Flags)**:
      - *Trường phái 1 (Cờ Boolean)*: Dùng các cột như `is_kicked = true`, `is_muted = true`.
      - *Trường phái 2 (Mốc thời gian hết hạn - Deadline Timestamp)*: Dùng các cột lưu thời điểm kết thúc án phạt: `kicked_cooldown_until = now + 5 phút`, `mic_unmute_cooldown_until = now + 30 giây`.
  * **Vấn đề / Thách thức kỹ thuật**:
    - Nếu dùng cờ Boolean: Sau khi phạt tắt mic 30 giây, làm thế nào để gỡ cờ chuyển về `false`? Bạn phải viết một Background Cron Job chạy quét Database liên tục từng giây để kiểm tra và gỡ cờ $\rightarrow$ Cực kỳ lãng phí CPU và làm nghẽn kết nối Database.
    - Hơn thế nữa, cờ Boolean rất dễ bị lách luật: Kẻ bị tắt mic chỉ cần bấm F5 thoát phòng rồi vào lại. Khi vào lại, hệ thống thấy mic đang tắt thì tưởng là "người dùng tự tắt" và cho phép họ tự bấm bật mic lên lại ngay lập tức!
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - Hệ thống triệt để sử dụng **Mốc thời gian Deadline** trong bảng `liveroom_room_members` và `liveroom_participants`:
      - **Án phạt đuổi khỏi phòng (Kick Penalty)**: Khi bị kick, hệ thống gán `kicked_cooldown_until = Instant.now().plusSeconds(300)` (5 phút). Kẻ bị đuổi nếu cố tình bấm xin vào lại sẽ bị chặn đứng ngay ở cửa kiểm duyệt vì `Instant.now().isBefore(kickedCooldownUntil)`.
      - **Án phạt tắt mic cưỡng chế (Remote Mute Penalty)**: Khi chủ phòng tắt mic của một thành viên nói bậy, cột `mic_unmute_cooldown_until` được gán mốc thời gian 30 giây sau.
    - **Ba lợi ích kiến trúc đỉnh cao**:
      1. **Không cần Job quét ngầm**: Chỉ cần so sánh mốc thời gian với `Instant.now()` tại thời điểm có request là biết ngay án phạt còn hiệu lực hay không. Tiết kiệm 100% tài nguyên xử lý nền.
      2. **Miễn nhiễm với trò lách luật thoát ra vào lại**: Dù kẻ quậy phá có thoát khỏi phòng, ngắt kết nối mạng rồi kết nối lại 10 lần thì giá trị `mic_unmute_cooldown_until` trong Database vẫn nằm nguyên đó. Chỉ khi đồng hồ thực tế vượt qua mốc này, nút bật mic mới sáng trở lại.
      3. **Tự động hết hạn chính xác tới phần nghìn giây**.

  ---

  #### 4. Lý Thuyết Giám Sát Chủ Phòng Vắng Mặt & Thời Gian Ân Hạn (`ownerGraceSeconds`)

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Mô hình phòng tập trung vào chủ (Host-Centric Architecture)**:
      - Phòng Live Room là phòng do Chủ phòng (Producer/Nghệ sĩ) tạo ra để giao lưu với người hâm mộ. Chủ phòng là người giữ quyền kiểm duyệt tối cao và chịu trách nhiệm về bản quyền âm nhạc.
    - **Tình huống thực tế bất khả kháng**:
      - Trong quá trình phát trực tiếp, mạng của chủ phòng có thể bị chập chờn (lag giật), vô tình tuột dây mạng, hoặc điện thoại/laptop hết pin đột ngột:
        - *Nếu đóng phòng ngay lập tức*: Trải nghiệm quá tồi tệ, vì có thể chỉ sau 10 giây chủ phòng đã cắm sạc hoặc kết nối lại được Wi-Fi.
        - *Nếu cứ để phòng mở vô tận*: Khi chủ phòng đã đi ngủ hoặc bỏ cuộc họp, phòng sẽ biến thành một "phòng ma" không người quản lý, mọi người tự do bật nhạc bừa bãi làm tiêu tốn tài nguyên máy chủ.
  * **Vấn đề / Thách thức kỹ thuật**:
    - Làm thế nào để vừa cho chủ phòng cơ hội kết nối lại mà không làm gián đoạn buổi họp, vừa đảm bảo phòng không bị chiếm quyền kiểm soát khi chủ vắng mặt?
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Cơ chế Thời gian ân hạn (`ownerGraceSeconds`)**:
      - Khi tạo phòng, hệ thống cấp một khoảng thời gian ân hạn (mặc định là **60 giây**, cấu hình được từ 30s đến 300s).
      - Ngay khi phát hiện socket của chủ phòng bị ngắt kết nối, hệ thống ghi nhận mốc: `owner_left_at = Instant.now()`.
    - **Cơ chế Đóng băng âm thanh phòng hộ (Audio Freeze)**:
      - Ngay khoảnh khắc chủ phòng vắng mặt, bài hát đang phát **tự động chuyển sang trạng thái Tạm dừng (`PAUSED`)**.
      - Phân quyền điều khiển lúc này bị thắt chặt: Thành viên bình thường trong phòng chỉ có thể bấm Pause, tuyệt đối **không ai được phép bấm Play hay chọn bài hát mới** (`canStartAudio = false`). Quy tắc này ngăn chặn triệt để việc kẻ xấu lợi dụng lúc chủ vắng nhà để phát nhạc lậu hoặc nội dung độc hại.
    - **Hai kịch bản kết thúc**:
      - *Kịch bản 1 (Chủ phòng quay lại kịp thời)*: Nếu chủ phòng kết nối lại trước 60 giây, cột `owner_left_at` lập tức được xóa về `null`. Chủ phòng lấy lại quyền điều khiển và bấm Play tiếp tục buổi nghe nhạc.
      - *Kịch bản 2 (Quá hạn ân hạn)*: Bộ lập lịch tự động `OwnerAbsentAutoEndScheduler` quét thấy `now - owner_left_at > ownerGraceSeconds`, lập tức kích hoạt thủ tục giải tán phòng với lý do: `EndedReason.OWNER_ABSENT_TIMEOUT`.

  ---

  #### 5. Lý Thuyết Phân Quyền Kiểm Duyệt & Nhật Ký Kiểm Toán Bất Khả Biến (`admin_actions`)

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Nguyên tắc quyền hạn tối thiểu (Principle of Least Privilege)**: Trong một không gian cộng tác, các hành động can thiệp vào quyền tự do của người khác (như tắt mic, đuổi khỏi phòng) là những hành động nhạy cảm, chỉ được cấp cho người sở hữu phòng.
    - **Tính bất khả chối bỏ (Non-Repudiation) & Kiểm toán (Audit Trail)**: Mọi thao tác hành chính đều phải để lại dấu vết vĩnh viễn trong cơ sở dữ liệu để giải quyết tranh chấp khi người dùng khiếu nại (ví dụ: *"Tại sao tôi đang nghe nhạc đàng hoàng lại bị đuổi?"*).
  * **Vấn đề / Thách thức kỹ thuật**:
    - Làm thế nào để ngăn chặn tuyệt đối các hành vi leo thang đặc quyền (Privilege Escalation) và lưu trữ nhật ký kiểm duyệt mà không làm chậm hệ thống?
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Chốt chặn phân quyền thép tại Use Case**:
      - Tại `RemoteMuteParticipantUseCase` và `KickParticipantUseCase`, hệ thống bắt buộc kiểm tra: `requireRoomOwner(room, actorId)`. Thành viên thường gửi frame STOMP yêu cầu tắt mic người khác sẽ bị ném ngoại lệ `UNAUTHORIZED_MODERATION` ngay tại cửa vào.
      - Chặn đứng lỗi logic tự hại: Chủ phòng **tuyệt đối không thể tự kick chính mình** (`actorId.equals(targetUserId) -> CANNOT_KICK_OWNER`).
    - **Bảng nhật ký kiểm toán bất khả biến (`liveroom_admin_actions`)**:
      - Mỗi khi chủ phòng bấm Duyệt, Từ chối, Tắt mic hoặc Đuổi người, hệ thống tự động ghi một bản ghi vào bảng `liveroom_admin_actions`:
        - `room_id`, `cycle_id`: Phiên xảy ra hành động.
        - `actor_id`: Người ra tay (Chủ phòng).
        - `target_user_id`: Người chịu hình phạt.
        - `action_type`: Loại hành động (`MUTE_MIC`, `KICK`, `APPROVE_JOIN`, `REJECT_JOIN`).
        - `created_at`: Thời gian chính xác đến mili-giây.
      - Bảng này là **Append-Only (Chỉ thêm mới, không sửa, không xóa)**. Nó cung cấp bằng chứng minh bạch 100% để đội ngũ quản trị viên hệ thống có thể điều tra nếu xảy ra khiếu nại về hành vi lạm quyền của chủ phòng.

  ---

  #### Tóm Tắt Bỏ Túi Để Trả Lời Phỏng Vấn (Elevator Pitch)

  Nếu người phỏng vấn hỏi: *"Làm thế nào bạn kiểm soát việc vào phòng, sức chứa và kiểm duyệt người dùng trong hệ thống Live Room?"*, sếp có thể trả lời đầy đẳng cấp và sắc bén như sau:

  > *"Tại Module Live Room của PWB_MiNi, cơ chế kiểm soát vào phòng và quản trị sức chứa được thiết kế theo các tiêu chuẩn an ninh và toàn vẹn dữ liệu khắt khe nhất:
  > 
  > 1. **Chống Race Condition bằng Khóa bi quan (`SELECT FOR UPDATE`)**: Sức chứa phòng bắt buộc dùng khóa bi quan tại tầng Database để đảm bảo khi hàng chục người cùng xin vào ở slot cuối cùng, phòng tuyệt đối không bao giờ bị tràn quá giới hạn 7 người.
  > 2. **Xử lý đối xứng hai cửa vào phòng**: Nhận diện rạch ròi 2 luồng vào phòng (Cửa trước tự vào cho khách quen vs Cửa sau do chủ duyệt), bảo đảm các sự kiện đồng bộ âm thanh và kết nối WebRTC được phát đầy đủ ở cả 2 cửa, loại bỏ hoàn toàn lỗi khách vào phòng bị điếc nhạc.
  > 3. **Hình phạt bằng Mốc thời gian Deadline**: Lưu án phạt tắt mic (30s) và cấm vào lại (5 phút) bằng dấu thời gian hết hạn thay vì cờ Boolean, giúp tự động hết hạn mà không tốn Background Job, đồng thời triệt tiêu trò lách luật thoát ra vào lại.
  > 4. **Giám sát chủ phòng vắng mặt (Grace Period)**: Cho chủ phòng 60 giây ân hạn để kết nối lại nếu rớt mạng, đồng thời tự động đóng băng âm thanh để ngăn chặn người ngoài bật nhạc bừa bãi khi chủ vắng mặt.
  > 5. **Kiểm toán bất khả biến (`admin_actions`)**: Mọi thao tác tắt mic, đuổi người của chủ phòng đều được ghi nhận vào nhật ký kiểm toán Append-Only, đảm bảo tính minh bạch và bảo vệ an toàn cho phòng cộng tác."*

- **Chi Tiết Phần 4: Đồng Bộ Phát Nhạc Chính Xác Tới Từng Giây (Audio Playback Sync & Anchor Timestamp)**:

  Khi phỏng vấn về các bài toán đồng bộ thời gian thực (Realtime Synchronization), câu hỏi của nhà tuyển dụng thường đào sâu vào **mô hình dữ liệu biểu diễn thời gian (time representation), giải quyết độ lệch đồng hồ (clock drift / skew), kiểm soát tranh chấp đa người dùng (concurrency control) và cơ chế tự phục hồi phía client (self-healing & audio drift compensation)**. Sếp có thể tự tin dẫn dắt qua **5 luận điểm kỹ thuật cốt lõi**:

  ---

  #### 1. Lý Thuyết Vị Trí Neo (Anchor Timestamp): Tại Sao Không Thể Lưu Vị Trí Chạy Vào Database?

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Bản chất của một bài hát đang phát**:
      - Thời gian phát của một bản nhạc là một đại lượng biến thiên liên tục theo từng phần nghìn giây ($t_1 = 10.001s, t_2 = 10.002s...$).
    - **Sai lầm kinh điển của cách làm ngây thơ (Tick Job Pattern)**:
      - Rất nhiều lập trình viên khi mới làm tính năng này thường nghĩ: *"Cứ mỗi 1 giây, server sẽ chạy một Background Job tăng `current_second = current_second + 1`, lưu vào Database rồi bắn WebSocket cho các client"*.
      - **Thảm họa sập hệ thống**: 
        - Nếu có 1.000 phòng Live Room đang hoạt động: Mỗi giây máy chủ phải thực thi **1.000 câu lệnh `UPDATE` liên tục xuống Database**! Cơ sở dữ liệu sẽ bị cạn kiệt I/O và nghẽn hàng đợi ghi chỉ để cập nhật một con số chạy vô bổ.
        - **Sai số mạng tích lũy**: Gói tin bay từ Server qua Internet đến Client mất từ 50ms đến 300ms. Đến lúc Client nhận được con số "giây thứ 10" thì ở Server bài hát đã chạy tới "giây thứ 10.3", khiến âm thanh của các máy luôn bị lệch nhịp nhau.
  * **Vấn đề / Thách thức kỹ thuật**:
    - Làm thế nào để 7 người ở 7 thành phố khác nhau cùng nghe chuẩn xác tới từng mili-giây của bài hát, mà **Server không cần chạy bất kỳ Background Job nào và không tốn một câu lệnh ghi Database nào mỗi giây**?
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Mô hình Vị trí neo tĩnh (Anchor Timestamp Model)**:
      - Cơ sở dữ liệu **tuyệt đối không bao giờ lưu vị trí đang chạy**. Trong bảng `liveroom_playback_states`, nó chỉ lưu đúng 2 giá trị tĩnh:
        1. `position_seconds`: Mốc thời gian của bài hát (tính bằng giây) **ngay tại thời điểm bắt đầu chạy**.
        2. `started_at`: Thời điểm bắt đầu chạy từ mốc đó (dấu thời gian `Instant` của Server). Khi bài hát tạm dừng (`PAUSED`), cột này mang giá trị `null`.
    - **Công thức suy diễn vị trí động**:
      - Khi bất kỳ ai cần biết bài hát đang ở giây thứ mấy, hệ thống không đọc con số chạy mà tính toán tức thời bằng công thức toán học:
        $$\text{Vị trí hiện tại} = \text{position\_seconds} + \text{Duration}(\text{started\_at} \rightarrow \text{now})$$
    - **Quy tắc 3 thao tác điều khiển âm thanh**:
      - *Tạm dừng (Pause)*: Kết tinh vị trí đang chạy thành một con số tĩnh mới: $\text{position\_seconds} = \text{Vị trí hiện tại}$, và gán $\text{started\_at} = \text{null}$.
      - *Phát tiếp (Resume)*: Giữ nguyên `position_seconds`, đặt lại mốc thời gian $\text{started\_at} = \text{now}$.
      - *Tua nhạc (Seek)*: Đặt $\text{position\_seconds} = \text{Vị trí mới muốn tua}$, và đặt $\text{started\_at} = (\text{nếu đang phát ? now : null})$.
    - **Khóa trần độ dài bài hát**: Sử dụng hàm `Math.min(position, songDurationSeconds)` để ngăn chặn tình trạng một phòng nghe để quên qua đêm bị nhảy lên giây thứ 30.000 của một bài hát chỉ dài 3 phút!
    - **Hiệu quả**: Không tốn 1% CPU cho Job định kỳ, không tốn I/O Database, và vị trí luôn chính xác tuyệt đối tới từng mili-giây ở mọi thời điểm tra cứu.

  ---

  #### 2. Lý Thuyết Lệch Đồng Hồ Máy Tính (Clock Drift) & Cơ Chế Bù Trừ Tự Động Phía Client

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Hiện tượng lệch đồng hồ giữa các thiết bị (Clock Skew / Clock Drift)**:
      - Mỗi máy tính cá nhân, điện thoại di động đều có bộ đếm thời gian riêng phụ thuộc vào pin CMOS, dao động thạch anh và cấu hình múi giờ của hệ điều hành. Việc đồng hồ máy tính của người dùng chạy nhanh hơn hoặc chậm hơn đồng hồ Server từ vài giây đến vài chục giây là chuyện hoàn toàn bình thường.
    - **Hậu quả khi nghe nhạc chung**:
      - Giả sử Server phát lệnh: *"Bài hát bắt đầu chạy lúc `started_at = 20:00:00`"*.
      - Máy của User A chạy nhanh hơn Server 5 giây (đồng hồ máy A đang là `20:00:05`): Máy A tính ra bài hát đã chạy được 5 giây.
      - Máy của User B chạy chậm hơn Server 5 giây (đồng hồ máy B đang là `19:59:55`): Máy B thấy thời gian hiện tại còn chưa tới thời điểm bắt đầu nên đứng im không phát.
      - **Kết quả**: Hai người ngồi cùng một phòng nghe nhưng âm thanh bị lệch nhau tới 10 giây, phá hủy hoàn toàn cảm giác "nghe nhạc cùng nhau"!
  * **Vấn đề / Thách thức kỹ thuật**:
    - Làm thế nào để mọi trình duyệt của người dùng đều nhìn vào cùng một đồng hồ quy chiếu thống nhất mà không cần cài đặt các giao thức đồng bộ giờ phức tạp như NTP (Network Time Protocol)?
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Tận dụng Header thời gian trong mọi Frame STOMP**:
      - Thay vì mở một API riêng để lấy giờ Server, hệ thống quy chuẩn: Mọi gói tin sự kiện phát ra từ Server (`RoomEvent`) đều được gắn kèm dấu thời gian chuẩn của Server: `timestamp = Instant.now()`.
    - **Thuật toán tính độ lệch đồng hồ tại `server-clock.ts`**:
      - Ngay khi nhận được bất kỳ frame nào từ WebSocket, Client lấy dấu thời gian của Server (`serverMs`) trừ đi đồng hồ nội bộ của máy mình (`Date.now()`) để ra khoảng lệch:
        $$\text{offsetMs} = \text{serverMs} - \text{Date.now()}$$
      - Từ thời điểm đó trở đi, mỗi khi Client cần tính toán vị trí bài hát, Client không dùng hàm `Date.now()` trần trụi mà dùng hàm đồng hồ quy chiếu:
        $$\text{currentServerTime} = \text{Date.now()} + \text{offsetMs}$$
    - **Đánh đổi kiến trúc thông minh**: Vì trong phòng Live Room các sự kiện (chat, nhạc, con trỏ) bắn về liên tục, độ lệch `offsetMs` được tái hiệu chuẩn liên tục. Sai số duy nhất chỉ là thời gian truyền gói tin một chiều trên mạng (vài chục mili-giây) — hoàn toàn không thể nhận biết được bằng tai người đối với âm thanh bài hát.

  ---

  #### 3. Lý Thuyết Khóa Lạc Quan (`@Version`) & Kỹ Thuật Bắt Ngoại Lệ Ngoài Transaction

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Đối chiếu triết lý với Phần 3**:
      - Ở Phần 3, sức chứa phòng dùng **Khóa bi quan (`SELECT FOR UPDATE`)** vì việc vượt quá 7 người sẽ làm sập mạng WebRTC, không thể sửa sai nên người đến sau bắt buộc phải xếp hàng chờ.
      - Nhưng ở bài toán điều khiển nhạc: Hai người cùng lúc bấm nút tua bài hát tại cùng một mili-giây là trường hợp **cực kỳ hiếm gặp**. Nếu có xảy ra, người thao tác sau chỉ cần nhận thông báo thao tác thất bại và tự động đồng bộ theo người thao tác trước, hoàn toàn không gây nguy hại cho hệ thống. Do đó, áp dụng **Khóa lạc quan (Optimistic Locking)** là giải pháp hoàn hảo để đạt hiệu năng đọc/ghi tối đa mà không khóa nghẽn Database.
  * **Vấn đề / Thách thức kỹ thuật**:
    - Khi hai người cùng bấm tua bài tại cùng một thời điểm: Làm thế nào để phát hiện xung đột?
    - Và một cái bẫy kỹ thuật kinh điển của Spring/JPA: Ngoại lệ khóa lạc quan (`OptimisticLockingFailureException`) chỉ xảy ra lúc **COMMIT Transaction** (khi hàm use case đã chạy xong). Làm thế nào để bắt được lỗi này và gửi thông báo êm thấm về cho đúng người thao tác lỗi mà không làm sập kết nối WebSocket?
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - Bảng `liveroom_playback_states` trang bị cột `version` kiểu số nguyên.
    - Khi User A và User B cùng gửi lệnh tua nhạc: Cả 2 luồng đều đọc được `version = 5`.
      - Luồng A commit trước: Lưu vị trí mới và tăng `version` lên 6 thành công.
      - Luồng B commit sau: Hibernate phát hiện số `version` trong Database đã là 6, khác với số 5 mà B đang giữ. Hibernate lập tức chặn đứng lệnh ghi và ném ngoại lệ `OptimisticLockingFailureException`.
    - **Bắt ngoại lệ tại `LiveroomStompExceptionHandler`**:
      - Vì ngoại lệ này nổ ra ngoài biên giới Transaction của Use Case, khối `try/catch` trong Controller thông thường hoàn toàn bất lực.
      - Hệ thống bắt ngoại lệ tại tầng đón lỗi tập trung của WebSocket: `LiveroomStompExceptionHandler`.
      - Bộ xử lý này nhận diện lỗi xung đột phiên bản, chuyển đổi thành mã lỗi nghiệp vụ `LR_075 (MUSIC_STATE_CONFLICT)`.
      - **Điểm tinh tế**: Lỗi này được gửi riêng vào kênh cá nhân `/user/queue/liveroom/errors` của đúng người bấm chậm hơn (User B), kèm thông điệp *"Trạng thái bài hát vừa được người khác cập nhật"*. Mọi người khác trong phòng không hề bị quấy rầy, kết nối WebSocket của User B vẫn sống nguyên vẹn.

  ---

  #### 4. Lý Thuyết State-Based Event Payload & Đảm Bảo Thứ Tự Sự Kiện Bằng `sequence_number`

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Hai trường phái truyền tải sự kiện thời gian thực**:
      1. *Delta-Based (Gửi phần thay đổi)*: Ví dụ: gửi gói tin mang nội dung *"Vừa tua thêm 5 giây"*. Nhược điểm chí mạng: Nếu Client bị chập chờn mạng mất đúng gói tin này, vị trí bài hát trên máy Client sẽ bị lệch vĩnh viễn vì thiếu mất một bước cộng dồn!
      2. *State-Based (Gửi toàn bộ trạng thái)*: Mỗi gói tin bắn ra đều chở **toàn bộ bức tranh trạng thái đầy đủ**: ID bài hát, tên bài hát, trạng thái Play/Pause, mốc `position_seconds`, thời điểm `started_at` và âm lượng hiện tại.
    - **Rủi ro gói tin đến lộn xộn (Out-of-Order Delivery)**:
      - Do các tuyến định tuyến trên Internet thay đổi liên tục, Gói tin số 1 gửi lúc `20:00:01` có thể bay vòng vèo và đến sau Gói tin số 2 gửi lúc `20:00:02`.
      - Nếu Client cứ nhận được tin là áp dụng mù quáng: Gói tin số 1 đến muộn sẽ ghi đè lên gói tin số 2, khiến bài hát đang phát bỗng dưng bị nhảy giật lùi về quá khứ!
* **Vấn đề / Thách thức kỹ thuật**:
  - Làm thế nào để bảo vệ Client tự phục hồi sau khi rớt mạng, và tuyệt đối không bao giờ bị giật lùi trạng thái khi gói tin đến sai thứ tự?
* **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
  - **Chuẩn hóa State-Based Payload**: Mọi sự kiện phát ra trên kênh `/topic/liveroom/{roomId}/music` đều chở trọn vẹn toàn bộ trạng thái của bài hát.
  - **Cơ chế số thứ tự tuần tự (`sequence_number`)**:
    - Trong Database, bảng `liveroom_playback_states` duy trì cột `sequence_number` tự động tăng thêm 1 sau mỗi lần có ai đó Play, Pause, Seek hay đổi bài.
    - Phía Frontend Client duy trì một biến trạng thái: `lastSeenSequenceNumber`.
    - Khi có bất kỳ gói tin STOMP nào bay tới, Client kiểm tra ngay:
      $$\text{Nếu } \text{frame.sequenceNumber} \le \text{lastSeenSequenceNumber} \rightarrow \text{VỨT BỎ GÓI TIN NGAY LẬP TỨC!}$$
    - **Lợi ích kép**:
      1. *Chống giật lùi*: Gói tin cũ đến trễ bị loại bỏ ngay tại cửa vào, không thể làm hỏng trạng thái mới.
      2. *Tự chữa lành (Self-Healing)*: Người dùng bị rớt mạng 10 giây, bỏ lỡ 5 gói tin ở giữa; ngay khi có mạng lại, chỉ cần nhận được đúng 1 gói tin mới nhất là toàn bộ giao diện và bài hát tự động đồng bộ chuẩn xác với cả phòng mà không cần tải lại trang.

  ---

  #### 5. Lý Thuyết Thuật Toán Tua Bù Lệch Nhịp Phía Trình Duyệt (Drift Compensation & Web Audio API)

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Hiện tượng trôi nhịp âm thanh trên trình duyệt (Audio Drift)**:
      - Trình duyệt phát nhạc thông qua thẻ HTML5 `<audio>`. Quá trình giải mã âm thanh chạy trên luồng xử lý riêng của trình duyệt và phụ thuộc vào card âm thanh phần cứng.
      - Sau 10 đến 15 phút nghe nhạc liên tục, do hiện tượng nghẽn CPU tạm thời hoặc chênh lệch tần số lấy mẫu phần cứng, con trỏ thời gian nội bộ của trình duyệt (`audio.currentTime`) thường bị **trôi lệch từ 0.3s đến 1s** so với mốc thời gian toán học chuẩn của Server.
    - **Cái bẫy "Vấp đĩa liên tục"**:
      - Nếu lập trình viên xử lý thô thiển: Cứ mỗi 500ms lại chạy hàm ép buộc `audio.currentTime = serverCalculatedPosition`, bài hát sẽ liên tục bị khựng lại một vài mili-giây để tua lại vị trí $\rightarrow$ Âm thanh bị vấp đĩa, giật cục liên tục, tạo ra trải nghiệm tra tấn người nghe!
* **Vấn đề / Thách thức kỹ thuật**:
  - Làm thế nào để âm thanh trên trình duyệt tự động đuổi kịp mốc thời gian chuẩn của Server một cách êm ái mà người nghe nhạc không hề nhận ra sự can thiệp?
* **Cơ chế kỹ thuật chúng ta xử lý (tại `use-playback-position.ts`)**:
  - Hệ thống cài đặt thuật toán **Bù lệch nhịp thông minh 3 tầng (3-Tier Drift Compensation)**:
    - Định kỳ mỗi giây, Client đo khoảng cách lệch:
      $$\Delta = |\text{audio.currentTime} - \text{serverCalculatedPosition}|$$
    - **Tầng 1: Vùng dung sai an toàn ($\Delta < 0.3$ giây)**:
      - Hệ thống hoàn toàn không can thiệp! Tai người không thể phân biệt được độ lệch dưới 300ms trong môi trường nghe nhạc qua mạng. Bài hát tiếp tục trôi tự nhiên, giữ trọn vẹn chất lượng âm thanh gốc.
    - **Tầng 2: Vùng vi chỉnh êm ái ($0.3 \le \Delta \le 1.5$ giây)**:
      - Tuyệt đối không dùng lệnh gán `currentTime`. Thay vào đó, Client tinh chỉnh nhẹ tốc độ phát của trình duyệt:
        - Nếu bài hát đang bị chậm hơn Server: Tăng tốc độ phát lên một chút ($\text{playbackRate} = 1.05$).
        - Nếu bài hát đang chạy nhanh hơn Server: Giảm tốc độ phát xuống một chút ($\text{playbackRate} = 0.95$).
      - Bài hát sẽ âm thầm chạy nhanh hơn hoặc chậm hơn một chút trong 2-3 giây để bắt kịp mốc chuẩn của Server rồi tự động trả tốc độ về $1.00$. Người nghe hoàn toàn không nhận ra cao độ hay nhịp điệu bị thay đổi!
    - **Tầng 3: Vùng tua cứng ($\Delta > 1.5$ giây)**:
      - Chỉ kích hoạt khi có biến động lớn (như ai đó bấm nút tua bài đi xa, hoặc máy vừa mở lại sau khi mất mạng). Lúc này Client mới thực thi lệnh gán trực tiếp `audio.currentTime = serverCalculatedPosition` để nhảy ngay lập tức tới đoạn nhạc mới.

  ---

  #### Tóm Tắt Bỏ Túi Để Trả Lời Phỏng Vấn (Elevator Pitch)

  Nếu người phỏng vấn hỏi: *"Làm thế nào bạn thiết kế tính năng nghe nhạc đồng bộ thời gian thực cho nhiều người dùng trong Live Room?"*, sếp có thể trả lời đầy đẳng cấp và tự tin như sau:

  > *"Tại Module Live Room của PWB_MiNi, tính năng nghe nhạc đồng bộ thời gian thực được thiết kế dựa trên 5 giải pháp kiến trúc đỉnh cao:
  > 
  > 1. **Mô hình Vị trí neo (Anchor Timestamp)**: Database không bao giờ lưu vị trí đang chạy, mà chỉ lưu mốc `position_seconds` tĩnh và thời điểm bắt đầu `started_at`. Vị trí thực tế được suy diễn theo thời gian thực, triệt tiêu 100% nhu cầu chạy Background Job và không tốn một câu lệnh ghi DB nào mỗi giây.
  > 2. **Bù trừ lệch đồng hồ máy tính (`server-clock`)**: Tận dụng timestamp trên mọi frame STOMP từ Server để Client tự tính `offsetMs`, tạo ra một đồng hồ quy chiếu thống nhất cho mọi người dùng mà không cần cài đặt giao thức NTP.
  > 3. **Kiểm soát tranh chấp bằng Khóa lạc quan (`@Version`)**: Áp dụng khóa lạc quan cho trạng thái phát nhạc để tối ưu hiệu năng; xử lý lỗi xung đột phiên bản tập trung tại `LiveroomStompExceptionHandler` và gửi thông báo riêng cho người thua cuộc mà không làm ảnh hưởng đến cả phòng.
  > 4. **Khả năng tự phục hồi với State-Based Payload & `sequence_number`**: Bắn toàn bộ bức tranh trạng thái trong mỗi gói tin kèm số thứ tự tự tăng; Client tự động vứt bỏ các gói tin cũ đến muộn và tự chữa lành trạng thái ngay khi có mạng trở lại.
  > 5. **Thuật toán bù lệch nhịp êm ái (Audio Drift Compensation)**: Phân loại độ lệch thành 3 tầng: bỏ qua nếu lệch dưới 300ms, vi chỉnh tốc độ phát $1.05x/0.95x$ nếu lệch nhẹ, và chỉ tua cứng khi lệch trên 1.5s, đảm bảo bài hát đồng bộ tuyệt đối mà không bao giờ bị vấp đĩa giật cục."*

- **Chi Tiết Phần 5: Signaling Server Cho WebRTC Full-Mesh (Video/Audio Chat P2P)**:

  Khi phỏng vấn về các giải pháp truyền thông thời gian thực (P2P Audio/Video Communications), nhà tuyển dụng thường kiểm tra sâu về **mô hình mạng (Mesh vs SFU/MCU), vai trò thực tế của backend (signaling mechanics), an ninh mạng và bảo vệ dữ liệu nhạy cảm (NAT traversal credentials & IP leakage)**. Sếp có thể tự tin dẫn dắt qua **5 luận điểm kỹ thuật cốt lõi**:

  ---

  #### 1. Lý Thuyết WebRTC P2P & Bản Chất "Bưu Điện Có Kiểm Tra Giấy Tờ" Của Backend

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **WebRTC (Web Real-Time Communication) là gì?**:
      - Là tiêu chuẩn mở của thế giới web cho phép hai trình duyệt truyền trực tiếp âm thanh, hình ảnh video với nhau theo mô hình **Ngang hàng (Peer-to-Peer - P2P)** với độ trễ cực thấp (dưới 100 mili-giây) mà **không cần dữ liệu media phải đi qua máy chủ Backend**.
    - **Nghịch lý bắt tay P2P qua Internet**:
      - Hai máy tính cá nhân ở hai đầu thế giới không thể tự nhiên "nhìn thấy" nhau để nối dây. Chúng bị ngăn cách bởi các thiết bị mạng NAT (Network Address Translation) và Tường lửa (Firewall). Máy tính của bạn không hề biết địa chỉ IP thực tế và cổng mạng (port) của người kia.
    - **Quá trình Signaling (Bắt tay viễn thông)**:
      - Để bắt tay, hai trình duyệt buộc phải nhờ một bên thứ ba đứng giữa làm trung gian chuyển giúp hai bức thư:
        1. **SDP (Session Description Protocol)**: Bức thư mô tả năng lực thiết bị (ví dụ: *"Tôi hỗ trợ chuẩn âm thanh Opus, camera phân giải 720p, mã hóa VP8/H.264..."*). Có 2 loại: `Offer` (Lời mời) và `Answer` (Lời đồng ý).
        2. **ICE Candidate (Ứng viên đường mạng)**: Bức thư chứa danh sách các "tọa độ mạng" khả dĩ mà máy có thể liên lạc được (ví dụ: IP nội bộ mạng Wi-Fi `192.168.1.15`, hoặc IP công khai `113.160.x.x` kèm port).
  * **Vấn đề / Thách thức kỹ thuật**:
    - Máy chủ Backend của chúng ta đóng vai trò gì trong luồng WebRTC này? Có cần xử lý hay giải mã âm thanh/video của người dùng không?
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Bản chất Backend: Chỉ là một "Bưu điện chuyển thư có kiểm tra giấy tờ" (Signaling Relay)**:
      - Backend hoàn toàn **không chạm vào bất kỳ 1 byte âm thanh hay hình ảnh video nào**! Sau khi hai trình duyệt bắt tay xong, toàn bộ luồng âm thanh/video sẽ bay thẳng trực tiếp qua kết nối P2P giữa hai máy tính.
      - Vai trò duy nhất của Backend là lắng nghe các frame STOMP mang tín hiệu SDP và ICE Candidate từ Client A (qua `/app/liveroom/{id}/rtc/offer`, `/answer`, `/ice`), đóng dấu kiểm tra hợp lệ, rồi chuyển phát nhanh tới đúng Client B qua kênh riêng.
      - Nhờ triết lý này, máy chủ Backend cực kỳ nhẹ tải, không tốn tài nguyên CPU xử lý video, và không phải trả hóa đơn băng thông khổng lồ cho việc streaming video trực tiếp.

  ---

  #### 2. Lý Thuyết Kiến Trúc Mạng Full-Mesh & Bí Ẩn Con Số Sức Chứa Mặc Định 7 Người

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Ba mô hình kiến trúc gọi video nhóm phổ biến**:
      1. *MCU (Multipoint Control Unit)*: Mọi người gửi video lên Server. Server giải mã, dùng CPU ghép chung thành một khung hình lớn (kiểu camera an ninh) rồi gửi về cho từng người $\rightarrow$ Tiêu tốn CPU máy chủ khủng khiếp, chi phí hạ tầng cực đắt.
      2. *SFU (Selective Forwarding Unit)*: Mỗi người gửi 1 luồng video lên Media Server chuyên dụng (như Kurento, Janus). Server đóng vai trò bộ định tuyến nhân bản luồng đó sang cho những người khác $\rightarrow$ Đòi hỏi phải xây dựng cụm Media Server riêng biệt và tốn nhiều băng thông máy chủ.
      3. *Full-Mesh (Mạng lưới toàn phần)*: Từng người tự mở kết nối P2P trực tiếp tới **tất cả những người còn lại trong phòng**! Không cần bất kỳ Media Server trung gian nào, chi phí máy chủ bằng $0$.
    - **Công thức bùng nổ kết nối trong Full-Mesh**:
      - Với phòng có $n$ người, tổng số kết nối P2P hình thành trong phòng là:
        $$\text{Tổng số kết nối} = \frac{n(n - 1)}{2}$$
      - Mỗi người tham gia phải tự gánh trách nhiệm: Mã hóa và tải lên đồng thời **$(n - 1)$ luồng video** cho $(n - 1)$ người còn lại!
  * **Vấn đề / Thách thức kỹ thuật**:
    - Tại sao trong `LiveroomConfig`, sức chứa mặc định của phòng Live Room lại được thiết kế khóa cứng ở con số **7 người**?
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Bảng đối chiếu quy mô mạng Full-Mesh**:
      - Khi phòng có 2 người: Chỉ có 1 kết nối duy nhất (mỗi người gửi 1 luồng).
      - Khi phòng có 4 người: Có 6 kết nối (mỗi người gửi 3 luồng).
      - Khi phòng đạt **7 người**: Có tới **21 kết nối P2P** đồng thời trong phòng, và **mỗi người phải upload cùng lúc 6 luồng video**!
    - **Giới hạn băng thông đường lên của người dùng (Uplink Bandwidth)**:
      - Đường truyền Internet gia đình hoặc 4G của người dùng thông thường chỉ có băng thông tải lên (Upload Speed) dao động từ 10Mbps đến 20Mbps.
      - Với 6 luồng video 720p/480p chạy song song, đường truyền của người dùng đã chạm tới ngưỡng bão hòa tuyệt đối. Nếu mở rộng sức chứa lên 10 người (45 kết nối, mỗi người gánh 9 luồng video), CPU của máy tính người dùng sẽ bị quá nhiệt 100% và hình ảnh bị đóng băng giật cục ngay lập tức.
    - Con số **7 người** là "điểm ngọt" (Sweet Spot) được tính toán khoa học: Vừa đủ cho một buổi họp nhóm ấm cúng của ban nhạc/nghệ sĩ, vừa khai thác triệt để lợi thế miễn phí của mạng Full-Mesh mà không làm sập đường truyền của người dùng.

  ---

  #### 3. Lý Thuyết Rò Rỉ Tọa Độ Mạng & Giải Pháp Bảo Mật Bằng User Destination (`/user/queue/liveroom/rtc`)

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Tính nhạy cảm của dữ liệu Signaling**:
      - Các gói tin SDP và đặc biệt là ICE Candidate chứa các thông số mạng tối mật của người dùng: Địa chỉ IP thực tế, địa chỉ IP mạng nội bộ (LAN IP), nhà mạng cung cấp dịch vụ (ISP), và các cổng mạng (ports) mà Firewall đang mở tạm thời.
  * **Vấn đề / Thách thức kỹ thuật**:
    - **Lỗ hổng nghe lén tọa độ mạng (Eavesdropping Vulnerability)**:
      - Trong bản thiết kế sơ khai, hệ thống từng định chuyển tiếp tín hiệu qua topic chung: `/topic/liveroom/{roomId}/rtc/{targetUserId}`.
      - Các kiến trúc sư đã thử nghiệm thực tế và phát hiện một lỗ hổng an ninh chí mạng: Bộ kiểm tra quyền `StompSubscriptionScopeInterceptor` chỉ đọc đoạn đầu của URL (`/topic/liveroom/{roomId}`) để kiểm tra. Vì kẻ xấu thực sự là một thành viên đang ngồi trong phòng, lệnh `SUBSCRIBE` của kẻ xấu vào topic của người khác sẽ **qua mặt được bộ lọc kiểm tra**!
      - Kẻ xấu có thể ngồi im trong phòng và nghe lén toàn bộ địa chỉ IP thực tế của các nghệ sĩ khác trong phòng để thực hiện các cuộc tấn công DDoS vào mạng gia đình của họ!
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Chuyển dịch sang cơ chế An toàn theo cấu tạo (Secure by Design)**:
      - Hệ thống xóa bỏ hoàn toàn việc dùng topic chung cho WebRTC, chuyển 100% luồng Signaling sang kênh riêng tư: `/user/queue/liveroom/rtc`.
    - **Bản chất định tuyến ngầm của Spring Framework**:
      - Khi Client gửi frame tới một người dùng đích, Spring tự động chuyển đổi tiền tố `/user` thành một hàng đợi bí mật gắn liền với danh tính `StompUserPrincipal` của chính người nhận (ví dụ: `/queue/liveroom/rtc-user456`).
      - **Triệt tiêu hoàn toàn nguy cơ nghe lén**: Bất kỳ người nào khác trong phòng, dù có cố tình gửi lệnh `SUBSCRIBE` vào `/user/queue/liveroom/rtc`, Spring cũng chỉ định tuyến về hàng đợi của chính họ. Không một ai trên toàn bộ hệ thống có thể đăng ký lắng nghe hàng đợi của người khác.
      - Kết hợp quy tắc cấm Client dùng lệnh `SEND` vào prefix `/user`, tính riêng tư của tọa độ mạng được bảo vệ tuyệt đối ở mức hạ tầng mà không cần viết thêm các hàm kiểm tra thủ công phức tạp.

  ---

  #### 4. Lý Thuyết Chốt Chặn Bưu Điện (`RtcRelayGuard`) & Rate Limit Chuyên Biệt Cho RTC

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **Nguyên tắc thẩm định người trung chuyển (Relay Verification)**:
      - Một bưu điện an toàn không chỉ chuyển tiếp thư mù quáng, mà phải kiểm tra kỹ thông tin người gửi, người nhận và khối lượng bưu phẩm trước khi phát đi.
  * **Vấn đề / Thách thức kỹ thuật**:
    - **Lỗ hổng mượn đường tấn công DoS**: Nếu Backend chỉ kiểm tra người gửi đang ở trong phòng mà không kiểm tra người nhận: Một kẻ xấu trong phòng có thể gửi liên tục hàng triệu gói tin RTC tới một `targetUserId` của một người lạ hoàn toàn không tham gia phòng (thậm chí là Admin), biến hệ thống thành công cụ spam quấy rối người khác!
    - **Lỗ hổng chuyển lậu dữ liệu**: SDP và ICE Candidate là chuỗi văn bản (String). Nếu không giới hạn kích thước, kẻ xấu có thể nhét mã độc hoặc các tệp tin hình ảnh/video lậu vào trường `sdp` để truyền qua lại mà vượt qua mọi bộ lọc của hệ thống chat.
    - **Nghịch lý Rate Limit của WebRTC**: Khác với chat (người dùng gõ từng câu cách nhau vài giây), trong WebRTC, khi một người vừa bước vào phòng, quy trình thương lượng mạng (Trickle ICE) sẽ **bắn ra hàng chục ứng viên mạng liên tục trong vòng 2-3 giây** cho cả 6 người còn lại. Nếu áp dụng bộ Rate Limit chung của chat (15 frame/10s), toàn bộ cuộc gọi WebRTC sẽ bị bóp chết ngay từ giây đầu tiên!
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Bộ thẩm định 3 bên `RtcRelayGuard`**:
      - Trước khi chuyển tiếp bất kỳ frame nào, use case gọi `verify(roomId, actorId, targetUserId)` để kiểm tra 3 điều kiện đồng thời:
        1. Phòng phải đang ở trạng thái hoạt động bình thường (`requireActiveRoom`).
        2. Người gửi phải đang thực sự ngồi trong phòng (`requireInRoom`).
        3. **Người nhận bắt buộc phải đang có mặt trong phòng (`requireTargetInRoom`)**.
        4. Chặn lỗi tự gửi cho chính mình: `actorId.equals(targetUserId) -> RTC_SELF_SIGNALING`.
    - **Khóa trần kích thước Payload cứng**:
      - Kiểm soát chặt độ dài chuỗi: **SDP tối đa 16.384 ký tự (16KB)**, **ICE Candidate tối đa 1.024 ký tự (1KB)**. Bất kỳ gói tin nào vượt quá trần này đều bị ném lỗi `RTC_PAYLOAD_TOO_LARGE` và loại bỏ ngay lập tức.
    - **Cấp riêng Bucket Token Rate Limit cho RTC (400 frame / 10 giây)**:
      - Tại `StompRateLimitInterceptor`, phân loại luồng riêng cho RTC với hạn mức **400 frame trong 10 giây** (cao hơn chat gấp 26 lần!).
      - Hạn mức 400 frame này được tính toán chuẩn xác để hấp thụ trọn vẹn "cơn bão tín hiệu ICE" của 7 người cùng bắt tay một lúc mà không bao giờ bị nghẽn hay rớt kết nối.

  ---

  #### 5. Lý Thuyết Vượt Tường Lửa (STUN/TURN) & Cơ Chế Cấp Credential Tạm Thời (TTL 24h)

  * **Lý thuyết nền tảng & Bản chất cốt lõi**:
    - **STUN (Session Traversal Utilities for NAT) - Chiếc gương soi**:
      - Máy chủ STUN hoạt động cực kỳ đơn giản và nhẹ nhàng. Trình duyệt gửi một gói tin hỏi STUN Server: *"Tôi là ai?"*. STUN Server nhìn vào gói tin và trả lời: *"Gói tin của bạn bay ra từ địa chỉ IP công khai X và Port Y"*. Nhờ đó trình duyệt biết được tọa độ công khai của mình để đưa vào danh sách ICE Candidate gửi cho bạn bè. STUN server chiếm rất ít tài nguyên và hầu như miễn phí.
    - **TURN (Traversal Using Relays around NAT) - Chiếc cầu cứu sinh**:
      - Trong thực tế, có khoảng **15% đến 20% trường hợp người dùng ngồi sau mạng 4G/5G di động hoặc sau mạng tường lửa nghiêm ngặt của công ty (Symmetric NAT)**. Ở loại mạng này, tường lửa sẽ đổi ngẫu nhiên cổng port cho mỗi đích đến khác nhau, khiến kết nối P2P trực tiếp **hoàn toàn thất bại 100%**.
      - Lúc này, giải pháp duy nhất là phải có một **Máy chủ trung chuyển dữ liệu (TURN Server)** đứng ở giữa: A gửi video lên TURN Server, TURN Server chuyển tiếp video đó sang cho B.
  * **Vấn đề / Thách thức kỹ thuật**:
    - Băng thông truyền video qua TURN Server rất tốn kém chi phí.
    - Nếu cấu hình Username và Password cố định của TURN Server vào file cấu hình Frontend, kẻ xấu chỉ cần mở tab Network của trình duyệt là đọc được tài khoản. Chúng sẽ mang tài khoản đó đi chia sẻ trên mạng hoặc làm proxy xem phim lậu, khiến hóa đơn Cloud của dự án vọt lên hàng nghìn USD!
  * **Cơ chế kỹ thuật chúng ta xử lý (Under The Hood)**:
    - **Cấu hình tách biệt tại `GET /rooms/{id}/rtc/config`**:
      - Endpoint này trả về danh sách ICE Server cho trình duyệt. STUN luôn được bật sẵn mặc định.
      - Cờ `rtc.turn.enabled` cho phép bật/tắt linh hoạt máy chủ TURN tùy theo ngân sách của hệ thống.
  * **Cơ chế sinh thông tin đăng nhập tạm thời (Ephemeral Turn Credentials)**:
    - Khi TURN được kích hoạt, hệ thống không dùng mật khẩu tĩnh mà sử dụng `TurnCredentialFactory` tích hợp thuật toán mã hóa HMAC-SHA1:
      - **Username**: Chứa dấu thời gian hết hạn trong tương lai: `expiryTimestamp:userId`.
      - **Password**: Là chuỗi băm HMAC-SHA1 của Username kết hợp với một khóa bí mật (`turn.secret`) được lưu an toàn ở Backend.
      - **Thời gian sống (TTL)**: Được thiết lập ngắn, **chính xác 24 giờ**.
    - Máy chủ TURN (như Coturn) dùng chung `turn.secret` để đối chiếu chữ ký. Sau 24 giờ, thông tin đăng nhập tự động trở thành phế thải. Toàn bộ hạ tầng mạng viễn thông được bảo vệ tuyệt đối, ngăn chặn 100% nguy cơ bị rò rỉ hoặc lạm dụng băng thông.

  ---

  #### Tóm Tắt Bỏ Túi Để Trả Lời Phỏng Vấn (Elevator Pitch)

  Nếu người phỏng vấn hỏi: *"Làm thế nào bạn thiết kế hạ tầng Signaling cho WebRTC trong hệ thống Live Room?"*, sếp có thể trả lời đầy đẳng cấp và sắc bén như sau:

  > *"Tại Module Live Room của PWB_MiNi, tính năng gọi video/audio chat WebRTC được thiết kế dựa trên 5 nguyên lý kiến trúc viễn thông chuẩn mực:
  > 
  > 1. **Mô hình Bưu điện chuyển thư (Signaling Relay)**: Backend hoàn toàn không xử lý dữ liệu Media âm thanh/video, mà chỉ đóng vai trò trung chuyển các gói tin bắt tay SDP và ICE Candidates qua giao thức STOMP, giúp hệ thống cực kỳ nhẹ tải và tiết kiệm 100% chi phí băng thông máy chủ.
  > 2. **Kiến trúc Full-Mesh tối ưu ở 7 người**: Từng client kết nối P2P trực tiếp với nhau ($21$ kết nối P2P trong phòng); giới hạn sức chứa 7 người là con số khoa học khớp với băng thông tải lên của người dùng phổ thông, loại bỏ nhu cầu tốn kém phải dựng Media Server chuyên dụng (SFU/MCU).
  > 3. **Bảo mật kênh Signaling bằng User Destination (`/user/queue/**`)**: Toàn bộ tọa độ mạng nhạy cảm được gửi về hàng đợi riêng tư theo cấu tạo của Spring, loại bỏ hoàn toàn lỗ hổng nghe lén IP/Port của nhau trên topic phòng.
  > 4. **Chốt chặn an ninh `RtcRelayGuard` & Rate Limit riêng**: Bắt buộc kiểm tra cả người gửi và người nhận đều phải có mặt trong phòng để chống spam DoS người ngoài; khóa trần kích thước SDP (16KB) và cấp riêng hạn mức Rate Limit 400 frame/10s để hấp thụ bão tín hiệu ICE.
  > 5. **Hạ tầng NAT Traversal & Ephemeral TURN Credentials**: Hỗ trợ STUN và TURN cho người dùng sau tường lửa Symmetric NAT, sử dụng thuật toán HMAC-SHA1 cấp thông tin đăng nhập tạm thời có TTL 24 giờ để triệt tiêu hoàn toàn rủi ro bị đánh cắp tài nguyên băng thông."*

---

#### 6. Bộ Lập Lịch Tự Động & Tối Ưu Hóa Trải Nghiệm Client (`Resource Reaper & Client Performance`)

- **Các file mã nguồn cốt lõi**:
  - `LiveRoomSessionScheduler.java` (`com.pwb.liveroom.infrastructure.scheduler`): Bộ lập lịch background tự động thu dọn các phòng rỗng, phòng vắng chủ và giải phóng tài nguyên.
  - `use-liveroom-store.ts` (`frontend/src/features/liveroom/stores/use-liveroom-store.ts`): Single Source of Truth phía client quản lý toàn bộ trạng thái phòng, tách biệt luồng I/O mạng khỏi chu kỳ render React.
  - `room-tab-lock.ts` (`frontend/src/features/liveroom/utils/room-tab-lock.ts`): Cơ chế phân xử độc quyền phòng qua BroadcastChannel chống mở trùng tab, triệt tiêu xung đột Media Device và Echo Loop.
  - `use-audio-level.ts` (`frontend/src/features/liveroom/hooks/use-audio-level.ts`): Đo lường biên độ âm thanh micro tức thời bằng Web Audio API tại client, tạo visualizer mượt mà không tốn băng thông máy chủ.

---

##### Luận điểm 1: Lý thuyết nền tảng & Bản chất cốt lõi: Resource Reaper & Bộ đôi Scheduler dọn rác tài nguyên (Garbage Collection tầng nghiệp vụ)

- **Bản chất vấn đề trong hệ thống Realtime Stateful**:
  - Khác với API Stateless (khi request kết thúc là tài nguyên được GC giải phóng), các ứng dụng WebSocket/WebRTC duy trì **Stateful Sessions** kéo dài.
  - Khi người dùng tắt nguồn đột ngột, sập nguồn máy tính, mất kết nối mạng 4G/Wifi hoặc trình duyệt bị crash bất ngờ, client **hoàn toàn không có cơ hội** gửi gói tin STOMP `DISCONNECT` hoặc `LEAVE_ROOM` lên máy chủ.
  - Nếu máy chủ chỉ thụ động chờ đợi client gửi lệnh thoát, hệ thống sẽ rơi vào thảm họa **"Phòng ma" (Ghost Rooms)** và **"Phiên thây ma" (Zombie Sessions)**:
    1. Phòng vẫn hiển thị trạng thái `ACTIVE` trên giao diện trang chủ, gây trải nghiệm tồi tệ cho người dùng mới bấm vào.
    2. Các thread giữ slot dữ liệu, cache Redis và bộ nhớ RAM máy chủ bị rò rỉ (Resource Leakage) theo thời gian, dẫn đến sập server vì Out-Of-Memory.
- **Cơ chế Under The Hood của bộ đôi Scheduler (`LiveRoomSessionScheduler.java`)**:
  - Hệ thống áp dụng mẫu thiết kế **Resource Reaper** (Thần chết thu gom tài nguyên) chạy định kỳ ở background thông qua Spring `@Scheduled`:
    1. **Task 1: Dọn dẹp phòng rỗng (`reapEmptyRooms`)**:
       - Định kỳ quét các phòng có số lượng người tham gia bằng 0 (`participantCount == 0`) trong một khoảng thời gian vượt quá ngưỡng chết `EMPTY_TIMEOUT` (10 phút).
       - Tự động kích hoạt quy trình hạ cờ trạng thái phòng sang `CLOSED`, thu hồi session âm thanh, giải phóng slot và thông báo đồng bộ tới cụm microservices.
    2. **Task 2: Dọn dẹp phòng vắng chủ (`reapOwnerAbsentRooms`)**:
       - Trong mô hình phòng học/phòng nghe nhạc của PWB_MiNi, chủ phòng (Owner) nắm quyền điều khiển danh sách phát nhạc và điều phối mic. Khi chủ phòng bị disconnect hoặc rớt mạng, phòng không bị đóng ngay lập tức mà rơi vào trạng thái "Chờ chủ kết nối lại" (Owner Reconnection Grace Period).
       - Scheduler liên tục kiểm tra timestamp hoạt động cuối cùng của chủ phòng. Nếu vượt quá ngưỡng `OWNER_ABSENT_TIMEOUT` (60 giây) mà chủ phòng không xuất hiện trở lại, hệ thống sẽ kích hoạt lệnh tự động hủy phòng hoặc kích hoạt cơ chế chuyển giao quyền lực để bảo vệ tính công bằng cho phòng.
  - **Tối ưu hóa Batch Processing chống nghẽn I/O**:
    - Scheduler tuyệt đối không thực hiện câu lệnh `SELECT * FROM rooms` để load hàng chục ngàn bản ghi lên RAM.
    - Truy vấn quét dọn bắt buộc phải chia trang (Pagination Batching) với kích thước cố định (ví dụ 50 phòng/batch) kết hợp index trên các cột `(status, updated_at, participant_count)`. Điều này ngăn chặn triệt để hiện tượng Spike CPU và quá tải bộ nhớ JVM trong mỗi chu kỳ quét.

---

##### Luận điểm 2: Chống va chạm luồng ngầm vs người dùng bằng Double-Check Locking trong Database Transaction

- **Tình huống xung đột hóc búa (Race Condition)**:
  - Giả sử tại giây thứ `59.9`, chủ phòng vừa kết nối mạng thành công và gửi gói tin STOMP `RECONNECT` lên máy chủ (xử lý trên luồng `WebSocket-Inbound-Thread`).
  - Cùng lúc đó, tại giây thứ `60.0`, `LiveRoomSessionScheduler` kích hoạt chu kỳ quét dọn (xử lý trên luồng `Scheduler-Worker-Thread`).
  - Nếu không có cơ chế đồng bộ nghiêm ngặt, cả 2 luồng sẽ cùng thực thi: một luồng cố gắng hồi phục phiên, luồng kia cố gắng đóng phòng và xóa dữ liệu. Hậu quả là người dùng vừa vào lại phòng đã bị hệ thống văng ra và phòng bị xóa oan (False-Positive Eviction).
- **Giải pháp Under The Hood: Pessimistic Locking kết hợp Double-Check Pattern**:
  - Khi Scheduler phát hiện một danh sách các phòng "có khả năng hết hạn", nó không vội vàng thực thi lệnh đóng phòng ngay.
  - Với từng phòng, Scheduler mở một Database Transaction độc lập với cơ chế khóa bi quan:
    ```sql
    SELECT * FROM live_rooms WHERE id = :roomId FOR UPDATE;
    ```
  - **Mô hình Double-Check bên trong vùng an toàn (Critical Section)**:
    1. Khi câu lệnh `FOR UPDATE` chiếm được Exclusive Row Lock trên bảng PostgreSQL, mọi thao tác ghi khác từ WebSocket thread buộc phải chờ đợi.
    2. Scheduler tiến hành kiểm tra lại lần 2 (Double-Check):
       - `room.getOwnerLastSeenAt()` có còn nằm trong khoảng thời gian chết hay không?
       - Trạng thái phòng hiện tại có bị thay đổi bởi luồng WebSocket vừa kịp ghi nhận trước đó hay không?
    3. Nếu phát hiện chủ phòng vừa gửi heartbeat hoặc số lượng người tham gia đã tăng trở lại: Scheduler lập tức hủy bỏ quy trình đóng phòng (Abort / No-op), nhả lock và ghi nhận phòng vẫn còn sống khỏe mạnh.
    4. Chỉ khi điều kiện vi phạm vẫn hoàn toàn đúng sau khi đã khóa độc quyền dòng dữ liệu, Scheduler mới kích hoạt logic đóng phòng và phát event WebSocket `ROOM_CLOSED_BY_TIMEOUT` cho những người còn lại.

---

##### Luận điểm 3: Kiến trúc Zustand Store Phía Client — Single Source of Truth & Tối ưu Render 60 FPS

- **Thách thức kiến trúc trên giao diện người dùng**:
  - Giao diện Live Room là một màn hình phức hợp chứa hàng chục thành phần hiển thị thời gian thực: Video/Audio Grid của các thành viên, thanh tiến trình âm nhạc (Playback Progress Bar), danh sách tin nhắn chat, bảng thông báo hệ thống, và hiệu ứng visualizer âm thanh.
  - Nếu áp dụng giải pháp React Context API hoặc đặt state tại Root Component (`useState`), mỗi khi có một tin nhắn chat bay về, một WebRTC ICE Candidate được trao đổi, hoặc một nhịp tick đồng bộ âm nhạc (mỗi 1 giây 1 lần), toàn bộ cây DOM của Root Component sẽ bị re-render lại từ đầu.
  - Hậu quả: Giao diện bị drop frame (tụt xuống dưới 20-30 FPS), gây hiện tượng giật hình và làm ngắt quãng luồng xử lý Web Audio API (gây tiếng nổ lụp bụp / Audio Glitches).
- **Giải pháp Under The Hood với Zustand (`use-liveroom-store.ts`)**:
  - **Tách biệt hoàn toàn luồng I/O mạng khỏi React Component Tree**:
    - WebSocket Client (STOMP Client) không phụ thuộc vào React Hook lifecycle để nhận dữ liệu.
    - Các listener STOMP được đăng ký trực tiếp và đẩy dữ liệu thẳng vào Zustand Store thông qua Vanilla JavaScript Actions mà không cần kích hoạt bất kỳ một hook React nào.
  - **Mô hình State Slices độc lập**:
    - Toàn bộ trạng thái phòng được chia nhỏ thành các slice chuyên trách: `roomInfoSlice`, `participantsSlice`, `playbackSlice`, `chatSlice`, `webrtcSlice`.
  - **Selective Subscription (Đăng ký chọn lọc) với Selector Pattern**:
    - Các component chỉ subscribe đúng phần dữ liệu nhỏ mà nó thực sự quan tâm:
      ```typescript
      // AudioProgressBar chỉ re-render khi playbackPosition thay đổi, hoàn toàn miễn nhiễm với chat
      const playbackPosition = useLiveRoomStore((state) => state.playbackPosition);
      
      // ChatBox chỉ re-render khi có message mới, không bị ảnh hưởng bởi tiến trình phát nhạc
      const messages = useLiveRoomStore((state) => state.messages);
      ```
    - Nhờ cơ chế shallow compare của Zustand, ngay cả khi các sự kiện STOMP bắn về với tần suất 50-100 frame/giây, chỉ có các component đích thực sự cần đổi giao diện mới bị re-render. Toàn bộ ứng dụng duy trì ổn định ở mức **60 FPS** mượt mà trên cả trình duyệt di động.

---

##### Luận điểm 4: Cơ chế Chống Trùng Tab Bằng Web BroadcastChannel (`room-tab-lock.ts`)

- **Rủi ro khi người dùng mở nhiều tab trong cùng một phòng**:
  - Người dùng vô tình mở 2 tab trình duyệt cùng truy cập vào một URL Live Room.
  - Nếu không có cơ chế chặn:
    1. Cả 2 tab cùng khởi tạo WebRTC `RTCPeerConnection` và cùng bật micro -> Gây ra hiện tượng vòng lặp âm thanh vô tận (Acoustic Feedback / Echo Loops) xé tai tất cả mọi người trong phòng.
    2. Cả 2 tab cùng kết nối STOMP với chung một `userId` -> Máy chủ nhận các frame trùng lặp và các gói tin WebRTC Signaling bị phân mảnh giữa 2 tab, dẫn đến đứt gãy kết nối P2P.
- **Cơ chế Under The Hood: Mutex liên tab qua BroadcastChannel & LocalStorage (`room-tab-lock.ts`)**:
  - Trình duyệt cung cấp Web API `BroadcastChannel` cho phép các ngữ cảnh duyệt web (Tabs, Windows, Iframes) cùng Origin giao tiếp trực tiếp với nhau mà không cần thông qua máy chủ.
  - **Quy trình chiếm giữ Lock (Mutex Acquisition)**:
    1. Khi Tab A mở phòng, nó tạo một kênh riêng biệt: `channel = new BroadcastChannel('room_lock_' + roomId)`.
    2. Tab A sinh một `tabInstanceId` ngẫu nhiên và phát bản tin `CLAIM_LOCK { tabId, timestamp }` qua kênh, đồng thời ghi đè thông tin phiên độc quyền vào `localStorage`.
    3. Tab A khởi tạo nhịp đập Heartbeat (mỗi 2 giây cập nhật timestamp vào `localStorage`).
  - **Quy trình xử lý xung đột khi Tab B mở lên**:
    1. Khi Tab B truy cập vào cùng phòng, nó lắng nghe kênh `BroadcastChannel` và đọc `localStorage`.
    2. Tab B phát hiện phòng này đang bị Tab A chiếm giữ và timestamp của Tab A vẫn còn sống (< 3 giây).
    3. Ngay lập tức, Tab B tự động khóa toàn bộ logic kết nối: không mở WebSocket, không xin quyền Micro/Camera.
    4. Tab B hiển thị một màn hình thông báo chặn thân thiện: *"Bạn đang mở phòng này tại một tab khác. Bạn có muốn chuyển quyền sang tab này không?"*.
  - **Cơ chế cưỡng chế chuyển quyền (Force Takeover / Preemption)**:
    1. Nếu người dùng bấm nút *"Chuyển quyền sang tab này"* trên Tab B, Tab B sẽ phát gói tin `FORCE_TAKEOVER { newTabId }` vào `BroadcastChannel`.
    2. Tab A nhận được gói tin, ngay lập tức giải phóng micro, ngắt kết nối STOMP, nhả lock và chuyển giao diện của mình sang chế độ Inactive.
    3. Tab B lúc này trở thành tab độc quyền duy nhất được phép khởi tạo kết nối Realtime. Triệt tiêu 100% hiện tượng hú tiếng và xung đột luồng dữ liệu.

---

##### Luận điểm 5: Giám sát Âm lượng Micro Tại Chỗ Bằng Web Audio API (`use-audio-level.ts`)

- **Bài toán hiển thị Speaking Indicator**:
  - Trong phòng họp hoặc phòng livestream, việc hiển thị vòng sáng nhấp nháy xung quanh avatar của người đang nói (Speaking Indicator) là tính năng cốt lõi giúp các thành viên nhận diện ai đang phát biểu.
  - **Sai lầm kiến trúc cơ bản**: Nhiều lập trình viên tính toán mức âm lượng trên client rồi liên tục gửi các gói tin WebSocket lên server (`volume: 75%`, `volume: 82%` với tần suất 20 lần/giây) để server broadcast lại cho toàn bộ phòng. Cách làm này sẽ tạo ra cơn bão hàng chục ngàn gói tin mỗi giây, làm nghẽn đường truyền mạng và hạ gục máy chủ ngay lập tức.
- **Giải pháp Edge Computing tại Client (`use-audio-level.ts`)**:
  - Hệ thống áp dụng nguyên tắc: **Tính toán và xử lý đồ họa hoàn toàn tại biên (Client Edge Computing)** bằng cách sử dụng Web Audio API.
  - **Luồng xử lý tín hiệu âm thanh (Audio Pipeline)**:
    1. Khi người dùng cấp quyền micro, hệ thống trích xuất `MediaStreamTrack` âm thanh và đưa vào một `AudioContext`:
       ```javascript
       const source = audioContext.createMediaStreamSource(stream);
       const analyser = audioContext.createAnalyser();
       analyser.fftSize = 256; // Kích thước biến đổi Fourier đủ nhỏ để tối ưu hiệu năng
       source.connect(analyser);
       ```
    2. **Đo lường năng lượng qua `requestAnimationFrame`**:
       - Sử dụng hàm vòng lặp hoạt họa của trình duyệt `requestAnimationFrame` để lấy dữ liệu miền thời gian (Time-Domain Data): `analyser.getByteFrequencyData(dataArray)`.
       - Tính toán giá trị Root Mean Square (RMS) đại diện cho công suất âm thanh tức thời.
    3. **Thuật toán lọc nhiễu và làm mượt (Smoothing & Debounce)**:
       - Cấu hình thuộc tính `analyser.smoothingTimeConstant = 0.8` để biểu đồ sóng âm không bị giật cục hay nhấp nháy loạn xạ khi có tiếng gió hoặc tiếng gõ bàn phím nhỏ.
       - Thiết lập ngưỡng kích hoạt tiếng nói `VOICE_THRESHOLD` (ví dụ: RMS > 15).
    4. **Cơ chế cập nhật trạng thái tối ưu**:
       - Mức âm lượng chi tiết chỉ phục vụ cho việc render thanh sóng âm (Audio Waveform) tại máy cục bộ ở tần số 60 FPS.
       - Khi trạng thái nói thay đổi từ `IM LẶNG` sang `ĐANG NÓI` (hoặc ngược lại), client chỉ gửi một sự kiện nhị phân duy nhất (`isSpeaking: true/false`) kèm theo kỹ thuật Debounce (chỉ tắt trạng thái nói nếu người dùng đã im lặng liên tục 400ms).
       - Nhờ đó, máy chủ và mạng WebRTC P2P chỉ phải nhận một vài sự kiện nhỏ lẻ, trong khi trải nghiệm thị giác của người dùng đạt độ mượt mà tuyệt đối không một mili-giây độ trễ.

---

##### Elevator Pitch (Tóm tắt bỏ túi 1 phút cho phỏng vấn - Phần 6)

> *"Tại Module Live Room của PWB_MiNi, sự ổn định của hệ thống và trải nghiệm mượt mà của người dùng được đảm bảo thông qua 5 giải pháp phối hợp chặt chẽ giữa Backend và Frontend:
> 
> 1. **Mẫu thiết kế Resource Reaper**: Bộ đôi Scheduler chạy ngầm định kỳ quét dọn phòng rỗng sau 10 phút và phòng vắng chủ sau 60 giây theo từng batch nhỏ 50 phòng, ngăn chặn triệt để hiện tượng rò rỉ bộ nhớ và tích tụ các 'phòng ma'.
> 2. **Chống va chạm luồng ngầm bằng Pessimistic Locking & Double-Check**: Scheduler khóa dòng bằng `SELECT ... FOR UPDATE` và kiểm tra lại trạng thái trước khi đóng phòng, đảm bảo không bao giờ đóng nhầm phòng khi người dùng vừa kịp reconnect ở mili-giây cuối cùng.
> 3. **Zustand Store tách biệt luồng mạng khỏi React Render**: Áp dụng Selector Pattern và State Slices độc lập, cho phép STOMP subscriber nạp dữ liệu trực tiếp vào store ngoài React, giữ vững tốc độ khung hình 60 FPS mà không gây giật lag âm thanh.
> 4. **Chống trùng lặp Tab bằng Web BroadcastChannel**: Thiết lập cơ chế Mutex liên tab cùng hệ thống Heartbeat và chuyển quyền cưỡng chế (Preemption), loại bỏ hoàn toàn hiện tượng lặp tiếng hú (Echo Loop) và xung đột luồng Media P2P.
> 5. **Giám sát âm lượng micro tại chỗ bằng Web Audio API**: Tận dụng `AudioContext` và `AnalyserNode` để tính toán công suất âm thanh RMS cục bộ ở 60 FPS, tạo hiệu ứng Speaking Indicator chân thực mà không tốn một byte băng thông nào gửi lên máy chủ."*

---

---

> 🧭 **Điều hướng**: [⬅️ Quay lại Mục Lục Tổng Quan](../notes.md) | [⬅️ Giai đoạn 3: Module Audio](03-giai-doan-3-module-audio.md) | [Giai đoạn 5: Ops & Security ➡️](05-giai-doan-5-ops-security.md)
