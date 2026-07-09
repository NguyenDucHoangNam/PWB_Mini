# 06. Thu hồi Quyền truy cập liên kết (Revoke Shared Link)

Tài liệu đặc tả A-Z tính năng Thu hồi quyền truy cập liên kết chia sẻ (Revoke Shared Link), cho phép Producer (Host) hủy bỏ hiệu lực của liên kết nghe thử tức thời, đồng bộ xóa cache và chặn đứng mọi yêu cầu truyền tải HLS hay tải tệp gốc tiếp theo từ phía khách hàng.

---

## 📋 1. Business & Requirements (Nghiệp vụ & Yêu cầu)

### 1.1. Mô tả Nghiệp vụ (User Story / Use Case)
*   **Đối tượng thực hiện**: Producer (Music Producer - `ROLE_USER_PRO`).
*   **Quy trình tóm tắt**:
    1.  Producer mở danh mục Lịch sử chia sẻ bản demo, click nút "Thu hồi" (Revoke) cạnh tên đối tác muốn khóa quyền.
    2.  Hệ thống hiển thị Modal xác nhận thu hồi.
    3.  Backend cập nhật cơ sở dữ liệu Postgres đánh dấu trạng thái thu hồi, đồng thời trục xuất (evict) dữ liệu khỏi Redis Cache.
    4.  Kể từ thời điểm đó, mọi yêu cầu từ phía khách hàng sử dụng Token của liên kết này để: truy cập giao diện, tải playlist HLS, lấy key giải mã AES-128, hoặc tải file gốc sẽ bị chặn đứng hoàn toàn (HTTP 403 Forbidden).

### 1.2. Quy tắc Nghiệp vụ (Business Rules)

#### A. Xác thực quyền hạn thu hồi (Authorization)
*   Chỉ Producer sở hữu bản nhạc demo đó mới được phép gọi API thu hồi quyền truy cập. 
*   Backend kiểm tra: Truy vấn bảng `demo_distributions` lấy thông tin `demo_id`, sau đó đối chiếu trường `owner_id` của bản demo đó trong bảng `demos` với `userId` của người gọi API. Nếu không khớp -> Trả lỗi HTTP `403 Forbidden` (`FORBIDDEN_ACCESS`).

#### B. Cơ chế vô hiệu hóa tức thời (Immediate Revocation)
*   Để tối ưu hiệu năng truy vấn khi khách hàng nghe nhạc, hệ thống cache cấu hình của bản phân phối vào Redis String: `demo:distribution:{shareToken}`.
*   **Quy trình đồng bộ khi thu hồi**:
    1.  Cập nhật thuộc tính `is_revoked = true` trong PostgreSQL ở bảng `demo_distributions`.
    2.  Xóa ngay lập tức key `demo:distribution:{shareToken}` khỏi Redis Cache.
    3.  Đồng thời, ghi nhận một khóa tạm thời để đánh dấu đã thu hồi: `demo:distribution:revoked:{shareToken}` với giá trị `"true"` và TTL **10 phút** trên Redis.
