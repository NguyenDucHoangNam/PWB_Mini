# Câu 6: Flyway Migration vs Hibernate ddl-auto: update & Xử lý xung đột Merge Migration (out-of-order)

### ❓ Câu hỏi:
> *"Tại sao trong môi trường Production bạn lại dùng Flyway Migration thay vì để Hibernate tự động sinh và cập nhật bảng (`ddl-auto: update`)? Điều gì sẽ xảy ra nếu 2 lập trình viên cùng merge 2 file migration khác nhau vào cùng một thời điểm (`out-of-order: true`)?"*

---

### 💡 Câu trả lời:

#### 1. Tại sao TUYỆT ĐỐI KHÔNG dùng Hibernate `ddl-auto: update` trên Production?

Trong môi trường học tập hoặc phát triển cá nhân (Local Dev), Hibernate `ddl-auto: update` rất tiện vì chỉ cần sửa JPA Entity là cấu trúc bảng tự động thay đổi. Tuy nhiên, đưa cấu hình này lên môi trường Production là một **hành vi tự sát kỹ thuật (Technical Suicide)** vì 5 nguyên nhân chí mạng sau:

1. **Không thể Đổi tên (Rename) hoặc Xóa (Drop) cột — Sinh ra dữ liệu mồ côi (Orphaned Data)**:
   - Khi bạn đổi tên thuộc tính trong Java Entity từ `fullName` thành `name`, Hibernate **không bao giờ** phát sinh lệnh `ALTER TABLE ... RENAME COLUMN`.
   - Thay vào đó, Hibernate âm thầm tạo thêm một cột mới `name` và bỏ rơi cột `fullName` cũ. Hậu quả là dữ liệu cũ nằm chết ở cột cũ, dữ liệu mới ghi vào cột mới, gây phân mảnh và sai lệch dữ liệu trầm trọng mà không có cảnh báo nào.
2. **Bất lực trước bài toán Data Migration (Chuyển đổi dữ liệu cũ)**:
   - Thay đổi cấu trúc phần mềm thực tế luôn đi kèm giữa **thay đổi cấu trúc bảng (DDL)** và **chuyển đổi dữ liệu hiện có (DML)**.
   - *Ví dụ thực tế*: Bạn cần tách cột `full_name` thành `first_name` và `last_name`, mã hóa lại mật khẩu cũ của 1 triệu user sang thuật toán mới, hoặc gán giá trị mặc định cho cột mới dựa trên tính toán từ 2 cột cũ. Hibernate hoàn toàn bất lực trước yêu cầu này, trong khi Flyway cho phép viết song song cả lệnh `ALTER TABLE` lẫn `UPDATE / INSERT` trong cùng một migration script.
3. **Mất hoàn toàn quyền kiểm soát tính năng nâng cao của Database (Native Features)**:
   - Hibernate chỉ sinh ra câu lệnh DDL tiêu chuẩn ở mức tối thiểu. Nó không thể tự sinh các tính năng tối ưu chuyên sâu của PostgreSQL như:
     - **Partial Index**: `CREATE INDEX idx_active_users ON users(email) WHERE is_deleted = false;`
     - **Full-Text / Trigram Index**: Index `pg_trgm` (GIN / GiST) để tìm kiếm bài hát hoặc phòng live siêu tốc.
     - **Tối ưu ràng buộc**: `CHECK (balance >= 0)`, Foreign Key options (`ON DELETE RESTRICT`), hoặc Table Partitioning cho bảng log/lịch sử hàng chục triệu dòng.
4. **Nguy cơ chiếm Exclusive Lock gây sập Production (Downtime)**:
   - Khi ứng dụng khởi động trên Database Production có hàng chục triệu bản ghi, việc Hibernate tự động chạy `ALTER TABLE ADD COLUMN ... DEFAULT ...` hoặc tạo Index thiếu kiểm soát sẽ kích hoạt **Exclusive Table Lock**.
   - Toàn bộ các câu lệnh `SELECT`, `INSERT`, `UPDATE` từ người dùng thực tế sẽ bị nghẽn trong hàng đợi kết nối (Connection Pool starvation), dẫn đến Gateway Timeout (HTTP 504) và sập hệ sinh thái.
   - Với Flyway, DBA / Tech Lead có thể chủ động kiểm tra execution plan, dùng `CREATE INDEX CONCURRENTLY` (PostgreSQL) hoặc chia nhỏ batch chạy vào ban đêm mà không khóa bảng.
5. **Rủi ro cấu hình nhầm làm mất sạch dữ liệu (Disaster Risk)**:
   - Chỉ cần một sai sót nhỏ trong tệp biến môi trường hoặc deploy nhầm profile khiến `ddl-auto` chuyển thành `create` hoặc `create-drop`, Hibernate sẽ tự động xóa sạch toàn bộ các bảng trong CSDL Production khi khởi động.

---

#### 2. Lợi ích cốt lõi của Flyway Migration (Triết lý Database-as-Code)

Flyway đưa việc quản trị CSDL về cùng tiêu chuẩn với mã nguồn phần mềm:

- **Tính tất định & Đồng nhất (Determinism)**: Tất cả các môi trường (Dev, CI, Staging, Production) đều trải qua chính xác các bước biến đổi CSDL giống nhau theo thứ tự thời gian.
- **Lịch sử minh bạch (Audit Trail)**: Flyway tự động duy trì bảng hệ thống `flyway_schema_history` lưu rõ: phiên bản nào đã chạy (`version`), ai chạy (`installed_by`), thời điểm nào (`installed_on`), tốn bao nhiêu mili-giây (`execution_time`).
- **Bảo vệ toàn vẹn bằng Checksum (SHA-256)**: Nếu ai đó bí mật sửa đổi nội dung một file migration cũ đã từng được áp dụng trên Production, Flyway sẽ phát hiện mã băm Checksum không khớp và từ chối khởi động app ngay lập tức, ngăn chặn nguy cơ sai lệch trạng thái CSDL.
- **Tích hợp liền mạch vào CI/CD**: Quá trình migration diễn ra tự động ngay khi ứng dụng khởi động (thông qua Spring Boot Autoconfiguration) hoặc thông qua Docker / Kubernetes Init Container trước khi Pod chính tiếp nhận lưu lượng mạng.

