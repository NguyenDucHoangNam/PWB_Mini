# 02. Quy trình Tham gia phòng & Phòng chờ (Join & Waiting Room)

Tài liệu đặc tả A-Z quy trình khách hàng (Listener) tham gia phòng Live Room, hệ thống hàng chờ duyệt (Waiting List) thời gian thực trên Redis, và cơ chế phê duyệt nguyên tử (Atomic Approval) sử dụng Redis Lua Script để ngăn chặn xung đột số lượng người tham gia.

---

## 📋 1. Business & Requirements (Nghiệp vụ & Yêu cầu)

### 1.1. Mô tả Nghiệp vụ (User Story / Use Case)
*   **Đối tượng thực hiện**: 
    *   *Listener*: Người dùng muốn vào phòng nghe nhạc chung (sở hữu mã `roomCode` và tên hiển thị).
    *   *Host*: Chủ phòng Live Room, người xem danh sách chờ và đưa ra quyết định phê duyệt.
*   **Quy trình tóm tắt**:
    1.  Listener nhập mã phòng và tên hiển thị tại giao diện Khách để yêu cầu tham gia.
    2.  Hệ thống kiểm tra tính tồn tại của phòng và giới hạn số lượng thành viên (tối đa 7 người bao gồm Host).
    3.  *Chế độ OPEN*: Listener được vào thẳng phòng ảo.
    4.  *Chế độ MODERATED*: Listener được đưa vào hàng đợi phòng chờ (Waiting List) trong Redis. Host nhận được thông tin yêu cầu của Listener thời gian thực qua WebSocket#### A. Kiểm soát số lượng người tối đa & Tách biệt logic kiểm tra (Atomic Participants Cap & Concurrency Check)
*   Giới hạn cứng của một phòng Live Room là **7 người kết nối đồng thời** (1 Host + 6 Listener).
*   **Tách biệt logic kiểm tra chống tương tranh (False ROOM_FULL)**: Để tránh việc Listener bị ném lỗi đầy phòng một cách oan uổng ở chế độ `MODERATED` (do cơ chế tăng count chiếm chỗ tạm rồi lại giảm ngay về cũ khiến các request đồng thời khác bị block trong mili-giây đó):
    *   Khi gọi API `POST /rooms/{roomCode}/join`, hệ thống chỉ đọc cấu hình phòng từ Redis (HGET `currentParticipants`) để check xem số lượng online hiện tại đã đạt mốc tối đa hay chưa (>= 7).
    *   **Nếu chế độ là OPEN (Vào thẳng)**: Chạy Redis Lua Script check-and-increment để chiếm chỗ thực tế trên Redis.
    *   **Nếu chế độ là MODERATED (Kiểm duyệt)**: Không thực hiện tăng số lượng giữ chỗ tạm thời. Listener được đưa thẳng vào hàng chờ. Số lượng `currentParticipants` chỉ được tăng một lần duy nhất một cách nguyên tử bằng Lua Script khi Host bấm `Approve` ở luồng 2.2.

#### B. Cơ chế Phòng chờ tự động dọn dẹp (Waiting List Auto-cleanup)
*   Trong chế độ **MODERATED**, các yêu cầu tham gia của Listener được xếp vào Redis ZSet `room:waiting:{roomCode}` chỉ chứa duy nhất giá trị là `userId` (UUID của Listener) để đảm bảo độ chính xác khi dọn dẹp bằng lệnh `ZREM` (dựa trên `userId` bóc tách từ Token Principal khi ngắt kết nối WebSocket).
*   Các thông tin mô tả chi tiết của yêu cầu chờ duyệt (như `displayName`, `requestedAt`) được lưu trữ song hành trong cấu trúc Redis Hash `room:waiting_metadata:{roomCode}` (Key: `userId`, Value: JSON String). Cả hai khóa này đều có TTL 4 giờ.
*   **Thời gian chờ tối đa**: Mỗi yêu cầu chờ duyệt chỉ có hiệu lực trong vòng **5 phút (300 giây)**.
*   **Lazy Cleanup**: Khi Host tải danh sách phòng chờ (`GET /api/v1/rooms/{roomCode}/waiting`), hệ thống tự động chạy lệnh dọn dẹp kép: `ZREMRANGEBYSCORE` để xóa sạch các yêu cầu đã quá 5 phút khỏi ZSet, và đồng thời `HDEL` các `userId` tương ứng khỏi Hash metadata trước khi trả về dữ liệu.

