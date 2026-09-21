# Phần 02: Xây Dựng Hạ Tầng STOMP WebSocket & Cơ Chế Bảo Mật Kênh Truyền

Tài liệu này ghi chép lại đầy đủ 100% toàn bộ bài giảng về **cách chúng ta thiết kế và xây dựng "đường ống hạ tầng" thời gian thực (Realtime Transport Infrastructure)**. Đây là phần kỹ thuật cốt lõi giúp hệ thống kết nối ổn định, bảo mật tuyệt đối trước tin tặc, tự động vượt qua tường lửa công ty và không bao giờ bị sập khi có sự cố mạng.

---

## 🗺️ Bản Đồ Kiến Trúc Hạ Tầng STOMP WebSocket Của PWB_MiNi

```mermaid
flowchart TD
    subgraph ClientLayer["Trình Duyệt (Next.js Client)"]
        ClientSocket["liveroom-socket.ts<br/>- Auto-refresh JWT (beforeConnect)<br/>- Exponential Backoff + Jitter 400ms"]
    end

    subgraph TransportGateway["Cổng Đón Tiếp (Gateway)"]
        DualEndpoint{"Endpoint: /ws<br/>(Kỹ thuật Đăng Ký Kép)"}
        PureWs["1. WebSocket Thuần<br/>(Tốc độ mili-giây, ít tốn byte)"]
        SockJsFallback["2. SockJS Fallback<br/>(Hạ cấp HTTP Long-Polling khi bị chặn)"]
    end

    subgraph SecurityInterceptors["Vòng Đai Bảo Mật (Backend Interceptors)"]
        AuthInterceptor["1. StompAuthChannelInterceptor<br/>(Chặn frame CONNECT, giải mã JWT,<br/>gán StompUserPrincipal)"]
        ScopeInterceptor["2. StompSubscriptionScopeInterceptor<br/>- Cấm SEND trực tiếp vào Broker<br/>- Kiểm tra quyền khi SUBSCRIBE<br/>- Cô lập /user/queue/rtc"]
    end

    subgraph InternalBroker["Điều Phối & Nhất Quán (Spring Boot)"]
        AppController["@MessageMapping (/app/**)<br/>Xử lý logic nghiệp vụ"]
        SyncManager["Transactional afterCommit<br/>(Chỉ phát STOMP khi DB Commit 100%)"]
        MessageBroker["Message Broker Tự Động<br/>(/topic, /queue)"]
    end

    ClientSocket -->|HTTP Upgrade Handshake| DualEndpoint
    DualEndpoint -->|Mạng bình thường| PureWs
    DualEndpoint -->|Bị tường lửa chặn| SockJsFallback
    PureWs --> AuthInterceptor
    SockJsFallback --> AuthInterceptor
    AuthInterceptor --> ScopeInterceptor
    ScopeInterceptor -->|Lệnh SEND| AppController
    AppController --> SyncManager
    SyncManager --> MessageBroker
    ScopeInterceptor -->|Lệnh SUBSCRIBE| MessageBroker
    MessageBroker -->|Phát Frame MESSAGE| ClientSocket
```

---

## 1. Bài Toán 1: Xác Thực JWT Khi Trình Duyệt Cấm Gửi Header Bắt Tay

### 1.1. Rào Cản Tiêu Chuẩn W3C Của Trình Duyệt
Trong ứng dụng REST API thông thường, mỗi khi gọi request, trình duyệt đều có thể dễ dàng chèn header: `Authorization: Bearer <JWT>` để máy chủ biết ai đang gọi.

Tuy nhiên, trong thế giới WebSocket:
* Để bắt đầu kết nối, trình duyệt gọi hàm JavaScript: `new WebSocket(url)`.
* **Tiêu chuẩn bảo mật W3C của các trình duyệt Web (Chrome, Firefox, Safari) cấm tuyệt đối lập trình viên thêm custom headers vào request bắt tay ban đầu này!**
* **Tại sao không truyền Token qua URL Query Parameter (ví dụ: `/ws?token=eyJhbG...`)?**
  - Nếu truyền trên URL, chuỗi JWT nhạy cảm sẽ bị ghi thẳng vào Access Log của máy chủ Nginx, lưu vết trong lịch sử duyệt web và bị rò rỉ qua các proxy trung gian. Đây là lỗ hổng bảo mật nghiêm trọng (OWASP Top 10).

