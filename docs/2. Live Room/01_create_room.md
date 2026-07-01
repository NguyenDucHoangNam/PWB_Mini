# 01. Khởi tạo & Cấu hình Phòng Live Room (Create & Configure Live Room)

Tài liệu đặc tả A-Z tính năng Khởi tạo và Cấu hình phòng Live Room dành cho Producer (Host), thiết lập các thuộc tính nền tảng, tạo mã phòng độc nhất và khởi động hệ thống liên lạc thời gian thực (WebSocket STOMP).

---

## 📋 1. Business & Requirements (Nghiệp vụ & Yêu cầu)

### 1.1. Mô tả Nghiệp vụ (User Story / Use Case)
*   **Đối tượng thực hiện**: Người dùng có quyền Producer (tài khoản đã nâng cấp lên gói Pro, sở hữu vai trò `ROLE_USER_PRO`).
*   **Quy trình tóm tắt**:
    1.  Producer truy cập màn hình quản lý, nhấn "Khởi tạo phòng Live Room".
    2.  Chọn chế độ vào phòng: Tự do (`OPEN`) hoặc Phê duyệt kiểm duyệt (`MODERATED`).
    3.  Hệ thống tạo mã phòng ngẫu nhiên 6 ký tự độc nhất (ví dụ: `A8B9D1`), lưu thông tin vào cơ sở dữ liệu Postgres và đưa trạng thái phòng hoạt động vào Redis Cache.
    4.  Hệ thống phản hồi thông tin phòng cho Client, tự động kích hoạt kết nối WebSocket STOMP của Producer (Host) tới server để sẵn sàng điều khiển.

### 1.2. Quy tắc Nghiệp vụ (Business Rules)

#### A. Phân quyền khởi tạo (Authorization)
*   Chỉ các tài khoản sở hữu vai trò **`ROLE_USER_PRO`** (Pro User / Producer) mới được quyền gọi API khởi tạo phòng Live Room.
*   Người dùng ở vai trò mặc định `ROLE_USER` khi thực hiện gọi API sẽ bị hệ thống từ chối ngay lập tức với mã HTTP `403 Forbidden` kèm mã lỗi `FORBIDDEN_ACCESS`.
*   **Giới hạn phòng hoạt động**: Mỗi Producer chỉ được phép có tối đa **1 phòng ACTIVE** tại bất kỳ thời điểm nào. Nếu đã tồn tại phòng ACTIVE, Backend trả lỗi HTTP `409 Conflict` (`ROOM_ALREADY_ACTIVE`) kèm thông tin `roomCode` của phòng đang hoạt động để Frontend điều hướng người dùng.

#### B. Cơ chế sinh mã phòng độc nhất (Room Code Generation & Collision Retry)
*   Mã phòng (Room Code) là một chuỗi gồm **6 ký tự chữ và số** viết hoa (không chứa các ký tự dễ nhầm lẫn như `0`, `O`, `1`, `I`), ví dụ: `A8B9D1`.
*   **Chống trùng lặp (Collision Control)**:
    1. Backend sinh mã ngẫu nhiên bằng `SecureRandom`.
    2. Thực hiện kiểm tra sự tồn tại của mã này trong Redis (`room:status:{roomCode}`) và Database.
    3. Nếu phát hiện trùng lặp, hệ thống thực hiện sinh lại mã mới (**tối đa 3 lần thử**).
    4. Nếu sau 3 lần vẫn trùng (xác suất cực kỳ thấp), hệ thống ném lỗi hệ thống `ROOM_CODE_COLLISION_FAILED` (HTTP 500) để đảm bảo không ghi đè dữ liệu.

#### C. Chế độ phòng (Room Modes)
*   **Chế độ OPEN (Vào tự do)**: Cho phép khách hàng có mã phòng tham gia thẳng mà không cần duyệt.
*   **Chế độ MODERATED (Kiểm duyệt)**: Khách hàng yêu cầu tham gia sẽ được xếp vào hàng chờ (Waiting List), chủ phòng phải nhấn duyệt mới được vào.

