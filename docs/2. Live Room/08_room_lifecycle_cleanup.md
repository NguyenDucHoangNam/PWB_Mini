# 08. Quản lý Vòng đời phòng ảo (Room Lifecycle & Cleanup)

Tài liệu đặc tả A-Z quy trình quản lý vòng đời phòng ảo (Live Room Lifecycle) từ khi Host chủ động đóng phòng, cơ chế ân hạn tạm thời khi Host mất mạng (Grace Period) và tiến trình tự động dọn dẹp phòng trống (Idle / AFK Room Cleanup) chạy nền tối ưu hiệu năng.

---

## 📋 1. Business & Requirements (Nghiệp vụ & Yêu cầu)

### 1.1. Mô tả Nghiệp vụ (User Story / Use Case)
*   **Đối tượng thực hiện**: 
    *   *Host*: Thực hiện đóng phòng chủ động khi hoàn thành buổi nghe thử demo.
    *   *Hệ thống (Scheduler)*: Tự động phát hiện các phòng nhàn rỗi (không có người) hoặc phòng bị bỏ hoang do Host mất mạng để tiến hành dọn dẹp giải phóng tài nguyên.
*   **Quy trình tóm tắt**:
    *   *Chủ động đóng*: Host nhấn "Đóng phòng" -> Gọi API -> Cập nhật Database (`CLOSED`) -> Xóa cache Redis -> Gửi tin nhắn đóng phòng qua WebSocket -> Ép toàn bộ Listener ngắt kết nối và quay lại trang chủ.
    *   *Tự động dọn dẹp*: 
        *   Nếu Host bị mất kết nối đột ngột (rớt mạng), phòng được chuyển sang trạng thái `INACTIVE_HOST` và có **5 phút ân hạn (Grace Period)** để Host kết nối lại. Quá 5 phút, phòng tự động bị đóng.
        *   Nếu phòng không có bất kỳ thành viên nào (số người kết nối = 0) liên tục trong **15 phút**, hệ thống tự động đóng phòng.

### 1.2. Quy tắc Nghiệp vụ (Business Rules)

#### A. Host chủ động đóng phòng (Voluntary Close)
*   Chỉ Host mới có quyền gọi API đóng phòng. Lệnh đóng phòng thực hiện tuần tự:
    1. Cập nhật bản ghi phòng trong PostgreSQL sang trạng thái `CLOSED` và điền mốc giờ `closed_at = now()`.
    2. Gửi một bản tin WebSocket chứa sự kiện `ROOM_CLOSED` đến tất cả các thành viên qua topic `/topic/rooms/{roomCode}/playback`.
    3. Đợi 500ms (để các Client nhận tin nhắn và chuẩn bị giao diện), sau đó tắt và đóng toàn bộ các phiên kết nối WebSocket STOMP của phòng đó.
    4. Xóa toàn bộ các khóa liên quan đến phòng đó trong Redis để giải phóng RAM:
        *   `room:status:{roomCode}` (Hash)
        *   `room:playback:{roomCode}` (Hash)
        *   `room:members:{roomCode}` (Hash)
        *   `room:waiting:{roomCode}` (ZSet)
        *   `room:delegated:{roomCode}` (Set)
        *   Xóa `roomCode` khỏi danh sách ZSet quản lý phòng hoạt động `rooms:active:zset`.