*   **Trám kẽ hở rò rỉ nhạc do bẫy Cookie sống dai (Secure Session Cookie Bypass)**: Mặc dù khách hàng đã có `SecureSessionCookie` hợp lệ với thời gian sống 1 giờ, tại endpoint trả khóa giải mã `/api/v1/stream/keys/{shareToken}`, trước khi phê duyệt cấp mảng byte nhị phân giải mã, Backend bắt buộc phải chạy lệnh `EXISTS demo:distribution:revoked:{shareToken}` xuống Redis. Nếu key này tồn tại (hoặc nếu key gốc `demo:distribution:{shareToken}` không còn tồn tại/DB check `is_revoked = true`), Backend lập tức từ chối và trả về lỗi `HTTP 403 Forbidden` để ngắt đứt luồng phát nhạc (Mid-stream Interruption) của Listener ngay lập tức.
*   **Cascade Revoke khi Producer xóa Demo gốc (CRITICAL — chống Orphaned Links)**: Khi `DELETE /api/v1/demos/{demoId}` (Producer xóa demo hoàn toàn), hệ thống **BẮT BUỘC** cascade revoke tất cả `demo_distributions` của demo đó (set `is_revoked=true`, populate audit `reason='demo-deleted'`). Lý do: nếu chỉ `demos.status = 'DELETED'` mà distribution vẫn `is_revoked=false`, các share link cũ vẫn đang phát nhạc trên Listener browser — cạn kiệt tài nguyên + vi phạm quyền Producer. Yêu cầu:
    1. Cascade chạy **trong CÙNG transaction** với update `demos.status = 'DELETED'`.
    2. Sau cascade, gọi logic `revokeDistribution(...)` cho TỪNG distribution để ghi audit + WS broadcast `DISTRIBUTION_REVOKED` + DEL cache + set blacklist `demo:distribution:revoked:{shareToken}` + jti revocation.
    3. Sau khi xóa, DEL `demo:key:{demoId}` AES key cache.
    4. Không chấp nhận "best-effort async" — nếu 1 distribution revoke fail, **rollback toàn bộ** transaction DELETE demo. Log `ERROR DELETE_DEMO_ROLLBACK {demoId, failedDistributionId, error}` để operator investigate.
    5. Sau 90 ngày xóa mới được áp dụng S3 Lifecycle xóa file `original/confirmed/...` (audit window).

#### C. Tính độc lập của liên kết
*   Việc thu hồi chỉ ảnh hưởng duy nhất đến mã Token (`shareToken`) của bản phân phối được chọn.
*   Các liên kết chia sẻ khác của cùng một bản demo đó gửi cho các đối tác khác vẫn hoạt động bình thường, không bị ảnh hưởng.

#### D. Đồng bộ giao diện Dòng thời gian của Đối tác (Shared Thread UI Sync via WebSockets)
*   Vì các liên kết chia sẻ được tổ chức hiển thị dưới dạng Luồng hội thoại lịch sử (`shared_threads`), nếu tại thời điểm Producer bấm nút thu hồi liên kết, đối tác nhận nhạc (Listener) cũng đang mở giao diện dòng thời gian của họ, bản ghi bài hát đó nên được cập nhật trạng thái thời gian thực.
*   Tại luồng xử lý thành công ở tầng Service, Backend phát đi một sự kiện WebSocket tới topic chung của cặp đôi đó:
    `{"event": "DISTRIBUTION_REVOKED", "data": {"shareToken": "{shareToken}"}}`
*   Frontend của đối tác khi nhận được tin này sẽ tự động chuyển trạng thái bài hát trên UI sang dạng làm mờ có gạch chéo kèm dòng chữ *"Liên kết đã bị thu hồi"*, ngăn chặn việc click phát nhạc bị báo lỗi giật cục, mang lại trải nghiệm UX nhất quán và tinh tế.

#### E. Xác thực quyền Subscribe WebSocket Topic (WS Authorization)
*   Mục 1.2.D gửi WS broadcast tới `/topic/shared-threads/{threadId}/distributions`. Bất kỳ ai biết `threadId` đều có thể subscribe.
*   **Rủi ro**: ThreadId có format UUID nhưng vẫn là identifier (không phải secret). Attacker có thể brute-force → nghe lỏm `DISTRIBUTION_REVOKED` events → biết Producer nào đang thu hồi share nào.
*   **Giải pháp 2 lớp**:
    1. **STOMP CONNECT auth**: Khi Listener mở kết nối WebSocket, phải xác thực bằng **Temporary Access Token** (TAT) ngắn hạn 5 phút lấy từ `GET /api/v1/demos/shared/{shareToken}/ws-token` sau khi đã verify cookie/IP ở docs 04. TAT chứa `{shareToken, demoId, recipientEmailHash, exp}`. Backend set trong STOMP Session Attribute qua `ChannelInterceptor`.
    2. **SUBSCRIBE authorization**: Tại `SUBSCRIBE /topic/shared-threads/{threadId}/distributions`, Backend check Session Attribute:
        - User có TAT với `shareToken` map tới `threadId` của shared_thread.
        - `recipient_email` trong shared_thread (lowercased) phải match với `recipientEmailHash` (lookup qua `shared_threads.recipient_email_hash`).
        - **Không match** → gửi `ERROR frame` STOMP `{"event":"SUBSCRIBE_DENIED","reason":"not-thread-participant"}` rồi disconnect.