---

### 1.2. Giải Pháp Của Chúng Ta: "Bắt Tay Công Khai, Đón Lõng Tại Frame CONNECT"

Chúng ta giải quyết bài toán này qua quy trình 3 bước thông minh:

```mermaid
sequenceDiagram
    autonumber
    actor Client as Trình Duyệt
    participant WsPort as Cổng /ws
    participant Interceptor as StompAuthChannelInterceptor
    participant Core as Hệ Thống Phía Sau

    Client->>WsPort: 1. HTTP Upgrade Handshake (GET /ws)
    Note over WsPort: Cổng mở tự do (Public Endpoint)<br/>Không đòi hỏi Token!
    WsPort-->>Client: 2. Trả về HTTP 101 Switching Protocols<br/>(Đường ống TCP vật lý được thông suốt)

    Note over Client, Interceptor: Lúc này ống đã mở nhưng CHƯA ĐƯỢC LÀM GÌ!

    Client->>Interceptor: 3. Gửi Frame STOMP đầu tiên: CONNECT<br/>Headers: Authorization: Bearer <JWT>
    Note over Interceptor: CHẶN ĐỨNG LẠI KIỂM TRA:<br/>- Giải mã chữ ký JWT<br/>- Kiểm tra hạn sử dụng token<br/>- Rút userId tạo StompUserPrincipal
    
    alt Token Hợp Lệ
        Interceptor-->>Client: 4a. Gửi Frame: CONNECTED (Thành công)
        Note over Interceptor, Core: Gán StompUserPrincipal vào phiên socket
    else Token Giả Mạo / Hết Hạn
        Interceptor-->>Client: 4b. Gửi Frame: ERROR ("WS_UNAUTHENTICATED")
        Note over Interceptor: ĐÓNG KẾT NỐI SOCKET NGAY LẬP TỨC!
    end
```

1. **Bước 1 — Bắt tay công khai (Public Handshake)**:
   - Chúng ta đưa endpoint `/ws/**` vào danh sách ngoại lệ bảo mật của Spring Security (`public-endpoints`).
   - Quá trình bắt tay nâng cấp từ HTTP lên WebSocket diễn ra tự do mà không cần Token. 
   - *Lưu ý quan trọng*: Lúc này, đường ống mạng chỉ vừa được thiết lập ở tầng vật lý, người dùng hoàn toàn chưa có bất kỳ quyền hạn nào để gửi hay nhận dữ liệu nghiệp vụ.
2. **Bước 2 — Đón lõng tại Frame STOMP `CONNECT`**:
   - Ngay sau khi đường ống thông, Client bắt buộc phải gửi một frame STOMP đầu tiên mang lệnh: **`CONNECT`**.
   - Vì STOMP hỗ trợ định dạng tiêu đề (headers) tùy ý, Client sẽ đính kèm JWT vào native header:
     ```stomp
     CONNECT
     accept-version:1.2
     Authorization:Bearer eyJhbGciOiJIUzI1Ni...

     ^@
     ```
3. **Bước 3 — Người gác cổng (`StompAuthChannelInterceptor`)**:
   - Backend cấu hình một Interceptor chặn ngay tại kênh tiếp nhận thông điệp (`clientInboundChannel`).
   - Interceptor tóm lấy frame `CONNECT`, rút chuỗi Token ra, gọi bộ thẩm định `AccessTokenAuthenticator`:
     - Kiểm tra chữ ký mật mã HMAC-SHA256.
     - Kiểm tra xem token còn hạn sống hay đã hết hạn.
   - **Gán định danh phiên (`StompUserPrincipal`)**:
     - Nếu hợp lệ, hệ thống tạo đối tượng `StompUserPrincipal` (chứa `userId`) và gán thẳng vào phiên socket thông qua lệnh: `accessor.setUser(principal)`.
     - Thẻ căn cước này sẽ đi theo phiên kết nối suốt toàn bộ vòng đời của socket.
     - Nếu token sai hoặc hết hạn: Ném ngoại lệ `WS_UNAUTHENTICATED`, Spring lập tức trả frame `ERROR` và **ngắt đứt socket tại chỗ**!