#### B. Thời gian ân hạn mất kết nối của Host (Host Disconnect Grace Period)
*   Khi kết nối WebSocket của Host bị ngắt:
    1. Hệ thống phát hiện qua cơ chế Heartbeat hoặc sự kiện `SessionDisconnectEvent` của Spring WebSocket.
    2. Cập nhật thuộc tính `status` trong Redis Hash `room:status:{roomCode}` từ `ACTIVE` thành `INACTIVE_HOST`.
    3. Thiết lập một khóa đếm ngược tạm thời trên Redis: `room:cooldown:host_disconnect:{roomCode}` với giá trị là `true` và **TTL là 300 giây (5 phút)**.
    4. *Kịch bản A (Host kết nối lại kịp thời)*: Trong vòng 5 phút, Host kết nối WebSocket lại, xác thực token thành công -> Hệ thống xóa khóa đếm ngược `room:cooldown:host_disconnect:{roomCode}` và chuyển trạng thái phòng lại thành `ACTIVE`.
    5. *Kịch bản B (Quá 5 phút)*: Khóa đếm ngược biến mất, tiến trình Scheduler nền quét và thực thi đóng phòng vĩnh viễn.

#### C. Tự động đóng phòng nhàn rỗi (Empty Room AFK Cleanup)
*   **Ràng buộc**: Một phòng Live Room không được phép tồn tại nếu không có ai sử dụng.
*   Khi số lượng thành viên trong phòng giảm về 0 (được cập nhật qua sự kiện ngắt kết nối WebSocket của thành viên cuối cùng):
    *   Hệ thống thiết lập khóa đếm ngược `room:cooldown:empty:{roomCode}` với **TTL là 900 giây (15 phút)**.
    *   Nếu có người mới vào phòng trước khi hết hạn -> Xóa khóa đếm ngược.
    *   Nếu quá 15 phút không có ai vào -> Scheduler nền quét và thực thi đóng phòng.

#### D. Bộ quét dọn dẹp nền (Background Cleanup Scheduler)
*   Hệ thống sử dụng một Spring Scheduler chạy định kỳ **mỗi 1 phút** để quét các phòng hoạt động.
*   **Phòng chống Concurrency**: Cấu hình **ShedLock** trên Redis để đảm bảo chỉ có duy nhất một instance Backend thực hiện quét dọn dẹp tại một thời điểm trong môi trường đa máy chủ (Clustered Environment).
*   **Logic quét**:
    1. Scheduler lấy toàn bộ danh sách phòng hoạt động trong ZSet `rooms:active:zset`.
    2. Duyệt qua từng phòng, kiểm tra sự tồn tại của các khóa đếm ngược (`room:cooldown:host_disconnect:*` hoặc `room:cooldown:empty:*`).
    3. Nếu phát hiện khóa đếm ngược đã hết hạn (tức là không còn tồn tại trên Redis) trong khi trạng thái phòng tương ứng vẫn là `INACTIVE_HOST` hoặc phòng trống không có hoạt động, Scheduler gọi hàm đóng phòng tự động (cập nhật DB và xóa cache).

---

### 1.3. Quy tắc Xác thực Dữ liệu (Validation Rules)
*   API đóng phòng `POST /api/v1/rooms/{roomCode}/close` không yêu cầu body, chỉ xác thực mã phòng trên URL Path và token JWT của người gọi trong Header.

---

### 1.4. Giới hạn Tần suất Truy cập API (IP Rate Limiting)

| API Endpoint | Giới hạn | Mô tả |
| :--- | :--- | :--- |
| `POST /api/v1/rooms/{roomCode}/close` | **5 requests / phút / IP** | Ngăn chặn việc bấm nút đóng phòng liên tục |

---

## 🔄 2. User Flow & Sequence Diagram (Luồng người dùng & Sơ đồ tuần tự)

### 2.1. Luồng Host chủ động Đóng phòng (Host Close Room Flow)

