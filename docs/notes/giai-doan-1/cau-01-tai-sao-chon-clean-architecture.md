# Câu 1: Tại sao chọn Clean Architecture thay vì mô hình 3 lớp truyền thống?

### ❓ Câu hỏi:
> *"Tại sao dự án của bạn lại chọn Clean Architecture / Hexagonal Architecture (Ports & Adapters) thay vì mô hình 3 lớp truyền thống (Controller - Service - Repository)? Mô hình này giúp giải quyết bài toán gì khi hệ thống mở rộng?"*

---

### 💡 Câu trả lời:

Trong dự án **PWB_MiNi**, chúng em quyết định áp dụng **Clean Architecture (Hexagonal Architecture / Ports & Adapters)** thay vì mô hình 3 lớp truyền thống vì 3 lý do kỹ thuật cốt lõi:

#### 1. Khắc phục nhược điểm "Database-Driven" của mô hình 3 lớp
- Ở mô hình 3 lớp thông thường, tầng `Service` phụ thuộc trực tiếp vào `Repository`, và `Repository` lại gắn chặt với `JPA Entity` (Database Schema). Cách tiếp cận này khiến nghiệp vụ bị dẫn dắt bởi Cơ sở dữ liệu: mỗi khi sửa một cột trong database, logic tầng Service và API rất dễ bị vỡ theo.
- Clean Architecture áp dụng nguyên lý **Đảo ngược phụ thuộc (Dependency Inversion)**: Tầng `Domain` nằm ở trung tâm và là **Java thuần 100% (POJO)**, hoàn toàn không dính bất kỳ annotation nào của Spring hay Hibernate (`@Entity`, `@Table`, `@Id`). Toàn bộ quy tắc kinh doanh được bảo vệ độc lập, không bị ảnh hưởng bởi công nghệ lưu trữ.

#### 2. Kiến trúc Ports & Adapters giúp mở rộng và thay đổi công nghệ linh hoạt
- **Ports (Cổng kết nối)**: Là các Interface do tầng Domain/Application định nghĩa (ví dụ: `UserRepository`, `S3StoragePort`) để tuyên bố nghiệp vụ cần gì mà không quan tâm ai thực hiện.
- **Adapters (Bộ điều hợp)**: Là các class thực thi nằm ở tầng Infrastructure mép ngoài (ví dụ: `UserRepositoryImpl` dùng Spring Data JPA, `S3StorageServiceImpl` dùng AWS SDK).
- **Bài toán mở rộng thực tế**: 
  - Nếu sau này hệ thống muốn đổi từ **PostgreSQL** sang **MongoDB** hoặc **Redis**, chúng em **chỉ cần viết một Adapter mới** ở tầng Infrastructure. Toàn bộ tầng lõi nghiệp vụ (`User.java`, `LoginUseCaseImpl.java`) **không phải sửa hay test lại dù chỉ một dòng code**.
  - Việc kiểm thử (Unit Test) cho Domain và UseCase diễn ra siêu tốc (vài mili-giây) vì là Java thuần, không cần phải mất 30–40 giây để khởi động Spring Context hay cấu hình Database giả lập.

#### 3. Hỗ trợ kiến trúc "Đa kênh giao tiếp" (Multi-Channel Inbound)
- PWB_MiNi không chỉ nhận request từ **REST Controller**, mà còn nhận sự kiện xử lý âm thanh ngầm từ **Kafka Consumer**, nhận tương tác phòng nghe từ **STOMP WebSocket**, và các tác vụ định kỳ từ **Scheduler**.
- Trong Clean Architecture, các kênh giao tiếp này chỉ đóng vai trò là các Inbound Adapter mép ngoài, cùng gọi vào chung một Use Case ở tầng Application (ví dụ: `ProcessSongUseCase`). Nhờ đó, logic xử lý bài hát được tập trung tại một nơi duy nhất, không bị phụ thuộc vào tầng Web Servlet (`HttpServletRequest`) và hoàn toàn không bị trùng lặp mã nguồn.

#### 4. Đánh đổi thực tế (Trade-offs)
- **Cái giá phải trả**: Tốn công viết nhiều boilerplate code hơn (tạo interface Port, class Adapter) và phải tách biệt 4 loại Object (`UserRequest`, `LoginCommand`, `User`, `UserJpaEntity`) kèm code Mapper chuyển đổi qua lại.
- **Đánh giá lựa chọn**: Sự đánh đổi này hoàn toàn xứng đáng với một hệ thống có **nghiệp vụ phức tạp và đa luồng tương tác (REST + Kafka + WebSocket)** như PWB_MiNi. Với các dự án CRUD đơn giản hoặc làm nhanh MVP thì mô hình 3 lớp truyền thống vẫn là lựa chọn thực dụng hơn.