#### D. Giới hạn số lượng người kết nối (Participants Limit)
*   Mỗi phòng Live Room giới hạn tối đa **7 người kết nối đồng thời** (bao gồm 1 Host và tối đa 6 Listener) để bảo toàn chất lượng đàm thoại WebRTC Mesh (P2P). Ràng buộc này được lưu cấu hình mặc định trong DB và đồng bộ lên cache Redis.

---

### 1.3. Quy tắc Xác thực Dữ liệu (Validation Rules)

| Trường dữ liệu | Ràng buộc | Annotation (Backend) | Mô tả |
| :--- | :--- | :--- | :--- |
| `mode` | Bắt buộc, giá trị phải là `OPEN` hoặc `MODERATED` | `@NotNull` + Java Enum `RoomMode` | Chế độ phê duyệt khi khách hàng vào phòng (Jackson tự động reject giá trị không hợp lệ) |

---

### 1.4. Giới hạn Tần suất Truy cập API (IP Rate Limiting)

| API Endpoint | Giới hạn | Mô tả |
| :--- | :--- | :--- |
| `POST /api/v1/rooms` | **3 requests / phút / IP** | Ngăn chặn hành vi spam tạo phòng liên tục làm cạn kiệt tài nguyên mã phòng và quá tải DB |

---

## 🔄 2. User Flow & Sequence Diagram (Luồng người dùng & Sơ đồ tuần tự)

### 2.1. Luồng Khởi tạo phòng Live Room (Room Creation Flow)

```mermaid
sequenceDiagram
    autonumber
    actor Host as Producer (Host)
    participant FE as Frontend App
    participant BE as Backend (Spring Boot)
    participant Redis as Redis Cache
    participant DB as PostgreSQL

    Host->>FE: Chọn Chế độ phòng & bấm "Tạo phòng"
    FE->>BE: POST /api/v1/rooms (CreateRoomRequest)
    
    BE->>BE: Xác thực quyền ROLE_USER_PRO
    alt Quyền không hợp lệ
        BE-->>FE: HTTP 403 Forbidden (FORBIDDEN_ACCESS)
    else Quyền hợp lệ
        BE->>Redis: Kiểm tra tồn tại phòng ACTIVE của Host
        alt Đã có phòng ACTIVE
            BE-->>FE: HTTP 409 Conflict (ROOM_ALREADY_ACTIVE, kèm roomCode hiện tại)
        else Chưa có phòng ACTIVE
        loop Sinh mã phòng (Tối đa 3 lần thử)
            BE->>BE: Sinh mã ngẫu nhiên 6 ký tự
            BE->>Redis: Kiểm tra tồn tại khóa 'room:status:{roomCode}'
            alt Không tồn tại trên Redis
                BE->>DB: Kiểm tra mã tồn tại trong bảng rooms với status='ACTIVE'
                alt Không tồn tại trong DB (Mã độc nhất)
                    Note over BE: Chọn mã này thành công
                end
            end
        end
        
        alt Thất bại do trùng lặp cả 3 lần (Không chọn được mã)
            BE-->>FE: HTTP 500 Internal Server Error (ROOM_CODE_COLLISION_FAILED)
        else Sinh mã thành công
            Note over BE, DB: Bắt đầu Transaction
            BE->>DB: Lưu Room mới (status='ACTIVE', mode, max_participants=7, host_id)
            Note over BE, DB: Commit Transaction
            
            BE->>Redis: Ghi cấu trúc Hash 'room:status:{roomCode}' (status='ACTIVE', current_participants=1, ...) (TTL: 4h)
            BE->>Redis: Thêm roomCode vào danh sách ZSet quản lý phòng hoạt động
            
            BE-->>FE: HTTP 201 Created (Trả về RoomResponse chứa roomCode)
            
            Note over FE, BE: Thiết lập kết nối thời gian thực
            FE->>BE: Kết nối WebSocket (wss://pwbmini.com/ws, JWT in CONNECT frame)
            BE-->>FE: Kết nối thành công
            FE->>BE: SUBSCRIBE /topic/rooms/{roomCode}/members
            FE->>BE: SUBSCRIBE /topic/rooms/{roomCode}/playback
            FE-->>Host: Hiển thị giao diện Dashboard Phòng Live ảo
        end
        end
    end
```