```mermaid
sequenceDiagram
    autonumber
    actor Host as Producer (Host)
    participant FE as Host Frontend App
    participant BE as Backend (Spring Boot)
    participant Redis as Redis Cache
    participant DB as PostgreSQL
    participant ListFE as Listener Frontend App

    Host->>FE: Bấm nút "Đóng phòng"
    FE->>BE: POST /api/v1/rooms/{roomCode}/close (Kèm JWT)
    
    BE->>BE: Xác thực Host sở hữu phòng
    
    Note over BE, DB: Bắt đầu Transaction
    BE->>DB: Cập nhật rooms -> status='CLOSED', closed_at=now()
    Note over BE, DB: Commit Transaction
    
    BE->>ListFE: Broadcast WebSocket qua topic /playback (event='ROOM_CLOSED')
    
    Note over BE, Redis: Dọn dẹp bộ nhớ Redis Cache
    BE->>Redis: Xóa các khóa Hash, ZSet, Set liên quan đến roomCode
    BE->>Redis: Xóa roomCode khỏi ZSet quản lý 'rooms:active:zset'
    
    BE-->>FE: HTTP 200 OK (Đóng phòng thành công)
    
    par Xử lý phía Host
        FE->>FE: Ngắt kết nối WebSocket
        FE-->>Host: Chuyển về trang Dashboard chủ
    and Xử lý phía các Listener
        ListFE->>ListFE: Nhận tin ROOM_CLOSED
        ListFE->>ListFE: Hiển thị Toast cảnh báo: "Phòng đã bị đóng bởi Host"
        ListFE->>ListFE: Ngắt kết nối WebSocket
        ListFE-->>ListFE: Chuyển hướng về trang nhập mã /rooms/join
    end
```

---

### 2.2. Luồng Tự động Dọn dẹp Phòng do Host mất mạng quá 5 phút

```mermaid
sequenceDiagram
    autonumber
    participant WS as WebSocket Connection
    participant BE as Backend (Spring Boot)
    participant Redis as Redis Cache
    participant Scheduler as Spring Scheduler (ShedLock)
    participant DB as PostgreSQL

    WS-->>BE: [Sự kiện] Host mất kết nối WebSocket đột ngột (rớt mạng)
    BE->>Redis: Cập nhật Hash 'room:status:{roomCode}' -> status='INACTIVE_HOST'
    BE->>Redis: Tạo khóa đếm ngược 'room:cooldown:host_disconnect:{roomCode}' (TTL = 300 giây)
    
    Note over BE, Scheduler: --- Trôi qua 5 phút, Host không vào lại ---
    
    Redis-->>Redis: Khóa 'room:cooldown:host_disconnect:{roomCode}' tự động hết hạn & biến mất
    
    Loop Định kỳ mỗi 1 phút
        Scheduler->>Redis: Thử lấy khóa ShedLock chạy quét dọn dẹp
        alt Lấy khóa ShedLock thành công
            Scheduler->>Redis: Lấy danh sách roomCode trong 'rooms:active:zset'
            loop Với mỗi roomCode đang hoạt động
                Scheduler->>Redis: Kiểm tra khóa đếm ngược 'room:cooldown:host_disconnect:{roomCode}'
                alt Khóa đếm ngược không còn tồn tại & status='INACTIVE_HOST'
                    Note over Scheduler, DB: Thực thi dọn dẹp tự động
                    Scheduler->>DB: Cập nhật rooms -> status='CLOSED', closed_at=now()
                    Scheduler->>Redis: Xóa sạch toàn bộ các key Redis của roomCode
                    Scheduler->>Redis: Xóa roomCode khỏi 'rooms:active:zset'
                end
            end
        end
    end
```

---

## 💾 3. Database & Cache Schema (Thiết kế Cơ sở Dữ liệu & Cache)

### 3.1. Các Khóa đếm ngược Redis bổ sung (Cooldown Keys)

| Định dạng Khóa (Redis Key) | Kiểu dữ liệu | Giá trị (Value) | TTL | Mục đích sử dụng |
| :--- | :--- | :--- | :--- | :--- |
| `room:cooldown:host_disconnect:{roomCode}` | `String` | `"true"` | **300 giây** (5 phút) | Theo dõi thời gian ân hạn chờ Host reconnect. |
| `room:cooldown:empty:{roomCode}` | `String` | `"true"` | **900 giây** (15 phút) | Theo dõi thời gian nhàn rỗi chờ người dùng mới vào. |
| `rooms:active:zset` | `ZSet` | `roomCode` | **Vô hạn** | Danh sách tập hợp tất cả các phòng đang có trạng thái hoạt động trên hệ thống để Scheduler quét. |