*   **WS Channel Interceptor cấu hình** (xem docs 09 mục 2.5): trước khi message tới controller, interceptor tự động reject nếu thiếu TAT.

#### F. AES Key Rotation ngay khi Revoke (Active Defense)
*   Mục 1.2.B set `is_revoked=true` + cache revocation. Tuy nhiên, **AES key đã được serve cho Cookie trước đó vẫn còn valid** trong TTL cookie (30 phút theo docs 04 mục 1.2.D). Listener tiếp tục decrypt `.ts` segments đã cached.
*   **Giải pháp**: Ngay khi revoke thành công (cùng transaction hoặc async gần như tức thì):
    1. **DEL** Redis `demo:key:{demoId}` cache.
    2. **DEL** Tất cả jti trong Set `demo:distribution:active_sessions:{shareToken}` (xem docs 04 Redis catalog) — push vào `stream:cookie:revoked:{jti}` với TTL còn lại của cookie.
    3. Nếu không có distribution nào khác đang share cùng demo → trigger **rotate AES key** (xem docs 04 mục 1.2.E). Worker re-encrypt tất cả `.ts` segments với key mới. Listener cố nghe tiếp → nhận key mới từ cache (đã miss) → nhưng `is_revoked=true` → Backend từ chối serve key → listener bị chặn vĩnh viễn.
*   **Trade-off latency vs security**: Rotation async qua Kafka `audio-processing-events` topic (worker pool). Acceptable delay ~10-30 giây cho file dài.

#### G. Audit Trail cho Revoke (Compliance)
*   Mọi lệnh revoke (kể cả idempotent) đều phải ghi audit log để truy vết tranh chấp / compliance.
*   Tạo bảng `demo_revoke_audit`:
    ```sql
    CREATE TABLE demo_revoke_audit (
        id UUID PRIMARY KEY,
        distribution_id UUID NOT NULL,
        demo_id UUID NOT NULL,
        revoked_by_user_id UUID NOT NULL,        -- Producer nào thao tác
        revoked_at TIMESTAMP NOT NULL DEFAULT NOW(),
        reason VARCHAR(50),                      -- 'manual', 'auto-recovery', 'gdpr-request', 'compliance', 'demo-deleted'
        ip_subnet_hash VARCHAR(64) NULL,         -- SHA-256(IP_HASH_SALT + IP/CIDR_SUBNET) — chỉ giữ subnet /24 hash, KHÔNG raw INET (GDPR Art. 4(5) pseudonymization)
        user_agent VARCHAR(255),
        CONSTRAINT fk_audit_dist FOREIGN KEY (distribution_id) REFERENCES demo_distributions(id),
        CONSTRAINT fk_audit_user FOREIGN KEY (revoked_by_user_id) REFERENCES users(id)
    );
    CREATE INDEX idx_audit_dist ON demo_revoke_audit(distribution_id);
    CREATE INDEX idx_audit_time ON demo_revoke_audit(revoked_at);
    ```
*   **Retention**: Giữ 2 năm (compliance), sau đó archive S3 với encryption AES-256 (xem docs 09 §5.4 Database Backup Encryption).
*   **GDPR Policy**: Audit chỉ lưu `ip_subnet_hash` (CIDR /24 hash) chứ không phải full IP. Forensic có thể detect "Producer revoke từ subnet Hà Nội vs HCMC" mà không track thiết bị cá nhân. Subject Access Request xóa toàn bộ row khi user yêu cầu (mất forensic subnet nhưng audit trail revocation action vẫn còn ở bảng `demo_distributions.is_revoked`).