#### C. Quy trình Xử lý Phê duyệt & Mời ra khỏi phòng (Approve / Reject / Kick Flow)
*   **Khi Host nhấn Approve (Đồng ý)**: 
    1. Hệ thống thực thi một Redis Lua Script duy nhất để đảm bảo tính nhất quán tuyệt đối (All-or-Nothing) cho toàn bộ tiến trình dịch chuyển trạng thái (State Migration) trên RAM Redis. Lua Script thực hiện:
        *   Kiểm tra số lượng hiện tại (`currentParticipants < maxParticipants`).
        *   Nếu còn chỗ trống: tăng count `currentParticipants` lên 1 đơn vị, xóa Listener khỏi ZSet hàng chờ `room:waiting` và Hash metadata `room:waiting_metadata`, đồng thời thêm Listener vào Redis Hash `room:members`.
        *   Nếu hết chỗ trống: trả về `0`.
    2. Nếu Lua Script trả về `1` (thành công), Backend thực hiện:
        *   Gửi thông báo phê duyệt thành công qua WebSocket riêng tư `/queue/rooms/join-result` của Listener.
        *   Broadcast danh sách thành viên mới tới toàn bộ phòng qua topic `/topic/rooms/{roomCode}/members`.
    3. Nếu Lua Script trả về `0` (phòng đã đầy): Trả về lỗi `ROOM_FULL` cho Host và từ chối yêu cầu của Listener.
*   **Khi Host nhấn Reject (Từ chối)**:
    1. Hệ thống xóa Listener khỏi ZSet hàng chờ `room:waiting:{roomCode}` và Hash metadata `room:waiting_metadata:{roomCode}`.
    2. Gửi thông điệp từ chối qua kênh WebSocket riêng tư của Listener để chuyển hướng họ về màn hình nhập mã.
*   **Khi Host thực hiện Kick thành viên** (Mời thành viên ra ngoài):
    1. Hệ thống chạy Lua Script để giảm `currentParticipants` đi 1 đơn vị trên Redis Hash `room:status:{roomCode}` và xóa thông tin Listener khỏi Redis Hash `room:members:{roomCode}` một cách nguyên tử.
    2. Gửi thông điệp chứa trạng thái `status: 'KICKED'` qua kênh WebSocket riêng tư `/queue/rooms/join-result` của Listener bị kick.
    3. Broadcast danh sách thành viên mới qua topic `/topic/rooms/{roomCode}/members`.

#### D. Cơ chế xác thực Khách vãng lai & Hàng rào Bảo mật (Temporary JWT & Security Sandbox)
*   Để giải quyết lỗi định tuyến tin nhắn qua kênh riêng tư `/user/queue/rooms/join-result` cho đối tượng Khách vãng lai không có tài khoản (thiếu JWT để Spring Security phân giải Principal gán session), hệ thống áp dụng giải pháp cấp Token tạm thời:
    *   Khi gọi API `POST /rooms/{roomCode}/join` thành công (status = 'WAITING' hoặc 'APPROVED'), Backend tự động sinh ra một JWT tạm thời `temporaryToken` chứa các thông tin: `userId` (UUID sinh ngẫu nhiên cho session khách), `roomCode`, và `role: LISTENER` với thời hạn sống (TTL) là **4 giờ** (đồng nhất với thời lượng tối đa của một phiên phòng Live).
    *   **Tránh bẫy tự ngắt kết nối (Token Expiry Trap)**: Việc đặt TTL là 4 giờ (thay vì 5 phút) nhằm giữ kết nối WebSocket của Listener trong hàng chờ luôn kiên cố, không bị đứt gãy giữa chừng kích hoạt cơ chế Disconnect tự động dọn dẹp khi Host duyệt chậm (ví dụ: bận hoặc AFK > 5 phút).
    *   Frontend dùng `temporaryToken` này làm Token xác thực để thiết lập kết nối WebSocket STOMP (CONNECT frame) ngay lập tức khi vào màn hình chờ duyệt. Nhờ có token này, Spring WebSocket gán Principal định danh hợp lệ cho session, cho phép định tuyến chính xác tin nhắn duyệt riêng tư tới `/user/queue/rooms/join-result` của session đó.