---

## 2. Bài Toán 2: Vượt Tường Lửa Bằng Kỹ Thuật Đăng Ký Kép (WebSocket + SockJS)

### 2.1. Vấn Đề Thực Tế: Môi Trường Mạng Bị Chặn Giao Thức
* WebSocket sử dụng cổng mạng và các gói tin nâng cấp đặc thù. Trong thực tế, rất nhiều người dùng ngồi tại:
  - Mạng nội bộ của các tập đoàn, ngân hàng, cơ quan nhà nước.
  - Mạng Wifi trường học, bệnh viện có gắn tường lửa lọc nội dung (Firewall / Proxy).
  - Một số nhà mạng 4G/5G cấu hình kiểm soát gói tin nghiêm ngặt (Deep Packet Inspection).
* Các thiết bị tường lửa này thường **chặn đứng kết nối WebSocket** vì nghi ngờ đây là lưu lượng mạng bất thường, khiến ứng dụng bị tê liệt hoàn toàn.

---

### 2.2. SockJS Là Gì & Cơ Chế Dự Phòng Tự Động (Fallback)
* **SockJS** là một thư viện JavaScript cung cấp giải pháp dự phòng toàn diện:
  - Ban đầu, nó luôn cố gắng thử kết nối bằng **WebSocket thuần** để đạt tốc độ cao nhất.
  - Nếu phát hiện kết nối WebSocket bị mạng chặn, nó sẽ **tự động hạ cấp (Fallback)** xuống các kỹ thuật giả lập thời gian thực chạy trên nền HTTP thông thường mà không tường lửa nào chặn được:
    1. **HTTP Streaming (XHR-Streaming)**: Giữ một kết nối HTTP mở dài hạn để đẩy dữ liệu liên tục.
    2. **HTTP Long-Polling**: Client gửi request, Server treo giữ kết nối chờ có tin rồi trả về.

---

### 2.3. Xung Đột Đường Dẫn Trong Spring MVC & Kỹ Thuật "Đăng Ký Kép"

Trong mã nguồn Backend của Spring Boot, nếu lập trình viên không hiểu sâu sẽ rất dễ làm hỏng ứng dụng:

> **Xung đột ngầm của Spring**:  
> * Nếu ta cấu hình: `registry.addEndpoint("/ws")` $\rightarrow$ Spring tạo bộ xử lý cho WebSocket thuần.  
> * Nếu ta gọi: `registry.addEndpoint("/ws").withSockJS()` $\rightarrow$ Spring **không phải chỉ bật thêm SockJS**, mà nó sẽ **thay thế hoàn toàn** bộ xử lý gốc bằng `SockJsHttpRequestHandler`. Khi đó, các Client chuẩn không có thư viện SockJS (như ứng dụng Mobile iOS/Android, công cụ Postman test API) sẽ bị lỗi và không kết nối được!

#### Kỹ thuật Đăng Ký Kép (Dual-Registration) Của Chúng Ta:
Trong cấu hình `WebSocketConfig`, chúng ta đăng ký endpoint `/ws` đúng **2 lần liên tiếp**:
```
Lần 1: registry.addEndpoint("/ws").setAllowedOriginPatterns(origins);
Lần 2: registry.addEndpoint("/ws").setAllowedOriginPatterns(origins).withSockJS();
```

* **Bản chất định tuyến ngầm của Spring**:
  - Đăng ký lần 1 tạo ra một tuyến đường chính xác cho đường dẫn: `/ws` (dành cho WebSocket thuần).
  - Đăng ký lần 2 tạo ra một tuyến đường dạng thư mục mở rộng: `/ws/**` (để phục vụ các endpoint nội bộ của SockJS như `/ws/info`, `/ws/.../xhr_streaming`).
* **Hiệu quả tối thượng**:
  - Cả hai cơ chế cùng tồn tại song song trong bộ nhớ máy chủ.
  - Người dùng mạng thông thường sẽ kết nối thẳng vào `/ws` bằng **WebSocket thuần** $\rightarrow$ Độ trễ siêu thấp dưới $10ms$, không tốn băng thông.
  - Người dùng bị tường lửa chặn sẽ kết nối vào `/ws/**` qua **SockJS Fallback** $\rightarrow$ Vẫn nghe nhạc và chat bình thường, không bao giờ bị văng khỏi phòng!