#### H. Idempotency cho Revoke (Idempotent Endpoint)
*   **Lịch sử thiết kế**: Bản đầu tiên của docs 04 §4.4 định nghĩa mã lỗi `409 DISTRIBUTION_ALREADY_REVOKED` cho trường hợp Producer gọi `/revoke` 2 lần. Sau R3 review, mã này đã được **loại bỏ** khỏi docs 04 §4.4 Error Codes table. Endpoint hiện hành xử sự idempotent như dưới đây.
*   **Quy tắc hiện hành**: Endpoint **idempotent** — gọi nhiều lần với cùng `distributionId` đều trả `200 OK`. Lần đầu thực hiện action, lần sau chỉ verify state và trả về success message.
*   **Tracking**: Redis `revoked:completed:{distributionId}` TTL **24 giờ** (tăng từ 10 phút vì Producer mobile thường retry trên slow network; tránh bị rate limit bởi proxy/CDN retry; tăng coverage cho luồng Cron reopen dashboard). Nếu có key này → skip DB update + WS broadcast (đã làm rồi), trả 200 OK.
*   **Sweep cleanup**: Cron chạy hằng đêm 03:30 UTC, scan keys có prefix `revoked:completed:*` với TTL đã expire → không cần DEL (Redis tự expire), nhưng emit metric `revoked_completed_total_size` để monitor Redis memory.
*   **Audit** vẫn ghi cho mỗi lần call (xem mục G) — truy vết được Producer spam click.

#### I. Per-User Rate Limit cho Revoke
*   Rate limit hiện tại chỉ IP-based (20/phút/IP). Producer dùng nhiều IP (VPN) có thể spam.
*   **Bổ sung per-user**: Tối đa **30 lần revoke / phút / user** cho `POST /revoke`. Track qua Redis `revoke:user_count:{userId}` TTL 60 giây.

---

### 1.3. Quy tắc Xác thực Dữ liệu
*   API thu hồi nhận tham số `distributionId` (UUID) trên URL Path để xác định bản phân phối cần xử lý.

---

### 1.4. Giới hạn Tần suất Truy cập API (Rate Limiting)

| API Endpoint | IP Limit | Per-User Limit | Mục đích |
| :--- | :--- | :--- | :--- |
| `POST /api/v1/demos/distributions/{id}/revoke` | **20 requests / phút / IP** | **30 requests / phút / user** | Tránh spam thu hồi liên tục (xem mục 1.2.I) |

Ngoài rate limit, áp dụng **idempotency** (xem mục 1.2.H): gọi lặp nhiều lần với cùng distributionId chỉ thực hiện action 1 lần, các lần sau trả 200 OK không side-effect.

---

## 🔄 2. User Flow & Sequence Diagram (Luồng người dùng & Sơ đồ tuần tự)

### 2.1. Luồng Thu hồi liên kết và Chặn truy cập (Revocation & Access Block Flow)

```mermaid
sequenceDiagram
    autonumber
    actor Producer
    participant FE as Producer Frontend App
    participant BE as Backend (Spring Boot)
    participant Redis as Redis Cache
    participant DB as PostgreSQL
    actor Listener as Khách hàng (Listener)
    participant ListFE as Listener Frontend App

    Producer->>FE: Bấm nút "Thu hồi" link của Listener_A
    FE->>BE: POST /api/v1/demos/distributions/{distId}/revoke
    
    BE->>BE: Xác thực Host sở hữu bản demo liên kết
    
    Note over BE, DB: Bắt đầu Transaction
    BE->>DB: Cập nhật demo_distributions -> is_revoked = true WHERE id = distId
    Note over BE, DB: Commit Transaction
    
    BE->>Redis: Xóa khóa 'demo:distribution:{shareToken_A}'
    BE->>Redis: Thêm khóa 'demo:distribution:revoked:{shareToken_A}' (TTL 10m)
    BE->>ListFE: Gửi sự kiện WebSocket {"event": "DISTRIBUTION_REVOKED", "data": {"shareToken": "shareToken_A"}}
    ListFE->>ListFE: Cập nhật UI mờ xám bản demo, hiện thông báo "Đã thu hồi"
    
    BE-->>FE: HTTP 200 OK (Thu hồi thành công)
    FE-->>Producer: Đổi trạng thái hiển thị thành "Đã thu hồi"
    
    Note over Listener, BE: --- Listener_A cố gắng nghe nhạc sau đó (Hoặc xin phân đoạn tiếp theo) ---
    Listener->>ListFE: Bấm Play hoặc tải lại trang nghe thử (Hoặc xin key phân đoạn tiếp)
    ListFE->>BE: GET /api/v1/stream/keys/{shareToken_A} (Kèm Secure Session Cookie)
    
    BE->>Redis: Kiểm tra blacklist: EXISTS 'demo:distribution:revoked:{shareToken_A}' -> Trả về true
    
    BE-->>ListFE: HTTP 403 Forbidden (LINK_REVOKED) (Cắt đứt phát nhạc ngay lập tức)
    ListFE->>ListFE: Ngắt phát nhạc, xóa buffer RAM
    ListFE-->>Listener: Hiển thị cảnh báo: "Liên kết đã hết hạn hoặc bị thu hồi"
```