##### 📝 Mô tả chi tiết các bước xử lý:
1.  **Gửi yêu cầu**: Producer thiết lập chế độ phòng (OPEN hoặc MODERATED) và click tạo. Frontend gửi `POST /api/v1/rooms` đính kèm Token JWT.
2.  **Kiểm tra quyền và giới hạn**: Backend xác thực Token và chỉ cho phép đi tiếp nếu User có vai trò `ROLE_USER_PRO`. Đồng thời kiểm tra xem Producer đã có phòng ACTIVE hay chưa — nếu đã có, trả HTTP 409 (`ROOM_ALREADY_ACTIVE`) kèm `roomCode` hiện tại.
3.  **Sinh mã phòng**: Backend thực hiện vòng lặp (tối đa 3 lần) để sinh mã 6 chữ số/chữ viết hoa không trùng lặp:
    *   Truy vấn nhanh Redis key `room:status:{roomCode}`.
    *   Truy vấn PostgreSQL tìm phòng có trạng thái `ACTIVE` trùng mã.
    *   Nếu vượt quá 3 lần trùng, báo lỗi HTTP 500.
4.  **Lưu Database**: Lưu bản ghi phòng mới vào Postgres để phục vụ báo cáo lịch sử.
5.  **Cập nhật Redis**: Ghi dữ liệu trạng thái phòng vào Redis Hash để phục vụ tra cứu nhanh khi khách hàng kết nối, đặt thời gian sống mặc định là 4 giờ (bằng giới hạn tối đa của một phiên phòng).
6.  **Kết nối WebSocket**: Frontend nhận Room Code, khởi tạo kết nối STOMP WebSocket tới cổng `/ws` của server, sau đó thực hiện đăng ký nhận tin nhắn (SUBSCRIBE) trên các topic thành viên và đồng bộ nhạc của phòng.

---

## 💾 3. Database & Cache Schema (Thiết kế Cơ sở Dữ liệu & Cache)

### 3.1. Sơ đồ thực thể Bảng `rooms` (PostgreSQL)

Bảng `rooms` được thiết kế để lưu vết lịch sử phòng ảo:

```sql
CREATE TABLE rooms (
    id UUID PRIMARY KEY,
    room_code VARCHAR(6) NOT NULL,
    host_id UUID NOT NULL,
    mode VARCHAR(15) NOT NULL, -- 'OPEN', 'MODERATED'
    status VARCHAR(15) NOT NULL, -- 'ACTIVE', 'CLOSED'
    max_participants INT NOT NULL DEFAULT 7,
    created_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP NULL,
    CONSTRAINT fk_rooms_host FOREIGN KEY (host_id) REFERENCES users(id)
);

-- Chỉ mục tối ưu hóa tìm kiếm phòng đang hoạt động
CREATE UNIQUE INDEX idx_rooms_active_code ON rooms(room_code) WHERE status = 'ACTIVE';
```

### 3.2. Cấu trúc dữ liệu Redis (Live Room State)

Khi phòng hoạt động, toàn bộ trạng thái thời gian thực được lưu trên Redis để xử lý tốc độ cao:

| Định dạng Khóa (Redis Key) | Kiểu dữ liệu | Trường dữ liệu (Fields) | TTL | Mục đích sử dụng |
| :--- | :--- | :--- | :--- | :--- |
| `room:status:{roomCode}` | `Hash` | `hostId`: UUID<br>`hostDisplayName`: String (Tên hiển thị của Host, denormalize từ `users.fullName`)<br>`mode`: `OPEN`/`MODERATED`<br>`status`: `ACTIVE`<br>`maxParticipants`: `7`<br>`currentParticipants`: `1` (Mặc định có Host)<br>`activeSourceId`: `null` (Demo được chọn)<br>`createdAt`: ISO 8601 | **4 giờ** (Max session) | Lưu trạng thái cấu hình và vận hành thời gian thực của phòng. |