*   **Hàng rào Bảo mật (ROLE_LISTENER Sandbox)**: Để tránh nguy cơ leo thang đặc quyền khi cấp `temporaryToken` (kẻ xấu dùng token này gọi vào các API nghiệp vụ hệ thống khác chỉ yêu cầu chung là đã đăng nhập), cấu hình Spring Security Filter Chain bắt buộc phải thiết lập một phân vùng cô lập (Sandbox) nghiêm ngặt cho vai trò `ROLE_LISTENER`:
    *   Vai trò `ROLE_LISTENER` chỉ được phép kết nối WebSocket endpoint `/ws`, và truy cập vào các API join/leave room hiện tại.
    *   Bị chặn đứng và trả lỗi `403 Forbidden` ở toàn bộ các API nghiệp vụ hệ thống khác.

#### E. Dọn dẹp thành viên khi mất kết nối (WebSocket Disconnect Cleanup)
*   Khi Listener ngắt kết nối WebSocket đột ngột (đóng trình duyệt, mất mạng), Backend bắt sự kiện `SessionDisconnectEvent` của Spring WebSocket để dọn dẹp bộ nhớ (tránh lỗi tích tụ thành viên ảo làm phòng bị báo đầy `ROOM_FULL`):
    *   Hệ thống thực thi một Redis Lua Script để giảm `currentParticipants` đi 1 đơn vị trên Redis Hash `room:status:{roomCode}` và xóa `userId` của Listener khỏi Redis Hash `room:members:{roomCode}` một cách nguyên tử.
    *   Đồng thời xóa Listener khỏi ZSet hàng chờ `room:waiting:{roomCode}` và Hash metadata `room:waiting_metadata:{roomCode}` (nếu có) bằng cách gửi lệnh `ZREM` và `HDEL` theo `userId` của Listener.
    *   Broadcast danh sách thành viên còn lại qua topic `/topic/rooms/{roomCode}/members` để đồng bộ UI của phòng.

---

### 1.3. Quy tắc Xác thực Dữ liệu (Validation Rules)

| API Request | Trường dữ liệu | Ràng buộc | Annotation (Backend) | Mô tả |
| :--- | :--- | :--- | :--- | :--- |
| `JoinRoomRequest` | `displayName` | Bắt buộc, không để trống, tối đa 30 ký tự | `@NotBlank`, `@Size(max=30)` | Tên hiển thị của khách hàng trong phòng |
| `ApproveRejectRequest`| `listenerId` | Bắt buộc, định dạng UUID | `@NotNull` | ID tài khoản người dùng của khách hàng cần duyệt |

---

### 1.4. Giới hạn Tần suất Truy cập API (IP Rate Limiting)

| API Endpoint | Giới hạn | Mô tả |
| :--- | :--- | :--- |
| `POST /api/v1/rooms/{roomCode}/join` | **5 requests / phút / IP** | Tránh spam gửi yêu cầu tham gia phá hoại phòng chờ của Host |
| `POST /api/v1/rooms/{roomCode}/waiting/approve` | **20 requests / phút / IP** | Tần suất thao tác duyệt của Host |

---

## 🔄 2. User Flow & Sequence Diagram (Luồng người dùng & Sơ đồ tuần tự)

### 2.1. Luồng Gửi Yêu cầu tham gia và Đưa vào Phòng chờ (Join Request Flow)

```mermaid
sequenceDiagram
    autonumber
    actor Listener as Client Listener
    participant FE as Frontend App
    participant BE as Backend (Spring Boot)
    participant Redis as Redis Cache
    participant HostFE as Host Frontend App

    Listener->>FE: Nhập Room Code & Tên hiển thị -> Nhấn "Vào phòng"
    FE->>BE: POST /api/v1/rooms/{roomCode}/join (JoinRoomRequest)
    
    BE->>Redis: Lấy cấu hình và số lượng từ key 'room:status:{roomCode}' (HGETALL)
    alt Phòng không tồn tại hoặc status != ACTIVE
        BE-->>FE: HTTP 404 Not Found (ROOM_NOT_FOUND)
    else Phòng hoạt động
        alt Phòng đã đầy cứng (currentParticipants >= 7)
            BE-->>FE: HTTP 400 Bad Request (ROOM_FULL)
        else Còn chỗ trống (currentParticipants < 7)
            BE->>BE: Sinh temporaryToken (JWT chứa userId tạm, roomCode, role=LISTENER)
            alt Chế độ phòng là OPEN (Vào tự do)
                BE->>Redis: Chạy Lua Script check-and-increment giữ chỗ & lưu 'room:members:{roomCode}'
                BE-->>FE: HTTP 200 OK (status='APPROVED', temporaryToken, accessGranted=true)
                FE->>BE: Kết nối WebSocket (CONNECT header Authorization: Bearer temporaryToken)
                FE->>BE: SUBSCRIBE /topic/rooms/{roomCode}/members
                BE->>FE: Broadcast danh sách thành viên mới qua topic
            else Chế độ phòng là MODERATED (Kiểm duyệt - Bỏ qua tăng count)
                BE->>Redis: Thêm userId vào ZSet 'room:waiting:{roomCode}'
                BE->>Redis: Lưu thông tin vào Hash 'room:waiting_metadata:{roomCode}' (userId -> JSON metadata)
                BE-->>FE: HTTP 200 OK (status='WAITING', temporaryToken, accessGranted=false)
                FE->>BE: Kết nối WebSocket (CONNECT header Authorization: Bearer temporaryToken)
                FE->>BE: SUBSCRIBE /user/queue/rooms/join-result (Nhận kết quả duyệt)
                FE-->>Listener: Chuyển sang giao diện màn hình Chờ Host duyệt
                BE->>HostFE: Tin nhắn WebSocket qua /topic/rooms/{roomCode}/host: "Có yêu cầu tham gia mới từ {displayName}"
            end
        end
    end
```