##### 📝 Mô tả chi tiết các bước xử lý:
1.  **Thu hồi quyền**: Producer click thu hồi trên bảng điều khiển. Frontend gửi `POST /api/v1/demos/distributions/{distId}/revoke`.
2.  **Xác thực và Cập nhật**: Backend kiểm tra quyền sở hữu, cập nhật cột `is_revoked = true` trong DB. Đồng thời, Backend thực hiện xóa khóa cache phân phối tương ứng `demo:distribution:{shareToken}` khỏi Redis, ghi nhận khóa blacklist `demo:distribution:revoked:{shareToken}` (TTL 10 phút) lên Redis, và broadcast sự kiện qua WebSocket tới Topic chung của hội thoại để tự động làm mờ và cập nhật trạng thái đã thu hồi trên giao diện Listener trong thời gian thực.
3.  **Kiểm tra và ngắt luồng (Mid-stream Interruption)**: Khi Listener cố gắng truy xuất lấy Key giải mã, tải playlist HLS hoặc tải tệp gốc:
    *   Hệ thống kiểm tra nhanh sự tồn tại của khóa blacklist `demo:distribution:revoked:{shareToken}` trên Redis.
    *   Nếu tồn tại khóa này (hoặc nếu key gốc không còn tồn tại/DB check `is_revoked = true`), Backend lập tức trả về lỗi HTTP 403 Forbidden. Trình phát HLS tại client lập tức ngắt luồng, xóa sạch bộ nhớ đệm RAM và thông báo liên kết bị khóa, chặn đứng hoàn toàn việc dùng cookie cũ còn hạn để bypass.

---

## 💾 3. Database & Cache Schema (Thiết kế Cơ sở Dữ liệu & Cache)

### 3.1. Thiết kế Cache Redis thời gian thực (Bổ sung)

| Định dạng Khóa (Redis Key) | Kiểu dữ liệu | Giá trị (Value) | TTL | Mục đích sử dụng |
| :--- | :--- | :--- | :--- | :--- |
| `demo:distribution:revoked:{shareToken}` | `String` | `"true"` | **600 giây** (10 phút) | Lưu vết nhanh các token đã bị thu hồi để chặn spam gọi API lấy key liên tục xuống DB. |
| `revoked:completed:{distributionId}` | `String` | `"1"` | **86400 giây** (24 giờ) | Đánh dấu distribution đã revoke xong để xử lý idempotency (xem mục 1.2.H) — TTL dài để cover retry window của mobile/CDN proxy. |
| `revoke:user_count:{userId}` | `String` | Counter (INCR) | **60 giây** | Đếm số lần revoke của user. Vượt 30/phút → `RATE_LIMIT_EXCEEDED` (xem mục 1.2.I). |

---

## 🔌 4. API Specifications (Đặc tả API)

### 4.0. Cấu hình Chung
*   **Base Path**: `/api/v1`
*   **Headers**: `Authorization: Bearer <accessToken>`

---

### 4.1. API Thu hồi liên kết phân phối (Revoke Distribution)
*   **Method**: `POST`
*   **Path**: `/api/v1/demos/distributions/{distributionId}/revoke`
*   **Auth Level**: `Requires ROLE_USER_PRO`