---

## 🔌 4. API & WebSocket Specifications (Đặc tả API)

### 4.0. Cấu hình Chung
*   **Base Path**: `/api/v1`
*   **Headers**: `Authorization: Bearer <accessToken>`
*   **Format Phản Hồi**: Hệ thống đồng nhất sử dụng cấu trúc `ApiResponse<T>` chuẩn hóa theo quy ước dự án:
    *   **Thành công**: Trả về Http Code thích hợp cùng JSON body chứa `success: true`, `message`, `data` (payload kết quả), `errors: null` và `timestamp` (ISO 8601).
    *   **Thất bại**: Trả về Http Code lỗi, JSON body chứa `success: false`, `message`, `data: null`, `errors` (mảng chi tiết lỗi với `code`, `field`, `message`) và `timestamp`.

---

### 4.1. API Khởi tạo phòng Live Room (Create Room)
*   **Method**: `POST`
*   **Path**: `/api/v1/rooms`
*   **Auth Level**: `Requires ROLE_USER_PRO`

#### Request Body (`CreateRoomRequest`):
```json
{
  "mode": "MODERATED"
}
```

#### Response Thành công (201 Created):
```json
{
  "success": true,
  "message": "Khởi tạo phòng Live Room thành công",
  "data": {
    "roomCode": "A8B9D1",
    "hostId": "c8b74f51-3a78-43d9-9524-34e803c4f2bb",
    "hostDisplayName": "Nguyễn Đức Hoàng Nam",
    "mode": "MODERATED",
    "status": "ACTIVE",
    "maxParticipants": 7,
    "createdAt": "2026-07-01T14:45:00Z"
  },
  "errors": null,
  "timestamp": "2026-07-01T14:45:00Z"
}
```

#### Response Lỗi Không có Quyền (403 Forbidden):
```json
{
  "success": false,
  "message": "Tài khoản của bạn không có quyền thực hiện hành động này. Vui lòng nâng cấp lên tài khoản Pro.",
  "data": null,
  "errors": [
    {
      "code": "FORBIDDEN_ACCESS",
      "field": null,
      "message": "Yêu cầu vai trò ROLE_USER_PRO"
    }
  ],
  "timestamp": "2026-07-01T14:45:00Z"
}
```

---

### 4.2. Đặc tả Kênh Kết nối thời gian thực (WebSocket STOMP)

Sau khi tạo phòng thành công, Frontend thiết lập kết nối WebSocket thông qua giao thức STOMP để truyền tin nhắn điều khiển thời gian thực:

*   **WebSocket Endpoint**: `wss://pwbmini.com/ws` (bắt buộc TLS trong môi trường Production)
*   **Xác thực kết nối**: Frontend gửi Access Token JWT trong STOMP `CONNECT` frame header `Authorization: Bearer <token>`. Backend sử dụng `ChannelInterceptor` xác thực token trước khi cho phép kết nối. Kết nối không có token hợp lệ bị từ chối ngay lập tức.
*   **Kênh Đăng ký nhận tin (Subscribe Topics)**:
    *   `/topic/rooms/{roomCode}/members`: Nhận danh sách cập nhật thành viên online, sự kiện tham gia/rời phòng.
    *   `/topic/rooms/{roomCode}/playback`: Nhận lệnh đồng bộ trạng thái trình phát nhạc (Play/Pause/Seek).
    *   `/topic/rooms/{roomCode}/chat`: Nhận tin nhắn chat tạm thời từ các thành viên trong phòng *(sẽ được đặc tả chi tiết trong bài riêng)*.