---

### 2.2. Luồng Host phê duyệt hoặc từ chối thành viên (Approve / Reject Flow)

```mermaid
sequenceDiagram
    autonumber
    actor Host as Producer (Host)
    participant HostFE as Host Frontend App
    participant BE as Backend (Spring Boot)
    participant Redis as Redis Cache
    participant ListFE as Listener Frontend App

    Host->>HostFE: Click nút "Đồng ý" (Approve) duyệt Listener_A
    HostFE->>BE: POST /api/v1/rooms/{roomCode}/waiting/approve (ApproveRejectRequest)
    
    BE->>Redis: Chạy Lua Script dịch chuyển trạng thái nguyên tử (Approve State Migration)
    Note over Redis: Kiểm tra currentParticipants < maxParticipants
    Note over Redis: Nếu hợp lệ: tăng count, xóa khỏi waiting & waiting_metadata, thêm vào members
    Redis-->>BE: Trả về kết quả (1 = Thành công, 0 = Đầy/Lỗi)
    
    alt Phòng đã đầy (Lua Script trả về 0)
        BE-->>HostFE: HTTP 400 Bad Request (ROOM_FULL)
    else Duyệt thành công (Lua Script trả về 1)
        par Trả kết quả về Host
            BE-->>HostFE: HTTP 200 OK (Duyệt thành công)
            BE->>BE: Broadcast danh sách thành viên mới qua /topic/rooms/{roomCode}/members
        and Gửi tín hiệu WebSocket riêng tới Listener_A (Đang kết nối)
            BE->>Redis: Gửi tin nhắn qua /user/queue/rooms/join-result (status='APPROVED')
            ListFE->>BE: SUBSCRIBE các kênh thành viên, nhạc, chat của phòng (/topic/rooms/*)
            ListFE-->>ListFE: Chuyển giao diện từ Chờ sang Trang Phòng ảo Live
        end
    end
    
    Note over Host, ListFE: --- Kịch bản từ chối (Reject) ---
    Host->>HostFE: Click nút "Từ chối" (Reject)
    HostFE->>BE: POST /api/v1/rooms/{roomCode}/waiting/reject (ApproveRejectRequest)
    BE->>Redis: Xóa Listener_A khỏi ZSet 'room:waiting:{roomCode}' & Hash 'room:waiting_metadata:{roomCode}'
    BE-->>HostFE: HTTP 200 OK
    BE->>BE: Gửi tin nhắn qua /user/queue/rooms/join-result (status='REJECTED')
    ListFE-->>ListFE: Đóng kết nối WebSocket & quay về trang nhập mã
```

---

### 2.3. Luồng Dọn dẹp Thành viên khi Ngắt kết nối (WebSocket Disconnect Cleanup Flow)

```mermaid
sequenceDiagram
    autonumber
    actor Listener as Client Listener
    participant BE as Backend (Spring Boot)
    participant Redis as Redis Cache

    Listener->>BE: Ngắt kết nối WebSocket đột ngột (SessionDisconnectEvent)
    BE->>BE: Bóc tách Principal để lấy userId & roomCode từ temporaryToken
    
    BE->>Redis: Chạy Lua Script dọn dẹp nguyên tử (room:status, room:members, room:waiting, room:waiting_metadata)
    Note over Redis: Giảm currentParticipants đi 1 đơn vị
    Note over Redis: Xóa userId khỏi Hash room:members
    Note over Redis: Xóa userId khỏi ZSet room:waiting & Hash room:waiting_metadata
    Redis-->>BE: Dọn dẹp thành công
    
    BE->>BE: Broadcast danh sách thành viên còn lại qua /topic/rooms/{roomCode}/members
```