---

## 3. Bài Toán 3: Quy Hoạch Kênh Truyền (Pub/Sub) & Bảo Mật Đa Tầng

Để vận hành một phòng Live có hàng trăm người ra vào, hệ thống áp dụng mô hình Publish / Subscribe với **4 tiền tố địa chỉ rõ ràng**:

| Tiền Tố | Bản Chất Kênh | Đối Tượng Nhận | Ví Dụ Thực Tế Trong Live Room |
| :--- | :--- | :--- | :--- |
| **`/topic`** | Kênh phát thanh công cộng (Broadcast) | **Cả phòng cùng nghe** | `/topic/liveroom/88` (Nhạc đang phát giây nào, tin nhắn chat chung) |
| **`/queue`** | Kênh gửi thông báo riêng (Unicast) | **Đúng 1 người nhận** | `/queue/errors` (Thông báo lỗi riêng cho bạn) |
| **`/app`** | Kênh dẫn vào **Mã nguồn Backend** | **Máy chủ xử lý logic** | `/app/liveroom/88/chat` (Gửi tin nhắn lên để lọc từ ngữ bậy) |
| **`/user`** | Kênh **ảo bảo mật cá nhân** | **Chỉ riêng người đó** | `/user/queue/liveroom/rtc` (Gửi tín hiệu WebRTC mật giữa 2 người) |

---

### 3.1. Ba Chốt Chặn Bảo Mật Kênh Tại `StompSubscriptionScopeInterceptor`

Nếu để mặc định của thư viện, hệ thống sẽ gặp phải 3 lỗ hổng bảo mật chết người. Chúng ta đã chặn đứng toàn bộ bằng `StompSubscriptionScopeInterceptor`:

```mermaid
flowchart TD
    FrameIn["Frame STOMP từ Client gửi lên"] --> CheckCommand{"Lệnh STOMP là gì?"}

    CheckCommand -- "Lệnh SEND" --> CheckSend{"Destination bắt đầu bằng gì?"}
    CheckSend -- "Bắt đầu bằng /topic, /queue, /user" --> BlockSend["❌ CHẶN ĐỨNG NGAY!<br/>(Cấm Client gửi tin lậu trực tiếp vào Broker)"]
    CheckSend -- "Bắt đầu bằng /app/**" --> AllowSend["✅ HỢP LỆ:<br/>Dẫn vào Controller để Backend duyệt"]

    CheckCommand -- "Lệnh SUBSCRIBE" --> CheckSub{"Kênh đăng ký là gì?"}
    CheckSub -- "/topic/liveroom/{roomId}/**" --> VerifyMember{"User có đang ngồi hợp lệ<br/>trong phòng này không?"}
    VerifyMember -- "Người ngoài / Đã bị kick" --> BlockSub["❌ CHẶN ĐỨNG!<br/>Từ chối nghe lén phòng"]
    VerifyMember -- "Thành viên hợp lệ" --> AllowSub["✅ CHO PHÉP LẮNG NGHE"]
    
    CheckSub -- "/user/queue/**" --> AllowPrivate["✅ KÊNH RIÊNG TƯ AN TOÀN"]
```

#### Chốt chặn 1: Bịt kín Message Broker đối với lệnh `SEND` (Chống gửi tin lậu)
* **Nguy cơ**: Nếu không chặn, một người dùng xấu có thể tự chế tool gửi frame `SEND` thẳng vào `/topic/liveroom/88` với nội dung giả mạo máy chủ: `{"event": "ROOM_CLOSED"}`. Toàn bộ người trong phòng sẽ tưởng phòng bị đóng và thoát ra!
* **Quy tắc của chúng ta**:
  - Cấm tuyệt đối Client gửi lệnh `SEND` vào bất kỳ kênh nào có tiền tố `/topic`, `/queue` hay `/user`.
  - Mọi tin nhắn gửi từ Client **bắt buộc phải có tiền tố `/app/**`**.
  - **Ý nghĩa**: 100% dữ liệu từ người dùng bắt buộc phải đi vào Controller của Backend, được kiểm tra quyền hạn, kiểm tra logic, lọc từ ngữ bậy rồi chính Backend mới là người phát tin ra `/topic`.