---

#### 3. Xung đột khi 2 lập trình viên cùng merge file migration & Cơ chế `out-of-order: true`

##### A. Bối cảnh thực tế trong Modular Monolith:
Trong kiến trúc Modular Monolith của dự án `PWB_MiNi`, các module được phân bổ dải số phiên bản (Version Ranges) độc lập để các team không tranh chấp số thứ tự:
- Module `iam`: Dải version từ `V1__...` đến `V99__...`
- Module `music`: Dải version từ `V100__...` đến `V199__...`
- Module `stream`: Dải version từ `V200__...` đến `V299__...`
- Module `live-room`: Dải version từ `V300__...` đến `V399__...`

Giả sử kịch bản sau diễn ra:
1. **Lập trình viên B (Module Live Room)** hoàn thành tính năng và merge PR trước. Tệp migration là `V301__create_live_rooms.sql`.
   - Hệ thống deploy lên môi trường chung, Flyway thực thi `V301`.
   - Phiên bản cao nhất được ghi nhận trong bảng `flyway_schema_history` lúc này là **301**.
2. **Lập trình viên A (Module IAM)** hoàn thành tính năng phân quyền sau đó vài phút và merge PR. Tệp migration là `V15__add_user_roles.sql`.

---

##### B. Điều gì xảy ra nếu dùng cấu hình mặc định (`spring.flyway.out-of-order = false`)?
- **Hiện tượng**: Flyway khởi động, quét codebase và thấy tệp `V15`. Nó so sánh số phiên bản `15` với phiên bản cao nhất hiện có trong CSDL (`301`). Vì `15 < 301`, Flyway xem đây là một file "lỗi thời bị bỏ quên".
- **Hậu quả**: Flyway ném ngoại lệ `FlywayException` và làm **sập ứng dụng ngay khi khởi động**:
  ```text
  org.flywaydb.core.api.FlywayException: Validate failed: 
  Detected resolved migration not applied to database: 15. 
  To solve this issue, set outOfOrder to true.
  ```
- **Hệ lụy vận hành**: Toàn bộ quy trình CI/CD bị chặn đứng. Lập trình viên A buộc phải đổi tên file từ `V15` thành `V302` (phá vỡ quy ước dải số module của IAM) hoặc phải rollback code của B.

---

##### C. Giải pháp với cấu hình `spring.flyway.out-of-order = true`
Khi cấu hình trong `application.yml`:
```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    out-of-order: true
```

- **Cơ chế hoạt động**:
  1. Flyway không quan tâm việc số phiên bản mới có nhỏ hơn số phiên bản cao nhất đã chạy hay không.
  2. Thay vào đó, nó quét toàn bộ danh sách file migration và đối chiếu với bảng `flyway_schema_history`:
     - File nào đã chạy (`V301`) -> Bỏ qua.
     - File nào **chưa có bản ghi** trong bảng lịch sử (`V15`) -> **Flyway lập tức nạp và thực thi**.
  3. Sau khi thực thi thành công, Flyway ghi `V15` vào bảng `flyway_schema_history` với số thứ tự cài đặt (`installed_rank`) kế tiếp (ví dụ: rank = 5, dù rank = 4 là của `V301`).

```
[Bảng flyway_schema_history khi bật out-of-order: true]
┌────────────────┬─────────┬───────────────────────────────┬─────────────────────────┐
│ installed_rank │ version │ description                   │ installed_on            │
├────────────────┼─────────┼───────────────────────────────┼─────────────────────────┤
│ 1              │ 1       │ init_users_table              │ 2026-09-01 10:00:00     │
│ 2              │ 2       │ create_refresh_tokens_table   │ 2026-09-02 11:30:00     │
│ 3              │ 300     │ init_live_rooms_table         │ 2026-09-05 09:15:00     │
│ 4              │ 301     │ create_live_rooms             │ 2026-09-10 12:00:00     │
│ 5              │ 15      │ add_user_roles (Chạy sau)     │ 2026-09-10 12:15:00     │  <-- Hợp lệ nhờ out-of-order!
└────────────────┴─────────┴───────────────────────────────┴─────────────────────────┘
```

---

##### D. ⚠️ Lưu ý sống còn của Senior / Lead khi bật `out-of-order: true`:
1. **Tuyệt đối không để xảy ra Cross-Module Data Dependency sai trật tự**:
   - `out-of-order: true` chỉ an toàn khi các migration của các module **độc lập hoàn toàn về mặt logic bảng**.
   - Nếu `V301` của Live Room cần Foreign Key trỏ tới cột `role_id` vừa mới được định nghĩa trong `V15` của IAM, việc chạy `V301` trước sẽ gây lỗi `Foreign key constraint fails`.
   - Do đó, trong Modular Monolith, các module luôn tuân thủ nguyên tắc **Loose Coupling** (không tạo Foreign Key cứng xuyên module ở tầng DB) và các tệp migration phải độc lập với nhau.
2. **Quy tắc Bất Biến (Immutability)**:
   - Dù bật `out-of-order`, các file migration đã từng chạy trên bất kỳ môi trường dùng chung nào **tuyệt đối không bao giờ được sửa nội dung**. Mọi thay đổi bắt buộc phải tạo file migration mới để tiến lên phía trước.
