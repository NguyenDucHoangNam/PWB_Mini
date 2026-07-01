# 05. Phân quyền Điều khiển trình phát (Control Delegation)

Tài liệu đặc tả A-Z tính năng Phân quyền Điều khiển trình phát (Control Delegation) trong phòng Live Room, cho phép Host (Producer) ủy quyền điều khiển Play/Pause/Seek bài hát cho một Listener cụ thể hoặc mở quyền toàn phòng thời gian thực qua Redis Cache và WebSocket.

---

## 📋 1. Business & Requirements (Nghiệp vụ & Yêu cầu)

### 1.1. Mô tả Nghiệp vụ (User Story / Use Case)
*   **Đối tượng thực hiện**: Host (Chủ phòng Live Room - `ROLE_USER_PRO`).
*   **Quy trình tóm tắt**:
    *   *Ủy quyền cá nhân*: Host mở danh sách thành viên online, click biểu tượng "Cấp quyền điều khiển" cạnh tên Listener_A. Backend ghi nhận và gửi thông báo qua WebSocket. Trình phát của Listener_A lập tức mở khóa (enable) các nút bấm điều khiển.
    *   *Ủy quyền toàn phòng*: Host click bật tùy chọn "Cho phép tất cả điều khiển". Toàn bộ Listener trong phòng lập tức được mở khóa nút bấm.
    *   *Thu hồi quyền*: Host tắt quyền điều khiển cá nhân hoặc toàn phòng, trình phát của các Listener tương ứng bị khóa lại (disabled).

### 1.2. Quy tắc Nghiệp vụ (Business Rules)

#### A. Quyền phân cấp & Quản lý vai trò điều khiển
*   **Chỉ Host mới có quyền phân quyền**: Listener được ủy quyền điều khiển nhạc chỉ được phép Play/Pause/Seek nhạc cục bộ, tuyệt đối không có quyền gọi API phân quyền cho người khác. Mọi nỗ lực truy cập API phân quyền của Listener đều bị chặn với mã lỗi HTTP `403 Forbidden`.
*   **Lưu trữ thông tin ủy quyền (Redis State)**:
    *   *Cấp quyền toàn phòng*: Trạng thái này lưu trong Redis Hash `room:status:{roomCode}` tại trường `globalDelegation` với giá trị `"true"` hoặc `"false"`.
    *   *Cấp quyền cá nhân*: Lưu danh sách ID người dùng (userId) được ủy quyền vào một Redis Set mang tên `room:delegated:{roomCode}`.
*   **Xóa dọn dẹp khi thực sự rời phòng (Cleanup on Actual Leave)**: Quyền điều khiển được gắn chặt với danh sách thành viên thực tế trong phòng (`room:members`). Hệ thống **không** tự động xóa `userId` khỏi Redis Set `room:delegated:{roomCode}` khi xảy ra sự cố ngắt kết nối WebSocket đột ngột (để hỗ trợ Listener khôi phục quyền điều khiển sau khi Reconnect thành công). Hệ thống chỉ thực thi lệnh `SREM` dọn dẹp quyền điều khiển khi Listener chủ động bấm nút "Rời phòng" hoặc bị Host "Kick" (đá) khỏi phòng Live.
*   **Chống trùng lặp ID Khách vãng lai (Guest ID Collision Prevention)**: Do Listener có thể là Khách vãng lai sử dụng Temporary JWT, mã định danh `userId` tạm thời trong Token này bắt buộc phải được sinh bằng cấu trúc UUID v4 tiêu chuẩn. Điều này đảm bảo tính ngẫu nhiên tuyệt đối, triệt tiêu hoàn toàn tỷ lệ trùng lặp ID dẫn đến việc Listener mới vào phòng nghiễm nhiên thừa hưởng nhầm quyền điều khiển của Listener cũ đã thoát.
*   **Dọn dẹp triệt để khi Đóng phòng (Explicit Eviction on Close)**: Khi Host chủ động đóng phòng vĩnh viễn (Usecase 01) hoặc khi phòng tự hủy do hết hạn 4 giờ, ngoài việc dọn dẹp phòng và thành viên, Backend bắt buộc phải chạy lệnh `DEL room:delegated:{roomCode}` để xóa sạch hoàn toàn Redis Set này, ngăn chặn tình trạng mồ côi key, tối ưu bộ nhớ đệm.