---

## 🔌 4. API & WebSocket Specifications (Đặc tả API)

### 4.0. Cấu hình Chung
*   **Base Path**: `/api/v1`
*   **Headers**: `Authorization: Bearer <accessToken>`

---

### 4.1. API Đóng phòng chủ động (Close Room)
*   **Method**: `POST`
*   **Path**: `/api/v1/rooms/{roomCode}/close`
*   **Auth Level**: `Requires ROLE_USER_PRO` (Chỉ Host mới gọi được)

#### Response Thành công (200 OK):
```json
{
  "success": true,
  "message": "Đóng phòng Live Room và dọn dẹp tài nguyên thành công",
  "data": null,
  "errors": null,
  "timestamp": "2026-07-01T15:50:00Z"
}
```

#### Response Thất bại do không phải Host (403 Forbidden):
```json
{
  "success": false,
  "message": "Bạn không có quyền đóng phòng này",
  "data": null,
  "errors": [
    {
      "code": "FORBIDDEN_ACCESS",
      "field": null,
      "message": "Chỉ Host mới được phép đóng phòng"
    }
  ],
  "timestamp": "2026-07-01T15:50:00Z"
}
```

---

### 4.2. Đặc tả Tin nhắn WebSocket Phát sóng khi Đóng phòng (`ROOM_CLOSED`)
*   **Kênh phát sóng**: `/topic/rooms/{roomCode}/playback`
*   **Payload tin nhắn (JSON)**:
```json
{
  "event": "ROOM_CLOSED",
  "data": {
    "roomCode": "A8B9D1",
    "reason": "Chủ phòng đã chủ động đóng phòng kết nối"
  }
}
```

---

### 4.3. Phụ lục Mã Lỗi Nghiệp Vụ (Error Codes)

| Http Status | Error Code (String) | Mô tả | Trường liên quan (`field`) |
| :--- | :--- | :--- | :--- |
| `403 Forbidden` | `FORBIDDEN_ACCESS` | Người dùng gọi API không phải là Host của phòng | `null` |
| `404 Not Found` | `ROOM_NOT_FOUND` | Không tìm thấy phòng hoặc phòng đã đóng trước đó | `roomCode` |

---

## 🎨 5. UI/UX & Frontend Integration Notes (Giao diện & Tích hợp)

### 5.1. Thiết kế Hộp thoại Cảnh báo Đóng phòng (Grayscale Theme & A11y)
*   **Trải nghiệm khi bị ép thoát (Force Redirect)**:
    *   Khi Listener nhận được sự kiện `ROOM_CLOSED` qua WebSocket, Frontend chặn ngay các hoạt động nghe nhạc/đàm thoại WebRTC.
    *   Hiển thị một hộp thoại Modal đơn sắc cảnh báo ở chính giữa màn hình: *"Buổi nghe thử đã kết thúc. Phòng đã được đóng bởi Producer"* kèm theo nút "Quay lại trang chủ" màu đen.
    *   Sau 5 giây, nếu người dùng không bấm, hệ thống tự động chuyển hướng về trang nhập mã `/rooms/join`.
*   **Accessibility (A11y)**:
    *   Hộp thoại Modal cảnh báo đóng phòng bắt buộc khai báo `role="alertdialog"` và `aria-modal="true"`. Tiêu điểm bàn phím tự động tập trung vào nút "Quay lại trang chủ" để người dùng dễ dàng thao tác bấm Enter.

---