---

## 💾 3. Database & Cache Schema (Thiết kế Cơ sở Dữ liệu & Cache)

### 3.1. Thiết kế Cache Redis thời gian thực

Hệ thống quản lý hàng chờ và danh sách thành viên online hoàn toàn trên Redis để đạt độ trễ mili giây:

| Định dạng Khóa (Redis Key) | Kiểu dữ liệu | Giá trị (Value) | TTL | Mục đích sử dụng |
| :--- | :--- | :--- | :--- | :--- |
| `room:waiting:{roomCode}` | `ZSet` | `userId` (Ví dụ: `c8b74f51-...`) | **4 giờ** | Danh sách hàng chờ duyệt (chỉ lưu ID để ZREM chính xác khi disconnect) xếp theo thời gian gửi yêu cầu (`timestamp` làm score). |
| `room:waiting_metadata:{roomCode}` | `Hash` | Key: `userId`<br>Value: JSON string chứa `displayName`, `requestedAt` | **4 giờ** | Metadata chi tiết của Listener trong hàng chờ để Host hiển thị. |
| `room:members:{roomCode}` | `Hash` | Key: `userId`<br>Value: JSON string chứa `displayName`, `role`, `joinedAt` | **4 giờ** | Danh sách các thành viên đang online thực tế trong phòng. |

#### ⚡ Redis Lua Script kiểm tra & tăng số lượng thành viên nguyên tử cho chế độ OPEN (Atomic Check-and-Set)
Đoạn code script được nạp và chạy trực tiếp trên Redis để tránh Race Condition vượt quá 7 người khi kết nối trực tiếp:

```lua
-- KEYS[1]: room:status:{roomCode}
-- Trả về 1 nếu còn chỗ trống và đã tăng, trả về 0 nếu phòng đã đầy
local current = redis.call('hget', KEYS[1], 'currentParticipants')
local max = redis.call('hget', KEYS[1], 'maxParticipants')

if not current or not max then
    return 0
end

if tonumber(current) < tonumber(max) then
    redis.call('hincrby', KEYS[1], 'currentParticipants', 1)
    return 1
else
    return 0
end
```

#### ⚡ Redis Lua Script dịch chuyển trạng thái phê duyệt nguyên tử cho Host Approve (Atomic Host Approval State Migration)
Đoạn code script giúp đồng bộ và di chuyển Listener từ hàng chờ sang thành viên chính thức một cách nguyên tử (All-or-Nothing):

```lua
-- KEYS[1]: room:status:{roomCode}
-- KEYS[2]: room:waiting:{roomCode}
-- KEYS[3]: room:waiting_metadata:{roomCode}
-- KEYS[4]: room:members:{roomCode}
-- ARGV[1]: userId
-- ARGV[2]: memberMetadataJson (Ví dụ: '{"displayName":"NguyenArtist","role":"LISTENER","joinedAt":"..."}')
-- Trả về 1 nếu phê duyệt thành công, trả về 0 nếu phòng đầy hoặc không tồn tại

local current = redis.call('hget', KEYS[1], 'currentParticipants')
local max = redis.call('hget', KEYS[1], 'maxParticipants')

if not current or not max then
    return 0
end

if tonumber(current) < tonumber(max) then
    -- 1. Tăng count người tham gia phòng
    redis.call('hincrby', KEYS[1], 'currentParticipants', 1)
    -- 2. Xóa Listener khỏi ZSet hàng chờ và Hash metadata hàng chờ
    redis.call('zrem', KEYS[2], ARGV[1])
    redis.call('hdel', KEYS[3], ARGV[1])
    -- 3. Thêm Listener vào Hash danh sách thành viên online chính thức
    redis.call('hset', KEYS[4], ARGV[1], ARGV[2])
    return 1
else
    return 0
end
```

---

## 🔌 4. API & WebSocket Specifications (Đặc tả API)

### 4.0. Cấu hình Chung
*   **Base Path**: `/api/v1`
*   **Headers**: `Authorization: Bearer <accessToken>` (Với các API yêu cầu xác thực)