#### B. Phản hồi giao diện thời gian thực & Đồng bộ cờ in-memory trong cụm node (Redis Pub/Sub Event Broadcasting)
*   **Đồng bộ cờ in-memory trong cụm node (Redis Pub/Sub Sync)**:
    *   Do yêu cầu phân quyền được gửi qua HTTP API (ví dụ: gửi lên Node A trong cụm), trong khi phiên kết nối WebSocket của Listener_A có thể đang duy trì trên Node B.
    *   Để tránh hiện tượng bất đồng bộ bộ nhớ tạm (In-Memory State Desynchronization) khiến Node B check in-memory fail và chặn lệnh gửi từ Listener_A:
    *   Khi bất kỳ Node nào xử lý thành công HTTP API phân quyền (`GRANT` hoặc `REVOKE`), ngoài việc cập nhật Redis Set/Hash, Node đó bắt buộc phải phát một bản tin broadcast xuống Redis Pub/Sub channel tên là `room-delegation-events` với payload JSON dạng:
      ```json
      {
        "userId": "e5b84f32-3a78-43d9-9524-34e803c4f2aa",
        "roomCode": "A8B9D1",
        "isController": true
      }
      ```
    *   Tất cả các Node trong cụm Backend đăng ký lắng nghe (Subscribe) channel này. Khi nhận tin, mỗi Node tự quét trong danh sách WebSocket Session Attributes cục bộ của mình. Nếu phát hiện đang quản lý kết nối của `userId` đó, Node đó lập tức cập nhật lại thuộc tính `isController` cục bộ trong RAM lên giá trị mới, đảm bảo tính nhất quán tức thì trên toàn bộ cụm.
*   **Phản hồi giao diện thời gian thực (Reactive Control lock/unlock)**:
    *   Khi có thay đổi ủy quyền, Backend gửi bản tin `DELEGATION_CHANGED` qua WebSocket broadcast.
    *   Tất cả Client nhận gói tin, so khớp logic:
        *   *Có quyền*: Nếu `globalDelegation` bằng `true`, HOẶC `userId` hiện tại có trong mảng `delegatedUserIds` -> Mở khóa nút bấm Play/Pause và thanh tua Seek.
        *   *Không có quyền*: Nếu không thỏa mãn các điều kiện trên -> Khóa (disable) các nút bấm và thanh seek.

---

### 1.3. Quy tắc Xác thực Dữ liệu (Validation Rules)

| API Request | Trường dữ liệu | Ràng buộc | Annotation (Backend) | Mô tả |
| :--- | :--- | :--- | :--- | :--- |
| `DelegateControlRequest` | `listenerId` | Bắt buộc, đúng định dạng UUID | `@NotNull` | ID tài khoản của Listener cần cấp/thu hồi quyền |
| | `action` | Bắt buộc, giá trị phải là `GRANT` hoặc `REVOKE` | `@NotBlank`, `@Pattern` | Hành động cấp (`GRANT`) hoặc thu hồi (`REVOKE`) |
| `GlobalDelegationRequest`| `enabled` | Bắt buộc | `@NotNull` | Bật (`true`) hoặc tắt (`false`) quyền điều khiển toàn phòng |

---

### 1.4. Giới hạn Tần suất Truy cập API (IP Rate Limiting)

| API Endpoint | Giới hạn | Mô tả |
| :--- | :--- | :--- |
| `POST /api/v1/rooms/{roomCode}/delegation` | **10 requests / phút / IP** | Tránh spam thay đổi quyền liên tục |
| `POST /api/v1/rooms/{roomCode}/delegation/global` | **10 requests / phút / IP** | Tránh spam thay đổi trạng thái toàn phòng |

---

## 🔄 2. User Flow & Sequence Diagram (Luồng người dùng & Sơ đồ tuần tự)

### 2.1. Luồng Cấp quyền điều khiển cá nhân (Individual Control Delegation)

```mermaid
sequenceDiagram
    autonumber
    actor Host as Producer (Host)
    participant HostFE as Host Frontend App
    participant NodeA as Backend Node A (HTTP)
    participant Redis as Redis Cache
    participant NodeB as Backend Node B (WS)
    participant ListFE as Listener A Frontend

    Host->>HostFE: Click "Cấp quyền" cho Listener_A
    HostFE->>NodeA: POST /api/v1/rooms/{roomCode}/delegation (GRANT, listenerId=UUID_A)
    
    NodeA->>NodeA: Xác thực Host sở hữu phòng Live
    NodeA->>Redis: SADD 'room:delegated:{roomCode}' UUID_A
    
    Note over NodeA, NodeB: Đồng bộ cờ in-memory qua Redis Pub/Sub
    NodeA->>Redis: PUBLISH room-delegation-events {"userId":"UUID_A","roomCode":"roomCode","isController":true}
    Redis-->>NodeB: Broadcast event nhận được tới Node B (đăng ký từ trước)
    NodeB->>NodeB: Tìm session WS của Listener_A & cập nhật isController = true
    
    NodeA-->>HostFE: HTTP 200 OK (Cấp quyền thành công)
    
    Note over NodeA, ListFE: Phát sóng WebSocket thông báo thay đổi
    NodeA->>ListFE: Broadcast qua topic /playback (DELEGATION_CHANGED, globalDelegation=false, delegatedUserIds=[UUID_A])
    
    par Xử lý phía Listener_A
        ListFE->>ListFE: Nhận tin, thấy ID của mình thuộc delegatedUserIds
        ListFE->>ListFE: Mở khóa (Enable) các nút bấm điều khiển nhạc
    end
```