*   **Auto-Reconnect & Heartbeat**:
    *   Frontend thực hiện reconnect tự động với **exponential backoff** (1s → 2s → 4s → tối đa 30s) khi kết nối bị đứt.
    *   STOMP Heartbeat: cấu hình `heartbeat-incoming: 10000ms`, `heartbeat-outgoing: 10000ms` để phát hiện kết nối chết.
    *   Nếu Host mất kết nối WebSocket quá **60 giây** mà không reconnect thành công, hệ thống đánh dấu phòng `INACTIVE_HOST` nhưng chưa đóng ngay để cho phép khôi phục. Nếu vượt **5 phút**, phòng tự động chuyển sang `CLOSED`.

---

### 4.3. Phụ lục Mã Lỗi Nghiệp Vụ (Error Codes)

Mỗi lỗi nghiệp vụ được định nghĩa trong `ErrorCode` Enum với HTTP Status tương ứng:

| Http Status | Error Code (String) | Mô tả | Trường liên quan (`field`) |
| :--- | :--- | :--- | :--- |
| `400 Bad Request` | `VALIDATION_FAILED` | Định dạng chế độ phòng không hợp lệ | `mode` |
| `403 Forbidden` | `FORBIDDEN_ACCESS` | Người dùng là `ROLE_USER` thường, không được phép tạo phòng | `null` |
| `409 Conflict` | `ROOM_ALREADY_ACTIVE` | Producer đã có phòng đang hoạt động, chỉ cho phép tối đa 1 phòng ACTIVE | `null` |
| `429 Too Many Requests` | `RATE_LIMIT_EXCEEDED` | Vượt quá giới hạn tần suất truy cập API | `null` |
| `500 Internal Error` | `ROOM_CODE_COLLISION_FAILED` | Sinh mã phòng bị trùng lặp liên tiếp 3 lần | `null` |

---

## 🎨 5. UI/UX & Frontend Integration Notes (Giao diện & Tích hợp)

### 5.1. Thiết kế Giao diện Đơn sắc & Hỗ trợ Tiếp cận (Grayscale Theme & A11y)
*   **Màn hình Tạo phòng**: Thiết kế hộp thoại tối giản. Nút chọn chế độ phòng (OPEN / MODERATED) dạng Radio Group đơn sắc phẳng. Nút bấm "Tạo phòng" đen hoàn toàn (`bg-black text-white`), chuyển xám đậm (`bg-neutral-800`) khi hover.
*   **Màn hình Dashboard Chờ của Host**: Hiển thị mã phòng `Room Code` ở kích thước lớn (`font-bold text-3xl tracking-widest`) kèm nút "Copy mã" nhỏ gọn để dễ dàng chia sẻ cho khách hàng.
*   **Thuộc tính Accessibility (A11y)**:
    *   Các tùy chọn chế độ phòng sử dụng thẻ `input[type="radio"]` tiêu chuẩn hoặc React Aria components để đảm bảo có thể điều hướng bằng bàn phím (Tab, Arrow keys).
    *   Thêm nhãn `aria-label` mô tả hành động cho nút "Copy mã phòng" và "Đóng phòng".

---

### 5.2. Tối ưu hóa Hiệu năng & Trải nghiệm Lập trình viên (Performance & DevEx)
*   **Chống gửi yêu cầu trùng lặp (Double Submit Prevention)**:
    *   Khi bấm nút "Tạo phòng", nút Submit lập tức bị vô hiệu hóa (`disabled`) và hiển thị Spinner xoay để chặn việc người dùng gửi liên tục các request tạo phòng trùng lặp (Debounce/Throttle).
*   **Xác thực tại Client (Client-Side Validation)**:
    *   Sử dụng Zod schema để validate cấu hình đầu vào (mode) trước khi gửi request API.
*   **Xử lý Ngoại lệ mạng (Network Offline Resilience)**:
    *   Frontend sử dụng bộ theo dõi trạng thái mạng (`window.navigator.onLine`).
    *   Nếu người dùng mất kết nối, hệ thống sẽ hiển thị một Toast cảnh báo "Mất kết nối mạng, vui lòng kiểm tra lại!" và chặn không cho click tạo phòng.