#### Chốt chặn 2: Kiểm soát quyền hạn khi `SUBSCRIBE` vào phòng (Chống nghe lén)
* **Nguy cơ**: Một kẻ tò mò ở ngoài phòng, chỉ cần đoán được ID phòng là 88, có thể gửi lệnh `SUBSCRIBE` vào `/topic/liveroom/88` để nghe lén chat và các bài hát nội bộ chưa phát hành của nghệ sĩ.
* **Quy tắc của chúng ta**:
  - Khi Client gửi frame `SUBSCRIBE` vào `/topic/liveroom/{roomId}/**`, Interceptor chặn lại.
  - Bóc tách `roomId` từ đường dẫn và lấy `userId` từ `StompUserPrincipal`.
  - Truy vấn kiểm tra: Người này có đang là thành viên hợp lệ ngồi trong phòng này không?
  - Nếu là người ngoài chưa được duyệt, hoặc người vừa bị chủ phòng Kick: **Từ chối ngay lập tức bằng frame `ERROR`**.

#### Chốt chặn 3: Cô lập tín hiệu nhạy cảm bằng Kênh Cá Nhân (`/user/queue/**`)
* **Nguy cơ**: Trong tính năng đàm thoại giọng nói (WebRTC), hai máy tính muốn gọi cho nhau phải trao đổi địa chỉ IP thật và cổng mạng của nhau (gọi là ICE Candidate). Nếu phát tán thông số này vào topic chung của phòng, bất kỳ ai trong phòng cũng đọc được địa chỉ IP nhà riêng của người khác, mở ra nguy cơ bị tấn công mạng (DDoS).
* **Quy tắc của chúng ta**:
  - Toàn bộ tín hiệu WebRTC và thông báo lỗi cá nhân được gửi tới địa chỉ: `/user/queue/liveroom/rtc`.
  - Cơ chế nội tại của Spring sẽ tự động dịch tiền tố `/user` thành một hàng đợi bí mật gắn liền với danh tính Principal của chính người nhận (ví dụ: `/queue/liveroom/rtc-user-999`).
  - Người dùng khác dù ở cùng phòng cũng **không có bất kỳ cách nào đăng ký nghe trộm được hàng đợi của người khác**. Đây là sự an toàn được bảo vệ ngay từ cấu trúc thiết kế (Secure by Design).

---

## 4. Bài Toán 4: Tính Nhất Quán Giao Dịch — "Không Bao Giờ Nói Dối Client" (`afterCommit`)

### 4.1. Bài Toán Ghi Kép Bất Đồng Bộ (Dual-Write Inconsistency)
Trong phòng Live, hầu hết mọi thao tác nghiệp vụ đều gồm 2 bước:
1. **Bước 1**: Ghi dữ liệu vào Cơ sở dữ liệu PostgreSQL (chạy trong 1 Transaction `@Transactional`).
2. **Bước 2**: Phát thông báo qua WebSocket STOMP cho cả phòng cùng biết.

> **Kịch bản thảm họa nếu làm ngây thơ**:  
> Chủ phòng bấm nút **"Đuổi bạn B ra khỏi phòng" (Kick)**.  
> * Use Case cập nhật Database: Đổi trạng thái của B thành đã bị đuổi.  
> * Tiện tay, Use Case gọi ngay lệnh phát STOMP: `messagingTemplate.convertAndSend("/topic/...", "B_DA_BI_DUOI")`.  
> * Frame STOMP bay qua mạng tới máy của mọi người chỉ trong $2ms$. Màn hình của cả phòng hiện thông báo B đã bị đuổi và tên của B biến mất!  
> * **Nhưng ngay sau đó, Database Transaction bị lỗi (Rollback)** do đứt kết nối mạng hoặc đụng độ khóa!  
> * **Hậu quả**: Trên màn hình mọi người thì thấy B đã bị đuổi, nhưng trong Database B vẫn là thành viên hợp lệ! Hệ thống đã "nói dối" người dùng, tạo ra sự lệch pha tai hại giữa giao diện và dữ liệu thực tế.

---

### 4.2. Giải Pháp Của Chúng Ta: Phát Sự Kiện Sau Commit (`afterCommit`)