---

### 4.1. API Listener Gửi yêu cầu tham gia phòng (Join Room)
*   **Method**: `POST`
*   **Path**: `/api/v1/rooms/{roomCode}/join`
*   **Auth Level**: `PermitAll` (Cho phép khách vãng lai tham gia bằng cách nhập tên)

#### Request Body (`JoinRoomRequest`):
```json
{
  "displayName": "Ca sĩ Khánh Phương"
}
```

#### Response Thành công trong chế độ OPEN (Vào thẳng):
```json
{
  "success": true,
  "message": "Yêu cầu tham gia được phê duyệt trực tiếp",
  "data": {
    "status": "APPROVED",
    "accessGranted": true,
    "roomCode": "A8B9D1",
    "mode": "OPEN",
    "temporaryToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJlNWI4NGYzMi0zYTc4LTQzZDktOTUyNC0zNGU4MDNjNGYyYWEiLCJyb29tQ29kZSI6IkE4QjlEMSIsInJvbGUiOiJMSVNURU5FUiJ9..."
  },
  "errors": null,
  "timestamp": "2026-07-01T15:00:00Z"
}
```

#### Response Thành công trong chế độ MODERATED (Vào hàng chờ):
```json
{
  "success": true,
  "message": "Đã gửi yêu cầu tham gia thành công. Vui lòng chờ Producer phê duyệt.",
  "data": {
    "status": "WAITING",
    "accessGranted": false,
    "roomCode": "A8B9D1",
    "mode": "MODERATED",
    "temporaryToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJlNWI4NGYzMi0zYTc4LTQzZDktOTUyNC0zNGU4MDNjNGYyYWEiLCJyb29tQ29kZSI6IkE4QjlEMSIsInJvbGUiOiJMSVNURU5FUiJ9..."
  },
  "errors": null,
  "timestamp": "2026-07-01T15:00:00Z"
}
```

---

### 4.2. API Host lấy danh sách chờ (Get Waiting List)
*   **Method**: `GET`
*   **Path**: `/api/v1/rooms/{roomCode}/waiting`
*   **Auth Level**: `Requires ROLE_USER_PRO` (Chỉ Host mới gọi được)

#### Response Thành công (200 OK):
```json
{
  "success": true,
  "message": "Lấy danh sách hàng chờ thành công",
  "data": [
    {
      "listenerId": "e5b84f32-3a78-43d9-9524-34e803c4f2aa",
      "displayName": "Ca sĩ Khánh Phương",
      "requestedAt": "2026-07-01T15:02:00Z"
    }
  ],
  "errors": null,
  "timestamp": "2026-07-01T15:03:00Z"
}
```

---

### 4.3. API Host duyệt phê duyệt (Approve/Reject)
*   **Method**: `POST`
*   **Path**: `/api/v1/rooms/{roomCode}/waiting/approve` (Hoặc `/reject` đối với từ chối)
*   **Auth Level**: `Requires ROLE_USER_PRO`

#### Request Body (`ApproveRejectRequest`):
```json
{
  "listenerId": "e5b84f32-3a78-43d9-9524-34e803c4f2aa"
}
```

#### Response Thành công (200 OK):
```json
{
  "success": true,
  "message": "Duyệt thành viên tham gia phòng thành công",
  "data": null,
  "errors": null,
  "timestamp": "2026-07-01T15:04:00Z"
}
```

---

### 4.4. Đặc tả Kênh Tin nhắn WebSocket STOMP
*   **Kênh Đẩy yêu cầu tham gia tới Host (Subscribe)**:
    *   `/topic/rooms/{roomCode}/host`: Host đăng ký để nhận tin thông báo thời gian thực khi có Listener mới đăng ký vào Waiting List.
*   **Kênh Trả kết quả duyệt riêng tư cho Listener (User Destination)**:
    *   `/user/queue/rooms/join-result`: Listener đăng ký kênh này (sử dụng User Destination của Spring STOMP) để nhận kết quả phê duyệt. Payload nhận được chứa `status: 'APPROVED'`, `status: 'REJECTED'` hoặc `status: 'KICKED'`.

---

### 4.5. Phụ lục Mã Lỗi Nghiệp Vụ (Error Codes)