### 5.2. Tối ưu hóa Hiệu năng & Trải nghiệm Lập trình viên (Performance & DevEx)
*   **Giải phóng WebRTC Media (Garbage Collection)**:
    *   Khi nhận sự kiện đóng phòng, Frontend bắt buộc phải chạy hàm dọn dẹp tài nguyên phần cứng của máy khách:
        ```typescript
        // Dừng toàn bộ các track camera và micro
        localStream.getTracks().forEach(track => track.stop());
        
        // Đóng các kết nối ngang hàng peer connection
        peerConnections.forEach(peerConnection => {
          peerConnection.close();
        });
        ```
    *   Nếu không dừng các track này, đèn thông báo camera/micro trên máy tính người dùng vẫn sẽ sáng (rò rỉ phần cứng) gây cảm giác bất an về bảo mật.

---

### 5.3. Trạng thái Kết nối & Trải nghiệm Reconnection (WebSocket UX)
*   Nếu mạng bị đứt đột ngột, Listener sẽ ở màn hình chờ kết nối lại (Reconnecting) trong tối đa 5 phút. 
*   Nếu trong thời gian đứt kết nối này, Host đóng phòng -> Khóa đếm ngược Grace Period của Host hết hạn -> Phòng bị xóa. Khi mạng của Listener khôi phục, quá trình reconnect WebSocket sẽ thất bại vì phòng không còn tồn tại trên Redis -> Hệ thống tự động chuyển hướng Listener về trang nhập mã kèm Toast thông báo: *"Phòng không tồn tại hoặc đã bị đóng."*

---

### 5.4. Sơ đồ Luồng Màn hình (Screen Flow)

Luồng chuyển dịch màn hình đối với Listener:

```mermaid
graph TD
    classDef default fill:#f9f9f9,stroke:#333,stroke-width:1px,color:#000;
    classDef screen fill:#ffffff,stroke:#000,stroke-width:2px,font-weight:bold,color:#000;
    classDef action fill:#e1e1e1,stroke:#666,stroke-width:1px,stroke-dasharray: 5 5,color:#000;

    LiveRoomPage["Màn hình Phòng Live <br> /rooms/A8B9D1"]:::screen -->|1. Nhận sự kiện ROOM_CLOSED| ShowModal["Hiển thị Modal cảnh báo <br> role=alertdialog"]:::screen
    
    ShowModal -->|Click xác nhận hoặc tự động sau 5s| CleanupAction{Dừng track camera/mic & Đóng WebRTC}:::action
    
    CleanupAction --> JoinPage["Màn hình Nhập mã <br> /rooms/join"]:::screen
```

---

## 📊 6. Logging & Observability (Ghi nhận Nhật ký & Giám sát)

### 6.1. Log Points (Các điểm ghi log)

| Level | Sự kiện | Payload mẫu |
| :--- | :--- | :--- |
| `INFO` | Voluntary close room request | `{"event": "VOLUNTARY_CLOSE_REQUEST", "roomCode": "A8B9D1", "hostId": "c8b74f51-..."}` |
| `INFO` | Room closed and cache cleared | `{"event": "ROOM_CLOSED_SUCCESS", "roomCode": "A8B9D1", "closedBy": "HOST"}` |
| `INFO` | Host disconnected - Cooldown started | `{"event": "HOST_DISCONNECTED_GRACE", "roomCode": "A8B9D1", "cooldownSeconds": 300}` |
| `INFO` | AFK Idle cleanup executed | `{"event": "AFK_CLEANUP_EXECUTED", "roomCode": "A8B9D1", "reason": "EMPTY_ROOM_15M"}` |
| `WARN` | Unauthorized close attempt | `{"event": "UNAUTHORIZED_CLOSE_ATTEMPT", "roomCode": "A8B9D1", "userId": "e5b84f32-..."}` |

### 6.2. Quy tắc Bảo mật Log
*   Không ghi log chi tiết các token hay thông tin định danh IP của Listener khi thực hiện tiến trình dọn dẹp nền tự động.
