# Câu 4: Phân tích cấu trúc 4 tầng (Domain, Application, Infrastructure, API)

### ❓ Câu hỏi:
> *"Hãy phân tích cấu trúc 4 tầng (Domain, Application, Infrastructure, API) trong kiến trúc của PWB_MiNi? Trách nhiệm cốt lõi của từng tầng là gì và quy tắc phụ thuộc (Dependency Rule) giữa các tầng được kiểm soát như thế nào?"*

---

### 💡 Câu trả lời:

Trong mỗi module của **PWB_MiNi** (`iam`, `audio`, `liveroom`), mã nguồn được tổ chức nghiêm ngặt thành **4 tầng kiến trúc phân lớp** theo nguyên lý Clean Architecture:

```
[api] (Web REST, STOMP WebSocket)
  │
  ▼
[application] (Use Cases, Commands, DTOs)
  │
  ▼
[domain] (Entities, Value Objects, Port Interfaces)  ◄── Tâm điểm (100% Pure Java)
  ▲
  │ (Implements Ports)
[infrastructure] (JPA Entities, Adapters, S3, Kafka, Redis)
```

---

#### 1. Trách nhiệm cốt lõi của từng tầng

| Tầng | Thành Phần Chứa Đựng | Trách Nhiệm Cốt Lõi | Ràng Buộc Công Nghệ |
| :--- | :--- | :--- | :--- |
| **`domain`** | • Entities (`User`, `Song`, `LiveRoom`)<br>• Value Objects (`EmailAddress`, `Password`)<br>• Port Interfaces (`UserRepository`)<br>• Business Exceptions | **Trái tim nghiệp vụ của hệ thống**. Nắm giữ toàn bộ quy tắc kinh doanh bất biến (Invariants) và định nghĩa các Port Interface để tuyên bố nhu cầu kết nối ra bên ngoài. | **Java thuần 100% (POJO)**. Tuyệt đối KHÔNG chứa bất kỳ thư viện Spring, Hibernate hay cơ sở dữ liệu nào (`@Entity`, `@Service`, `@Transactional`). |
| **`application`** | • Use Case Interfaces & Impls (`LoginUseCaseImpl`)<br>• Commands (`LoginCommand`)<br>• Output Views (`UserView`) | **Điều phối luồng nghiệp vụ (Orchestrator)**. Nhận Command từ bên ngoài, gọi Port để lấy Entity từ DB, kích hoạt phương thức nghiệp vụ của Entity, gọi Port lưu lại, và phát sự kiện Outbox. | Không dính líu đến tầng Web (không dùng `HttpServletRequest`, không trả về `ResponseEntity`), giúp Use Case có thể chạy được từ cả REST, Kafka hay CLI. |
| **`infrastructure`** | • JPA Entities (`UserJpaEntity`)<br>• Adapters (`UserRepositoryImpl`)<br>• S3 Storage Service, Kafka Producer/Consumer, Redis Cache | **Hiện thực hóa công nghệ (Outbound Adapters)**. Cài đặt các Port do Domain/Application đặt ra. Chịu trách nhiệm trực tiếp nói chuyện với cơ sở dữ liệu, hàng đợi thông điệp và dịch vụ đám mây. | Tự do sử dụng các thư viện công nghệ nặng: Spring Data JPA, AWS SDK, Kafka Client, RedisTemplate. |
| **`api`** | • REST Controllers (`AuthController`)<br>• WebSocket Controllers (`MusicStompController`)<br>• Request/Response DTOs (`RegisterRequest`) | **Cửa ngõ tiếp nhận tương tác (Inbound Adapters)**. Nhận request từ Client, kiểm tra định dạng (`@Valid`), chuyển đổi sang Command để gọi Use Case, và chuẩn hóa dữ liệu trả về `ApiResponse`. | Chỉ làm nhiệm vụ điều hướng và xác thực dữ liệu đầu vào, tuyệt đối không chứa logic nghiệp vụ tính toán. |

---

#### 2. Quy tắc phụ thuộc (The Dependency Rule) & Cơ chế bảo vệ ranh giới

- **Quy tắc bất di bất dịch**: Mã nguồn từ tầng ngoài chỉ được phép phụ thuộc vào tầng trong, tầng trong **tuyệt đối không biết gì về tầng ngoài**:
  - `domain`: Độc lập tuyệt đối, không import bất kỳ class nào từ `application`, `infrastructure`, hay `api`.
  - `application`: Chỉ phụ thuộc vào `domain`.
  - `infrastructure`: Phụ thuộc vào `application` và `domain` để implements các Port.
  - `api`: Phụ thuộc vào `application` và `domain` để kích hoạt các Use Case.
  - `api` và `infrastructure` **hoàn toàn không được import lẫn nhau**.
- **Cơ chế đảo ngược phụ thuộc (DIP) giữa Domain và Infrastructure**:
  - Tầng `application` cần lưu User, nhưng nó không gọi trực tiếp xuống Database. Thay vào đó, `domain` tạo ra interface `UserRepository` (Port). Tầng `infrastructure` viết class `UserRepositoryImpl` (Adapter) để implements interface đó.
  - Tại thời điểm biên dịch (Compile-time), mã nguồn trỏ từ ngoài vào trong. Nhưng tại thời điểm chạy (Runtime), luồng điều khiển chảy mượt mà từ Application xuống Database nhờ cơ chế Dependency Injection của Spring Boot.

---

#### 3. Lợi ích kiến trúc mang lại cho PWB_MiNi

1. **Kiểm thử siêu tốc (Blazing Fast Testing)**: Vì tầng `domain` và `application` là Java thuần, chúng em có thể viết hàng trăm test case Unit Test cho logic đăng nhập, xử lý bài hát chỉ với JUnit 5 và Mockito, chạy xong trong **vài giây** mà không cần bật Spring ApplicationContext hay dựng Database giả lập.
2. **Khả năng thay thế công nghệ (Pluggable Infrastructure)**: Khi cần nâng cấp từ lưu trữ file cục bộ lên AWS S3, hoặc từ PostgreSQL sang MongoDB, lập trình viên chỉ cần thay thế hoặc viết thêm class ở tầng `infrastructure`, toàn bộ tầng `domain` và `application` giữ nguyên vẹn 100%.