| Http Status | Error Code (String) | Mô tả | Trường liên quan (`field`) |
| :--- | :--- | :--- | :--- |
| `400 Bad Request` | `ROOM_FULL` | Phòng đã đạt giới hạn 7 người kết nối đồng thời | `null` |
| `404 Not Found` | `ROOM_NOT_FOUND` | Không tìm thấy mã phòng yêu cầu hoặc phòng đã đóng | `roomCode` |
| `404 Not Found` | `SESSION_NOT_FOUND` | Không tìm thấy yêu cầu chờ duyệt của Listener này trên Redis | `listenerId` |

---

## 🎨 5. UI/UX & Frontend Integration Notes (Giao diện & Tích hợp)

### 5.1. Thiết kế Giao diện Đơn sắc & Hỗ trợ Tiếp cận (Grayscale Theme & A11y)
*   **Màn hình Chờ duyệt của Listener**: Thiết kế tối giản, hiển thị Spinner xoay đơn sắc nhạt và thông điệp: *"Yêu cầu đang chờ duyệt... Vui lòng đợi Producer"* kèm theo nút "Hủy yêu cầu" (nếu khách hàng không muốn đợi nữa).
*   **Giao diện quản lý hàng chờ của Host**: Hiển thị một danh mục nhỏ gọn góc phải hoặc góc trái màn hình Dashboard, liệt kê tên các thành viên đang đợi kèm theo 2 nút phẳng đơn sắc: `[ Duyệt ]` (nền đen chữ trắng) và `[ Từ chối ]` (viền xám chữ đen).
*   **Xử lý UI khi bị Kick (Mời ra ngoài)**: Khi Listener nhận được thông điệp `status: 'KICKED'` qua WebSocket, Frontend lập tức đóng kết nối WebSocket, ngắt luồng WebRTC Mesh hiện tại, hiển thị một Modal/Alert đơn sắc thông báo: *"Bạn đã bị chủ phòng mời ra ngoài"*, và tự động chuyển hướng người dùng quay lại màn hình nhập mã `/rooms/join`.
*   **Thuộc tính Accessibility (A11y)**:
    *   Các input nhập mã phòng và tên hiển thị bắt buộc định nghĩa đầy đủ `id`, `name`, `aria-label`.
    *   Nút duyệt/từ chối trên màn hình Host có `aria-label` tương ứng (ví dụ: `aria-label="Phê duyệt Ca sĩ Khánh Phương vào phòng"`).

---

### 5.2. Tối ưu hóa Hiệu năng & Trải nghiệm Lập trình viên (Performance & DevEx)
*   **Chống gửi yêu cầu trùng lặp (Double Submit Prevention)**:
    *   Khi bấm nút "Vào phòng" hoặc nút "Duyệt / Từ chối", các nút này lập tức bị vô hiệu hóa (`disabled`) và hiển thị Spinner xoay để chặn việc người dùng gửi liên tiếp các request trùng lặp (Debounce/Throttle).
*   **Xác thực tại Client (Client-Side Validation)**:
    *   Sử dụng Zod để validate mã phòng (đúng 6 ký tự, không trống) và tên hiển thị (tối thiểu 2, tối đa 30 ký tự) trước khi gọi API.
*   **Xử lý Ngoại lệ mạng (Network Offline Resilience)**:
    *   Frontend sử dụng bộ theo dõi trạng thái mạng (`window.navigator.onLine`).
    *   Nếu Listener bị mất mạng trong lúc đang ở màn hình Chờ duyệt, giao diện hiển thị cảnh báo đỏ *"Mất kết nối mạng, đang chờ kết nối lại..."* thay vì treo ứng dụng.

---

### 5.3. Trạng thái Kết nối & Trải nghiệm Reconnection (WebSocket UX)
*   **Trạng thái Kết nối (Connection Indicators)**:
    *   Cả Host và Listener (sau khi được duyệt) đều hiển thị một Badge nhỏ góc trên màn hình biểu diễn trạng thái kết nối WebSocket STOMP: `Connecting` (Xám nháy), `Connected` (Đen chữ Trắng), `Disconnected` (Xám đậm).
*   **Hạn chế Đa tab (Multi-tab Prevention)**:
    *   Sử dụng `BroadcastChannel` để theo dõi. Nếu phát hiện Listener mở tab thứ hai của cùng một phòng Live Room, tab cũ sẽ nhận thông báo và tự động giải phóng/redirect để tránh WebRTC Mesh conflict.

---

### 5.4. Sơ đồ Luồng Màn hình (Screen Flow)

Luồng chuyển dịch giao diện đối với Listener:

```mermaid
graph TD
    classDef default fill:#f9f9f9,stroke:#333,stroke-width:1px,color:#000;
    classDef screen fill:#ffffff,stroke:#000,stroke-width:2px,font-weight:bold,color:#000;
    classDef action fill:#e1e1e1,stroke:#666,stroke-width:1px,stroke-dasharray: 5 5,color:#000;

    JoinPage["Màn hình Nhập mã <br> /rooms/join"]:::screen -->|Bấm vào phòng| JoinAPI{Gọi API POST /join}:::action
    
    JoinAPI -->|Trả status=APPROVED Chế độ OPEN| LiveRoomPage["Màn hình Phòng Live <br> /rooms/A8B9D1"]:::screen
    
    JoinAPI -->|Trả status=WAITING Chế độ MODERATED| WaitingPage["Màn hình Chờ duyệt <br> /rooms/waiting"]:::screen
    
    WaitingPage -->|1. Nhận WebSocket: APPROVED| LiveRoomPage
    WaitingPage -->|2. Nhận WebSocket: REJECTED| JoinPage
    WaitingPage -->|3. Click Hủy yêu cầu| JoinPage
```

---

## 📊 6. Logging & Observability (Ghi nhận Nhật ký & Giám sát)

### 6.1. Log Points (Các điểm ghi log)

| Level | Sự kiện | Payload mẫu |
| :--- | :--- | :--- |
| `INFO` | Join request received | `{"event": "JOIN_REQUEST", "roomCode": "A8B9D1", "listenerName": "Ca sĩ Khánh Phương"}` |
| `INFO` | Added to waiting list | `{"event": "WAITING_LIST_ADDED", "roomCode": "A8B9D1", "userId": "e5b84f32-..."}` |
| `INFO` | Listener approved | `{"event": "LISTENER_APPROVED", "roomCode": "A8B9D1", "listenerId": "e5b84f32-...", "hostId": "c8b74f51-..."}` |
| `INFO` | Listener rejected | `{"event": "LISTENER_REJECTED", "roomCode": "A8B9D1", "listenerId": "e5b84f32-..."}` |
| `WARN` | Join blocked (Room full) | `{"event": "JOIN_BLOCKED_FULL", "roomCode": "A8B9D1", "currentCount": 7}` |
| `WARN` | Lazy cleanup executed | `{"event": "LAZY_CLEANUP_EXECUTED", "roomCode": "A8B9D1", "removedCount": 1}` |
| `ERROR` | Lua Script execution failure | `{"event": "REDIS_LUA_FAILED", "roomCode": "A8B9D1", "error": "Redis scripting error"}` |

### 6.2. Quy tắc Bảo mật Log
*   Không log thông tin nhạy cảm của cookie hay token người dùng trong quá trình gọi API.
*   **Masking**: Mã hóa hoặc ẩn danh IP của khách hàng trong log tương thích các quy định bảo mật.

---

## 📊 7. Kiến trúc Lưu vết Lịch sử (Design Choice Note - Persistent vs Ephemeral)

*   **Bản chất Dữ liệu real-time**: Toàn bộ dữ liệu thành viên online, phòng chờ và trạng thái hiện tại được lưu trữ hoàn toàn trên RAM Redis (Ephemeral) để đảm bảo độ trễ thấp và tự động giải phóng/dọn dẹp nhanh chóng khi kết nối ngắt kết nối.
*   **Giải pháp Lưu trữ Lịch sử (Analytics/Reporting)**: Trường hợp hệ thống phát triển tính năng thống kê trong tương lai (ví dụ: đếm tổng số lượt khách ghé thăm, thời gian nghe nhạc trung bình), hệ thống sẽ áp dụng giải pháp lưu trữ bất đồng bộ để tránh gây nghẽn luồng xử lý chính:
    *   Tại thời điểm xử lý thành công luồng tham gia phòng hoặc ngắt kết nối WebSocket, Backend phát các sự kiện tương ứng: `LISTENER_JOINED` và `LISTENER_LEFT` sang Apache Kafka.
    *   Một Data Worker độc lập (Analytics Consumer) sẽ tiêu thụ các sự kiện này và ghi nhận/persist vào bảng lưu trữ lịch sử dưới PostgreSQL làm dữ liệu kho (Data Warehouse).
    *   Thiết kế này đảm bảo tách biệt hoàn toàn luồng nghiệp vụ real-time chính với luồng ghi nhận lịch sử phục vụ báo cáo.