##### 📝 Mô tả chi tiết các bước xử lý:
1.  **Host ra lệnh**: Host nhấn icon ủy quyền cạnh Listener_A. Frontend gửi `POST /rooms/{roomCode}/delegation` với payload `listenerId = UUID_A` và `action = 'GRANT'`. Request này đi đến **Backend Node A** (node xử lý HTTP).
2.  **Cập nhật Redis & Pub/Sub**:
    *   Backend Node A xác thực Host, thêm UUID_A vào Set `room:delegated:{roomCode}` trên Redis.
    *   Đồng thời, Node A gửi thông điệp `PUBLISH` xuống Redis Pub/Sub channel `room-delegation-events`.
3.  **Đồng bộ Bộ nhớ Tạm Node B**:
    *   **Backend Node B** (node đang giữ kết nối WebSocket STOMP của Listener_A) nhận được message từ Redis Pub/Sub, tiến hành tìm kiếm STOMP Session cục bộ tương ứng của Listener_A và ghi đè thuộc tính in-memory `isController = true`.
4.  **Phát sóng & Frontend Cập nhật**:
    *   Node A gửi broadcast sự kiện `DELEGATION_CHANGED` qua WebSocket.
    *   Listener_A nhận tin, thấy ID của mình nằm trong mảng `delegatedUserIds` -> chuyển đổi state sang mở khóa nút bấm trình phát nhạc (lúc này các thao tác WS gửi lên Node B sẽ được thông qua cực nhanh vì cờ in-memory đã đồng bộ).
    *   Các Listener khác không thấy ID của mình -> giữ nguyên trạng thái khóa.

---

## 💾 3. Database & Cache Schema (Thiết kế Cơ sở Dữ liệu & Cache)

### 3.1. Cấu trúc dữ liệu Redis bổ sung (Delegation State Storage)

| Định dạng Khóa (Redis Key) | Kiểu dữ liệu | Giá trị (Value) | TTL | Mục đích sử dụng |
| :--- | :--- | :--- | :--- | :--- |
| `room:status:{roomCode}` | `Hash` | Thêm trường `globalDelegation`: `"true"`/`"false"` | **4 giờ** | Cấu hình bật/tắt quyền toàn phòng. |
| `room:delegated:{roomCode}` | `Set` | Danh sách các `userId` (UUID) | **4 giờ** | Lưu trữ các Listener đang có quyền điều khiển cá nhân. |

---

## 🔌 4. API & WebSocket Specifications (Đặc tả API & Kênh truyền tin)

### 4.0. Cấu hình Chung
*   **Base Path**: `/api/v1`
*   **Headers**: `Authorization: Bearer <accessToken>`

---

### 4.1. API Ủy quyền điều khiển cá nhân (Delegate Control)
*   **Method**: `POST`
*   **Path**: `/api/v1/rooms/{roomCode}/delegation`
*   **Auth Level**: `Requires ROLE_USER_PRO`

#### Request Body (`DelegateControlRequest`):
```json
{
  "listenerId": "e5b84f32-3a78-43d9-9524-34e803c4f2aa",
  "action": "GRANT"
}
```

#### Response Thành công (200 OK):
```json
{
  "success": true,
  "message": "Cấp quyền điều khiển trình phát thành công",
  "data": null,
  "errors": null,
  "timestamp": "2026-07-01T15:20:00Z"
}
```

---

### 4.2. API Ủy quyền điều khiển toàn phòng (Global Delegation)
*   **Method**: `POST`
*   **Path**: `/api/v1/rooms/{roomCode}/delegation/global`
*   **Auth Level**: `Requires ROLE_USER_PRO`

#### Request Body (`GlobalDelegationRequest`):
```json
{
  "enabled": true
}
```

#### Response Thành công (200 OK):
```json
{
  "success": true,
  "message": "Cập nhật quyền điều khiển toàn phòng thành công",
  "data": null,
  "errors": null,
  "timestamp": "2026-07-01T15:21:00Z"
}
```

---

### 4.3. Đặc tả Tin nhắn WebSocket Broadcast (`DELEGATION_CHANGED`)
*   **Kênh nhận tin (Subscribe Topic)**: `/topic/rooms/{roomCode}/playback`
*   **Payload tin nhắn (JSON)**:
```json
{
  "event": "DELEGATION_CHANGED",
  "data": {
    "globalDelegation": false,
    "delegatedUserIds": [
      "e5b84f32-3a78-43d9-9524-34e803c4f2aa"
    ]
  }
}
```

---