#### Response Thành công (200 OK):
```json
{
  "success": true,
  "message": "Thu hồi quyền truy cập liên kết chia sẻ thành công",
  "data": null,
  "errors": null,
  "timestamp": "2026-07-01T16:40:00Z"
}
```

#### Response Thất bại do không phải chủ sở hữu (403 Forbidden):
```json
{
  "success": false,
  "message": "Tài khoản của bạn không có quyền thu hồi liên kết này",
  "data": null,
  "errors": [
    {
      "code": "FORBIDDEN_ACCESS",
      "field": null,
      "message": "Người dùng không phải là Producer sở hữu tệp nhạc"
    }
  ],
  "timestamp": "2026-07-01T16:40:00Z"
}
```

---

### 4.2. Phụ lục Mã Lỗi Nghiệp Vụ (Error Codes)

| Http Status | Error Code (String) | Mô tả | Trường liên quan (`field`) |
| :--- | :--- | :--- | :--- |
| `403 Forbidden` | `FORBIDDEN_ACCESS` | Người gọi API không phải là người sở hữu bản nhạc | `null` |
| `429 Too Many Requests` | `RATE_LIMIT_EXCEEDED` | Vượt `20/phút/IP` hoặc `30/phút/user` cho `/revoke` | `null` |

> **Lưu ý QUAN TRỌNG về 404 vs Idempotency** (refactor sau R4):
> - `DISTRIBUTION_NOT_FOUND` (404) đã bị **LOẠI BỎ khỏi endpoint `/revoke`** để giữ idempotency promise (xem mục 1.2.H).
> - **Quy tắc mới**: Khi `distributionId` không tồn tại (HOẶC đã từng tồn tại nhưng bị hard-delete), revoke endpoint trả `200 OK {success: true, message: "Thu hồi hoàn tất (idempotent)"}` với audit log ghi `reason='not-found-revoked'` (giả lập). Mục đích:
>   1. Attacker không thể phân biệt "distribution tồn tại" vs "không tồn tại" qua status code → chống enumeration.
>   2. Producer double-click / mobile retry không bị fail UX.
> - Nếu cần 404 cho audit/admin endpoint READ-ONLY khác (vd `GET /demos/distributions/{id}`), đó là endpoint khác — không liên quan đến `/revoke`.
> - Mã `DISTRIBUTION_ALREADY_REVOKED` (409) cũng đã được loại bỏ vì endpoint hiện **idempotent**.

### 4.3. Cấu hình liên quan (tham chiếu)

```yaml
pwb:
  audio:
    revoke:
      blacklist-ttl-seconds: 600       # 10 phút — TTL của demo:distribution:revoked:{shareToken}
      notify-listener-websocket: true  # Bật broadcast DISTRIBUTION_REVOKED (xem 6. Logging)
```

---

## 🎨 5. UI/UX & Frontend Integration Notes (Giao diện & Tích hợp)

### 5.1. Thiết kế Giao diện Thu hồi (Grayscale Theme & A11y)
*   **Modal xác nhận thu hồi (Confirmation Dialog)**:
    *   Khi Producer bấm "Thu hồi", hiển thị hộp thoại xác nhận đơn sắc tối giản. Nền trắng, viền đen.
    *   Thông điệp: *"Bạn có chắc chắn muốn thu hồi liên kết nghe thử này? Khách hàng sẽ không thể tiếp tục nghe trực tuyến hoặc tải xuống file nhạc."*
    *   Hai nút lựa chọn phẳng: `[ Xác nhận thu hồi ]` (nền đen chữ trắng) và `[ Hủy bỏ ]` (nền trắng chữ đen viền xám).
*   **Cập nhật danh sách**:
    *   Nút bấm "Thu hồi" trên dòng bản ghi tương ứng lập tức chuyển sang nhãn `[ Đã thu hồi ]` dạng Text tĩnh màu xám nhạt (`text-neutral-400`), vô hiệu hóa mọi click tương tác sau đó.
*   **Accessibility (A11y)**:
    *   Hộp thoại xác nhận khai báo `role="alertdialog"`, `aria-modal="true"`.

---

