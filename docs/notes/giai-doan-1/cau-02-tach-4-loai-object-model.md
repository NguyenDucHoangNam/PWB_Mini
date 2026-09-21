# Câu 2: Tách biệt 4 loại Object Model cho cùng một khái niệm User

### ❓ Câu hỏi:
> *"Trong Clean Architecture của bạn, tại sao cho cùng một đối tượng User lại phải sinh ra tới 4 loại Model khác nhau (`UserRequest/Response`, `LoginCommand/UserView`, `User`, `UserJpaEntity`)? Việc chuyển đổi (mapping) qua lại giữa 4 class này có gây lãng phí hiệu năng (Overhead) không? Cái giá phải trả và cái được là gì?"*

---

### 💡 Câu trả lời:

Trong dự án **PWB_MiNi**, việc tách một khái niệm `User` thành 4 class độc lập đại diện cho 4 tầng kiến trúc là một quyết định có chủ đích nhằm đảm bảo nguyên tắc **Phân tách trách nhiệm (Separation of Concerns)** và **Cô lập rủi ro thay đổi**.

---

#### 1. Vai trò và lý do tồn tại của 4 loại Model

| Loại Model | Tầng Kiến Trúc | Trách Nhiệm Cốt Lõi | Ví Dụ Đặc Thù |
| :--- | :--- | :--- | :--- |
| **`UserRequest` / `UserResponse`** | `api/dto` | **Hợp đồng giao tiếp bên ngoài (API Contract)**. Định dạng JSON giao tiếp với Frontend. Chứa các annotation validation của Web (`@NotBlank`, `@Email`), ẩn giấu hoàn toàn thông tin nhạy cảm. | `RegisterRequest` có kiểm tra `@Pattern` mật khẩu, `UserResponse` chỉ trả về `id`, `email`, `displayName`. |
| **`LoginCommand` / `UserView`** | `application/dto` | **Tham số điều phối nghiệp vụ (Use Case I/O)**. Là các DTO bất biến (Immutable Data / Java Record), không dính dáng đến HTTP hay JSON. Giúp Use Case có thể được gọi từ bất kỳ đâu (REST, Kafka, CLI). | `LoginCommand(email, password, clientIp, userAgent)` chứa đầy đủ ngữ cảnh để chạy Use Case đăng nhập. |
| **`User`** | `domain/model` | **Trái tim nghiệp vụ (Rich Domain Entity)**. Java thuần 100%, chứa toàn bộ quy tắc kinh doanh bất biến (Invariants). Constructor là `private`, không có setter tự do, chỉ thay đổi trạng thái qua các hàm nghiệp vụ. | `user.changePassword(...)`, `user.markEmailVerified()`. |
| **`UserJpaEntity`** | `infrastructure/entity` | **Ánh xạ vật lý xuống Database (Persistence Model)**. Chứa toàn bộ annotation của Hibernate/JPA (`@Entity`, `@Table`, `@Id`, `@Column`). Phục vụ ORM, Dirty Checking, Foreign Keys. | Ánh xạ trực tiếp tới bảng `iam_users` trong PostgreSQL. |

---

#### 2. Việc chuyển đổi (Mapping) qua lại có gây lãng phí hiệu năng (Overhead) không?

- **Về mặt CPU**:
  - Việc chuyển đổi giữa các đối tượng (qua MapStruct hoặc getter/setter thủ công) chỉ là các phép gán tham chiếu bộ nhớ (Reference Assignment) trong RAM. Quá trình này chỉ tốn **vài nano-giây (nanoseconds)**.
  - So sánh với độ trễ của mạng (Network Latency: 10–50ms) hoặc độ trễ truy vấn Database (Database I/O: 2–10ms), chi phí mapping của CPU là **hoàn toàn không đáng kể (negligible)**.
- **Về mặt Bộ nhớ (RAM & Garbage Collection)**:
  - Các đối tượng DTO và Command là các đối tượng có vòng đời cực ngắn (Short-lived objects). Chúng được cấp phát trong vùng nhớ `Eden Space` của JVM Young Generation và được Garbage Collector (G1GC / ZGC) thu hồi gần như tức thì mà không gây stop-the-world hay rò rỉ bộ nhớ.

---

#### 3. Cái giá phải trả (Cost) và Cái mua được (Benefits)

- **Cái giá phải trả**:
  1. **Tăng số lượng code (Boilerplate Code)**: Phải viết thêm class DTO, Entity và các interface Mapper.
  2. **Chi phí Onboarding**: Lập trình viên Junior mới vào dự án có thể thấy rườm rà và thắc mắc tại sao không dùng luôn JPA Entity từ Controller đến Database cho nhanh.

- **Cái mua được (Giá trị kỹ thuật sống còn)**:
  1. **Độc lập tuyệt đối giữa API và Database Schema**:
     - Khi DBA yêu cầu đổi tên cột `usr_pwd_hash` thành `password_digest` trong bảng `iam_users` (sửa `UserJpaEntity`), hợp đồng JSON của Frontend (`UserResponse`) **hoàn toàn không bị vỡ**.
     - Ngược lại, khi Frontend muốn đổi tên trường JSON `displayName` thành `fullName`, ta chỉ cần sửa DTO ở tầng API mà không phải chạy migration sửa database.
  2. **Bảo mật an toàn dữ liệu (Zero Data Leakage)**:
     - Nếu dùng chung JPA Entity cho Controller, chỉ cần lập trình viên quên gắn `@JsonIgnore` trên trường `passwordHash`, mật khẩu băm của người dùng sẽ bị phơi trần ra ngoài response JSON. Việc tách biệt DTO loại bỏ 100% nguy cơ này.
  3. **Chống tấn công Mass Assignment (Over-Posting)**:
     - Kẻ tấn công không thể tự ý gửi kèm trường `"role": "ADMIN"` trong JSON để tự nâng cấp quyền của mình, vì `RegisterRequest` chỉ định nghĩa đúng các trường cho phép nhập liệu.