Tại bộ phát sự kiện `StompLiveroomEventPublisherAdapter`, chúng ta triển khai kỹ thuật đồng bộ giao dịch thông qua công cụ của Spring: `TransactionSynchronizationManager`:

```mermaid
sequenceDiagram
    autonumber
    actor Owner as Chủ Phòng
    participant UC as Use Case Nghiệp Vụ
    participant DB as PostgreSQL (ACID)
    participant Sync as TransactionSynchronization
    participant WS as WebSocket STOMP

    Owner->>UC: Bấm Đuổi thành viên B
    Note over UC, DB: Bắt đầu Transaction (@Transactional)
    UC->>DB: 1. Cập nhật trạng thái bị đuổi trong DB
    UC->>Sync: 2. Gọi phát sự kiện STOMP
    Note over Sync: Kiểm tra: Transaction đang chạy!<br/>TẠM GIỮ SỰ KIỆN LẠI (Chưa gửi vội)
    
    alt Database COMMIT Thành Công 100%
        DB-->>UC: Commit thành công!
        Sync->>WS: Kích hoạt hook afterCommit() -> BẮN TIN STOMP ĐI!
        WS-->>Owner: Màn hình cập nhật chính xác tuyệt đối
    else Database Bị Lỗi (ROLLBACK)
        DB-->>UC: Rollback giao dịch!
        Note over Sync: Hook afterCommit KHÔNG BAO GIỜ ĐƯỢC GỌI!<br/>Hủy bỏ gói tin STOMP, không gửi rác ra ngoài!
    end
```

* **Cơ chế hoạt động**:
  1. Khi Use Case gọi lệnh phát sự kiện STOMP, hệ thống kiểm tra xem luồng hiện tại có đang nằm trong một Database Transaction hay không.
  2. Nếu có, hệ thống **không gửi đi ngay**, mà đóng gói sự kiện lại và treo vào móc đồng bộ `afterCommit()`.
  3. **Chỉ khi nào PostgreSQL chốt sổ câu lệnh `COMMIT` thành công 100%**, hook `afterCommit()` mới bung ra và phóng frame STOMP xuống trình duyệt.
  4. Nếu Database bị lỗi và Rollback, hook này vĩnh viễn không bao giờ chạy. Không một thông tin sai lệch nào có cơ hội lọt xuống màn hình của người dùng!

---

## 5. Bài Toán 5: Khả Năng Tự Phục Hồi Phía Client (Client Resilience)

Một hạ tầng tốt không chỉ nằm ở phía Backend, mà phải có sự phối hợp ăn ý từ phía Trình duyệt (Frontend). Tại file `liveroom-socket.ts`, chúng ta đã giải quyết 2 bài toán vận hành gai góc nhất:

### 5.1. Bài Toán Token Hết Hạn Khi Máy Ngủ (Sleep Mode)
* **Hiện tượng**: Người dùng mở phòng nghe nhạc trên Laptop, sau đó gập máy lại đi ăn trưa trong 30 phút.
* Trong thời gian đó, mã JWT Access Token (có thời hạn sống 15 phút) đã hết hạn.
* Khi mở máy ra, trình duyệt cố gắng kết nối lại WebSocket bằng chính cái token đã chết đó $\rightarrow$ Server từ chối và người dùng bị văng ra màn hình đăng nhập một cách rất ức chế.
* **Cách chúng ta giải quyết (`freshToken` trước khi kết nối)**:
  - Chúng ta can thiệp vào hook `beforeConnect` của thư viện STOMP Client.
  - Trước khi gửi frame `CONNECT`, Client luôn kiểm tra thời gian sống còn lại của JWT.
  - Nếu token đã hết hạn (hoặc còn dưới 30 giây), Client sẽ tạm dừng kết nối, âm thầm gọi API Refresh Token để lấy một JWT mới tinh, cập nhật vào tiêu đề rồi mới tiến hành kết nối.
  - Người dùng mở máy ra là kết nối lại mượt mà ngay lập tức, không bao giờ bị gián đoạn.

---

### 5.2. Bài Toán "Bão Kết Nối" (Thundering Herd Problem)