### 5.2. Tối ưu hóa Hiệu năng & Trải nghiệm Lập trình viên (Performance & DevEx)
*   **Cập nhật State ở Client**:
    *   Khi API thu hồi trả về thành công, Frontend cập nhật mảng danh sách phân phối trong Zustand Store cục bộ (cập nhật thuộc tính `isRevoked = true` cho dòng ghi nhận đó) để render lại UI tức thời mà không cần reload trang hay gọi lại API danh sách.

---

### 5.3. Sơ đồ Luồng Màn hình (Screen Flow)

```mermaid
graph TD
    classDef default fill:#f9f9f9,stroke:#333,stroke-width:1px,color:#000;
    classDef screen fill:#ffffff,stroke:#000,stroke-width:2px,font-weight:bold,color:#000;
    classDef action fill:#e1e1e1,stroke:#666,stroke-width:1px,stroke-dasharray: 5 5,color:#000;

    HistoryPage["Trang lịch sử phân phối của Demo <br> /dashboard/demos/{id}"]:::screen -->|Bấm Thu hồi| ConfirmDialog["Hộp thoại xác nhận thu hồi"]:::screen
    
    ConfirmDialog -->|Click xác nhận| CallRevokeAPI{Gọi API POST /revoke}:::action
    
    CallRevokeAPI -->|Trả 200 thành công| UpdateLocalState[Cập nhật State Zustand cục bộ thành Đã thu hồi]:::action
    
    UpdateLocalState --> HistoryPage
```

---

## 📊 6. Logging & Observability (Ghi nhận Nhật ký & Giám sát)

### 6.1. Log Points (Các điểm ghi log)

| Level | Sự kiện | Payload mẫu |
| :--- | :--- | :--- |
| `INFO` | Revoke request received | `{"event": "REVOKE_REQUEST", "distId": "f7b84f32-...", "userId": "c8b74f51-..."}` |
| `INFO` | Distribution revoked successfully | `{"event": "DISTRIBUTION_REVOKED", "distId": "f7b84f32-...", "token": "e5b84f32-..."}` |
| `INFO` | Idempotent revoke — already done | `{"event": "REVOKE_IDEMPOTENT_SKIP", "distId": "...", "userId": "..."}` |
| `INFO` | Revoke audit recorded | `{"event": "REVOKE_AUDIT_RECORDED", "auditId": "...", "distId": "...", "reason": "manual"}` |
| `INFO` | Cache evicted | `{"event": "DISTRIBUTION_CACHE_EVICTED", "shareToken": "e5b84f32-..."}` |
| `INFO` | AES key cache evicted (on revoke) | `{"event": "AES_KEY_CACHE_EVICTED", "demoId": "...", "trigger": "revoke"}` |
| `INFO` | AES rotation triggered (on revoke) | `{"event": "AES_ROTATION_TRIGGERED", "demoId": "...", "oldVersion": 2, "newVersion": 3}` |
| `INFO` | Cookie jti's blacklisted (on revoke) | `{"event": "JTI_BULK_BLACKLISTED", "shareToken": "...", "jtiCount": 5}` |
| `INFO` | Revoke blacklist key set | `{"event": "DISTRIBUTION_BLACKLIST_SET", "shareToken": "...", "ttlSeconds": 600}` |
| `INFO` | WebSocket broadcast sent | `{"event": "DISTRIBUTION_REVOKED_WS_SENT", "topic": "/topic/shared-threads/{threadId}/distributions", "shareToken": "..."}` |
| `WARN` | WebSocket SUBSCRIBE denied (authz) | `{"event": "WS_SUBSCRIBE_DENIED", "userId": "...", "threadId": "...", "reason": "not-thread-participant|missing-tat"}` |
| `WARN` | Unauthorized revoke attempt | `{"event": "UNAUTHORIZED_REVOKE_ATTEMPT", "distId": "f7b84f32-...", "userId": "e5b84f32-..."}` |
| `ERROR` | WS broadcast failed (vẫn rollback lỗi) | `{"event": "WS_BROADCAST_FAILED", "topic": "...", "error": "..."}` |

### 6.2. Quy tắc Bảo mật Log
*   Không ghi log email của khách hàng được liên kết với Token bị thu hồi trong log nghiệp vụ thô.
