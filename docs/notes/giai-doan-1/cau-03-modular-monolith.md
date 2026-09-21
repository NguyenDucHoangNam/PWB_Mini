# Câu 3: Modular Monolith là gì? So sánh với Monolith truyền thống & Microservices? Vì sao dự án chọn Modular Monolith?

### ❓ Câu hỏi:
> *"Modular Monolith là gì? Nó khác gì so với một monolith truyền thống, và khác gì so với microservices? Vì sao dự án PWB_MiNi lại chọn Modular Monolith?"*

---

### 💡 Câu trả lời:

#### 1. Modular Monolith là gì?
**Modular Monolith** là một phong cách kiến trúc phần mềm trong đó toàn bộ hệ thống được triển khai và vận hành như **một tiến trình duy nhất (Single Deployment Unit / 1 tiến trình JVM)**, nhưng mã nguồn bên trong được phân chia nghiêm ngặt thành các **Module nghiệp vụ độc lập (Bounded Contexts)** có ranh giới rõ ràng.

Trong **PWB_MiNi**, hệ thống gồm 3 module nghiệp vụ cốt lõi: `iam`, `audio`, và `liveroom`. Mỗi module tự quản lý trọn vẹn 4 tầng Clean Architecture (`domain`, `application`, `infrastructure`, `api`), tự quản lý dải migration database riêng và tự đăng ký qua `@AutoConfiguration`. Ranh giới phụ thuộc một chiều được cưỡng chế ở cấp độ biên dịch Maven (`pom.xml`): `liveroom` phụ thuộc `audio` và `iam`, nhưng `audio` và `iam` hoàn toàn độc lập với nhau.

---

#### 2. Bảng so sánh: Monolith truyền thống vs Modular Monolith vs Microservices

| Tiêu chí | Monolith Truyền Thống | Modular Monolith (PWB_MiNi) | Microservices |
| :--- | :--- | :--- | :--- |
| **Cấu trúc mã nguồn** | **Gom theo tầng kỹ thuật**: Tất cả Controller ở 1 package, Service ở 1 package. Dễ biến thành "Big Ball of Mud" (Bãi bùn), gọi chéo hỗn loạn. | **Gom theo nghiệp vụ (Domain)**: Từng module độc lập (`iam`, `audio`, `liveroom`). Module này không được tự tiện chọc vào database hay nội bộ của module khác. | **Độc lập hoàn toàn**: Mỗi service là một Repository riêng, độc lập công nghệ và mã nguồn. |
| **Đơn vị triển khai** | 1 tiến trình (1 file `.jar` / `.war`). | **1 tiến trình duy nhất** (1 file `.jar` chạy trên 1 JVM). | Nhiều tiến trình độc lập (N containers chạy trên cụm Kubernetes). |
| **Giao tiếp liên module** | Gọi hàm Java trực tiếp, query chéo bảng tự do. | **Gọi qua Java Interface hoặc In-process Events**. Tốc độ nano-giây trong RAM, **0ms độ trễ mạng**. | **Gọi qua mạng (Network I/O)**: REST API, gRPC, Kafka. Tốn độ trễ serialize JSON và rủi ro đứt mạng. |
| **Tính nhất quán dữ liệu** | Chung 1 Database, khóa ngoại (Foreign Keys) nối chằng chịt giữa các bảng. | Chung 1 Database vật lý nhưng **bảng dữ liệu tách biệt theo prefix** (`iam_*`, `audio_*`, `liveroom_*`), **cấm dùng Foreign Key chéo module**. | **Database per Service**: Mỗi service có DB riêng. Bắt buộc dùng Saga Pattern / Eventual Consistency phức tạp. |
| **Chi phí vận hành (Ops)** | Rất thấp (1 server, 1 database). | **Rất thấp**: 1 VPS nhỏ là đủ vận hành toàn bộ hệ thống. | **Cực kỳ tốn kém và phức tạp**: Cần K8s, API Gateway, Service Mesh, Distributed Tracing, đội ngũ DevOps lớn. |

---

#### 3. Vì sao PWB_MiNi lại chọn Modular Monolith?

Quyết định lựa chọn Modular Monolith xuất phát từ 4 lý do thực chiến:

1. **Ràng buộc phần cứng thực tế (Hardware Constraints)**:
   - Hệ thống được triển khai trên một máy chủ VPS có cấu hình **4 vCPU và 7.6GB RAM**.
   - Nếu xẻ nhỏ hệ thống thành 4–5 Microservices, chi phí bộ nhớ tĩnh (JVM Memory Overhead) của từng Spring Boot instance (mỗi tiến trình ngốn 500MB–1GB RAM chỉ để khởi động framework) sẽ làm cạn kiệt RAM và kích hoạt **Linux OOM Killer** đánh sập máy chủ. Modular Monolith gom tất cả vào 1 JVM giúp tận dụng tối đa 7.6GB RAM cho bộ nhớ đệm và kết nối.
2. **Quy mô đội ngũ phát triển (Team Size & Simplicity)**:
   - Microservices sinh ra để giải quyết bài toán **tổ chức con người** khi công ty có hàng trăm lập trình viên cần deploy độc lập mà không dẫm chân nhau.
   - Với quy mô nhóm nhỏ, Modular Monolith giúp chúng em tập trung 100% vào logic sản phẩm, loại bỏ toàn bộ gánh nặng bảo trì hạ tầng phân tán (không cần lo lắng Circuit Breaker, Service Discovery, Distributed Transactions).
3. **Hiệu năng thời gian thực và Không độ trễ mạng (Zero Network Latency)**:
   - PWB_MiNi có các tính năng xử lý nhạc và phòng live STOMP đòi hỏi tốc độ phản hồi tính bằng mili-giây. Khi module `liveroom` cần kiểm tra thông tin bài hát từ module `audio`, lệnh gọi diễn ra trực tiếp trong bộ nhớ RAM (In-Memory Java Call), nhanh gấp hàng trăm lần so với việc gửi request HTTP qua mạng trong Microservices.
4. **Là bàn đạp hoàn hảo để tiến lên Microservices (Stepping Stone)**:
   - Vì các module đã được thiết kế ranh giới rành mạch, database không có Foreign Key chéo và giao tiếp qua abstraction, nên nếu tương lai sản phẩm phát triển với hàng triệu người dùng, việc tách module `audio` (xử lý render nhạc nặng) thành một Microservice độc lập chỉ mất **vài ngày tái cấu trúc**, hoàn toàn không phải đập đi xây lại từ đầu.