### 4.4. Phụ lục Mã Lỗi Nghiệp Vụ (Error Codes)

| Http Status | Error Code (String) | Mô tả | Trường liên quan (`field`) |
| :--- | :--- | :--- | :--- |
| `400 Bad Request` | `VALIDATION_FAILED` | Hành động không hợp lệ (phải là `GRANT` hoặc `REVOKE`) | `action` |
| `403 Forbidden` | `FORBIDDEN_ACCESS` | Người gọi không có quyền Host của phòng này | `null` |

---

## 🎨 5. UI/UX & Frontend Integration Notes (Giao diện & Tích hợp)

### 5.1. Thiết kế Giao diện Phân quyền (Grayscale Theme & A11y)
*   **Bảng quản lý thành viên của Host**:
    *   Mỗi dòng thành viên trong danh sách online của Host hiển thị một icon hình chìa khóa hoặc bảng điều khiển nhỏ.
    *   Icon ở trạng thái chưa cấp quyền: Màu xám nhạt nét đứt (`stroke-neutral-300`).
    *   Icon ở trạng thái đã cấp quyền: Màu đen đậm (`stroke-black`).
    *   Nút bật/tắt toàn phòng: Dạng Switch đơn sắc phẳng (Nền xám nhạt khi tắt, đen khi bật).
*   **Accessibility (A11y)**:
    *   Các Switch và icon tương tác của Host bắt buộc khai báo vai trò `role="switch"`, `aria-checked="true/false"`, kèm nhãn `aria-label="Cấp quyền điều khiển cho Listener A"`.

---

### 5.2. Tối ưu hóa Hiệu năng & Trải nghiệm Lập trình viên (Performance & DevEx)
*   **State Sync trong Zustand Store**:
    *   Frontend lưu trạng thái `isPlayerEditable: boolean` trong Zustand. Trình phát nhạc sẽ đăng ký theo dõi (subscribe) thuộc tính này để cập nhật trạng thái `disabled` của các nút bấm Play/Pause/Seek ngay lập tức khi thay đổi.
*   **Chống spam click phân quyền**:
    *   Vô hiệu hóa tạm thời icon click phân quyền của dòng đó trong khi đợi API phản hồi (mờ đục icon, đặt `pointer-events: none`).

---

### 5.3. Trạng thái Kết nối & Trải nghiệm Reconnection (WebSocket UX)
*   Khi kết nối lại thành công sau khi mất mạng, Client gọi đồng thời API `/playback` để cập nhật lại cấu hình `globalDelegation` và danh sách `delegatedUserIds` mới nhất từ Redis, khôi phục trạng thái khóa/mở nút bấm chính xác.

---

### 5.4. Sơ đồ Luồng Màn hình (Screen Flow)

```mermaid
graph TD
    classDef default fill:#f9f9f9,stroke:#333,stroke-width:1px,color:#000;
    classDef screen fill:#ffffff,stroke:#000,stroke-width:2px,font-weight:bold,color:#000;
    classDef action fill:#e1e1e1,stroke:#666,stroke-width:1px,stroke-dasharray: 5 5,color:#000;

    LiveRoomPage["Màn hình Phòng Live <br> /rooms/A8B9D1"]:::screen -->|Host click biểu tượng phân quyền| CallDelegation{Gọi API POST /delegation}:::action
    
    CallDelegation -->|Trả 200 thành công| BroadcastWS[Nhận DELEGATION_CHANGED qua WebSocket]:::action
    
    BroadcastWS -->|Màn hình Listener A| EnableControls[Mở khóa nút bấm trình phát]:::action
    BroadcastWS -->|Màn hình Listener khác| DisableControls[Khóa nút bấm trình phát]:::action

    EnableControls --> LiveRoomPage
    DisableControls --> LiveRoomPage
```

---

## 📊 6. Logging & Observability (Ghi nhận Nhật ký & Giám sát)

### 6.1. Log Points (Các điểm ghi log)

| Level | Sự kiện | Payload mẫu |
| :--- | :--- | :--- |
| `INFO` | Individual delegation change | `{"event": "CONTROL_DELEGATED", "roomCode": "A8B9D1", "listenerId": "e5b84f32-...", "action": "GRANT", "hostId": "c8b74f51-..."}` |
| `INFO` | Global delegation change | `{"event": "GLOBAL_DELEGATION_CHANGED", "roomCode": "A8B9D1", "enabled": true, "hostId": "c8b74f51-..."}` |
| `WARN` | Unauthorized delegation attempt | `{"event": "UNAUTHORIZED_DELEGATE_ATTEMPT", "roomCode": "A8B9D1", "userId": "e5b84f32-..."}` |

### 6.2. Quy tắc Bảo mật Log
*   Chỉ ghi nhận ID người dùng, không ghi nhận thông tin cá nhân hay địa chỉ IP của Listener được phân quyền vào nhật ký.