```
Kịch bản thảm họa nếu không có Jitter:
Server bảo trì khởi động lại trong 5 giây.
1.000 người dùng cùng bị đứt mạng.
Nếu tất cả đều chờ đúng 1 giây rồi kết nối lại:
==> Đúng giây thứ 6: CẢ 1.000 MÁY TÍNH CÙNG ÙA VÀO 1 MILI-GIÂY!
==> CPU chạm trần 100%, nghẽn mạng TCP, Server sập tiếp lần 2 (Reboot Loop)!
```

#### Giải pháp của chúng ta: Thuật toán Exponential Backoff kết hợp Jitter ngẫu nhiên 400ms
Client của chúng ta tự cài đặt thuật toán lùi thời gian thông minh:

$$\text{Thời gian chờ} = \min\Big(15\text{s}, \; 1\text{s} \times 2^{(\text{lần\_thử} - 1)}\Big) + \text{random}(0, 400\text{ms})$$

1. **Exponential Backoff (Lùi theo cấp số nhân)**: 
   - Lần 1 chờ $1$ giây, lần 2 chờ $2$ giây, lần 3 chờ $4$ giây, lần 4 chờ $8$ giây... tối đa là $15$ giây. Tránh việc dồn dập tấn công máy chủ khi máy chủ đang gặp sự cố.
2. **Jitter (Độ trễ ngẫu nhiên $0 - 400ms$)**:
   - Khoảng thời gian ngẫu nhiên nhỏ này giúp làm loãng dòng lưu lượng: người thử lại lúc 1.05 giây, người lúc 1.23 giây, người lúc 1.38 giây...
   - 1.000 kết nối được rải đều theo thời gian, triệt tiêu hoàn toàn hiện tượng Bão kết nối, bảo vệ máy chủ khởi động lại an toàn 100%.

---

## 6. Tổng Kết 5 Trụ Cột Cốt Lõi Của Hạ Tầng STOMP

| Thành Phần Hạ Tầng | Vấn Đề Gặp Phải | Giải Pháp Kỹ Thuật Của Chúng Ta |
| :--- | :--- | :--- |
| **Xác thực Handshake** | W3C cấm gửi Header lúc bắt tay | Public Handshake, chặn đầu xác thực JWT tại frame STOMP `CONNECT` qua `StompAuthChannelInterceptor`. |
| **Tương thích mạng** | Tường lửa doanh nghiệp chặn WebSocket | Kỹ thuật **Đăng Ký Kép**: Hỗ trợ song song WebSocket thuần (tốc độ cao) và SockJS Fallback (vượt tường lửa). |
| **Bảo mật kênh truyền** | Lỗ hổng gửi tin lậu và nghe lén | Cấm Client `SEND` vào Broker, ép qua `/app/**`; kiểm tra tư cách khi `SUBSCRIBE`; cô lập signaling qua `/user/queue/**`. |
| **Nhất quán dữ liệu** | Phát tin WebSocket khi DB bị Rollback | Tạm giữ sự kiện, chỉ phát STOMP khi nhận tín hiệu **`afterCommit`** thành công từ PostgreSQL Transaction. |
| **Khả năng tự phục hồi** | Token chết khi ngủ máy & Bão kết nối | Hook `beforeConnect` tự làm tươi token, thuật toán **Exponential Backoff + Jitter 400ms** chống sập server. |

---

## 🧭 Cầu Nối Sang Phần Tiếp Theo

Bây giờ, "Đường Ống Hạ Tầng" truyền dẫn thời gian thực đã được xây dựng hoàn chỉnh, bảo mật tuyệt đối và chịu tải siêu bền bỉ.

Đã đến lúc chúng ta bắt đầu đổ những dòng nước nghiệp vụ đầu tiên vào đường ống này:  
👉 **"Làm thế nào để tạo một căn phòng nghe nhạc? Tại sao một phòng có thể tồn tại nhiều năm nhưng mỗi buổi nghe nhạc phải là một phiên mới toanh (`1 Room - N Cycles`)? Và cơ chế hoàn tác 5 giây khi lỡ tay bấm nhầm hoạt động ra sao?"**  

Đó chính là nội dung của **Phần 03: Chức Năng Tạo Phòng & Quản Trị Vòng Đời Phiên (Session Cycle)**.