---

### 5.3. Trạng thái Kết nối & Trải nghiệm Reconnection (WebSocket UX)
*   **Trạng thái Kết nối (Connection Indicators)**:
    *   Hiển thị một Badge nhỏ góc trên màn hình biểu diễn trạng thái WebSocket:
        *   `Connecting` (Badge Xám nhạt, nhấp nháy chậm).
        *   `Connected` (Badge Đen chữ Trắng ổn định).
        *   `Disconnected` (Badge Xám đậm, cảnh báo đứt kết nối).
*   **Tự động kết nối lại (Reconnection)**:
    *   Khi đứt mạng, hiển thị thông báo: *"Mất kết nối. Đang thử lại trong X giây..."*
    *   Thực hiện cơ chế reconnection với exponential backoff (1s -> 2s -> 4s -> max 30s) như đã đặc tả ở mục 4.2.
*   **Hạn chế Đa tab (Multi-tab Prevention)**:
    *   Để tránh xung đột WebRTC và feedback âm thanh, khi phát hiện người dùng mở tab thứ hai của cùng một phòng Live Room, Frontend sử dụng `BroadcastChannel` để cảnh báo và tự động chặn/redirect tab mới mở về trang chủ.

---

### 5.4. Sơ đồ Luồng Màn hình (Screen Flow)

Luồng chuyển dịch màn hình từ trang chủ của Producer đến phòng Live Room:

```mermaid
graph TD
    classDef default fill:#f9f9f9,stroke:#333,stroke-width:1px,color:#000;
    classDef screen fill:#ffffff,stroke:#000,stroke-width:2px,font-weight:bold,color:#000;
    classDef action fill:#e1e1e1,stroke:#666,stroke-width:1px,stroke-dasharray: 5 5,color:#000;

    ProducerDashboard["Bảng điều khiển Producer <br> /dashboard"]:::screen -->|Click Tạo phòng Live| CreateConfigPage["Màn hình cấu hình tạo phòng <br> /rooms/create"]:::screen
    
    CreateConfigPage -->|Bấm nút tạo & gọi API| CreateAction{Backend tạo phòng}:::action
    
    CreateAction -->|Thất bại: 403 Forbidden| ProducerDashboard
    CreateAction -->|Thành công: Trả Room Code| LiveRoomPage["Màn hình Phòng Live ảo <br> /rooms/A8B9D1"]:::screen
    
    LiveRoomPage -->|Kích hoạt| ConnectWS{Kết nối WebSocket /ws}:::action
    ConnectWS -->|Thành công| ActiveRoom[Bắt đầu nhận tín hiệu điều khiển]:::action
```

---

## 📊 6. Logging & Observability (Ghi nhận Nhật ký & Giám sát)

### 6.1. Log Points (Các điểm ghi log)

| Level | Sự kiện | Payload mẫu |
| :--- | :--- | :--- |
| `INFO` | Create room request | `{"event": "CREATE_ROOM_REQUEST", "hostId": "c8b74f51-...", "mode": "MODERATED"}` |
| `INFO` | Room created successfully | `{"event": "ROOM_CREATED", "roomCode": "A8B9D1", "hostId": "c8b74f51-...", "mode": "MODERATED"}` |
| `WARN` | Room code collision | `{"event": "ROOM_CODE_COLLISION", "collisionCode": "A8B9D1", "attempt": 1}` |
| `WARN` | Unauthorized create attempt | `{"event": "UNAUTHORIZED_CREATE_ATTEMPT", "userId": "c8b74f51-...", "role": "ROLE_USER"}` |
| `ERROR` | Room creation job crashed | `{"event": "ROOM_CREATION_FAILED", "error": "Redis connection timeout"}` |

### 6.2. Quy tắc Bảo mật Log
*   Không log địa chỉ IP thô của Producer vào log hệ thống, chỉ ghi nhận mã thiết bị rút gọn hoặc hash của IP để bảo vệ quyền riêng tư.
