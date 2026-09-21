# Câu 5: Mô hình Ports & Adapters (Hexagonal Architecture) hoạt động như thế nào?

### ❓ Câu hỏi:
> *"Mô hình Ports & Adapters (Kiến trúc Lục giác - Hexagonal Architecture) hoạt động như thế nào? Hãy phân biệt Inbound Port/Adapter và Outbound Port/Adapter qua ví dụ thực tế trong dự án PWB_MiNi?"*

---

### 💡 Câu trả lời:

#### 1. Bản chất cốt lõi: Ẩn dụ "Cổng cắm và Củ sạc" (Port & Adapter)
- **Port (Cổng kết nối)**: Hãy tưởng tượng chiếc laptop của bạn có một cổng cắm **USB Type-C**. Đây là một **tiêu chuẩn giao tiếp cố định (Specification / Interface)**. Laptop tuyên bố: *"Tôi chỉ cần nhận dữ liệu đúng chuẩn USB-C, tôi không quan tâm bên ngoài là thiết bị gì"*.
- **Adapter (Củ sạc / Bộ điều hợp)**: Chuột máy tính, bàn phím rời, tai nghe, hay dây sạc đều là các **Adapter**. Chúng chuyển đổi tín hiệu đặc thù của từng thiết bị thành tín hiệu USB-C để cắm vào cổng của laptop. Khi bạn đổi từ chuột có dây sang chuột không dây, bo mạch laptop **hoàn toàn không phải thay đổi**.
- **Trong phần mềm**:
  - **Port** là các **Java Interface** do tầng bên trong (Domain hoặc Application) sở hữu.
  - **Adapter** là các **Java Class cụ thể** nằm ở tầng bên ngoài (API hoặc Infrastructure), chịu trách nhiệm chuyển đổi dữ liệu và nói chuyện với các công nghệ cụ thể (HTTP, PostgreSQL, S3, Kafka).

---

#### 2. Phân biệt Inbound (Driving) vs Outbound (Driven) trong PWB_MiNi

Mô hình Ports & Adapters chia thế giới xung quanh ứng dụng thành 2 chiều giao tiếp rõ rệt:

```
                  ┌────────────────────────────────────────────────────────┐
                  │                    ỨNG DỤNG LÕI                        │
  [Client ngoài]  │                                                        │  [Hạ tầng ngoài]
┌────────────────┐│ ┌───────────────────┐            ┌───────────────────┐ │┌────────────────┐
│ Trình duyệt    ││ │ INBOUND PORT      │            │ OUTBOUND PORT     │ ││ Database       │
│ Mobile App     ├┼─┼>(LoginUseCase)    │  (Domain)  │ (UserRepository)  ├─┼┼─> (PostgreSQL) │
│ Postman        ││ │                   │            │                   │ ││                │
│                ││ └─────────▲─────────┘            └─────────▲─────────┘ │└────────────────┘
└───────┬────────┘│           │                                │           │        ▲
        │         │           │                                │           │        │
        ▼         │           │                                │           │        │
┌────────────────┐│           │                                └───────────┼────────┘
│INBOUND ADAPTER ││           │                                            │
│(AuthController)├┼───────────┘                      OUTBOUND ADAPTER      │
│                ││                                (UserRepositoryImpl)    │
└────────────────┘│                                                        │
                  └────────────────────────────────────────────────────────┘
```

##### A. Chiều Inbound (Đầu vào / Driving — Thế giới bên ngoài gọi vào ứng dụng)
- **Inbound Port (Use Case Interface)**:
  - Nằm ở: Tầng `application`.
  - Định nghĩa các hành động nghiệp vụ mà thế giới bên ngoài có thể yêu cầu hệ thống thực hiện.
  - *Ví dụ PWB_MiNi*: Interface `LoginUseCase.java` với phương thức `LoginResult execute(LoginCommand command)`.
- **Inbound Adapter (Controller / Listener)**:
  - Nằm ở: Tầng `api` hoặc mép ngoài `infrastructure`.
  - Nhận tín hiệu từ giao thức mạng bên ngoài, kiểm tra tính hợp lệ và gọi vào Inbound Port tương ứng.
  - *Ví dụ PWB_MiNi*: 
    - `AuthController.java`: Nhận request HTTP POST `/api/v1/auth/login`, đọc body JSON, validate `@Valid`, đóng gói thành `LoginCommand` và gọi `loginUseCase.execute(command)`.
    - `MusicStompController.java`: Nhận frame WebSocket STOMP tua nhạc `/app/liveroom/{roomId}/seek`, giải mã gói tin và gọi `controlPlaybackUseCase.seek(...)`.

##### B. Chiều Outbound (Đầu ra / Driven — Ứng dụng gọi ra thế giới bên ngoài)
- **Outbound Port (Repository / Service Interface)**:
  - Nằm ở: Tầng `domain` (nếu là nghiệp vụ dữ liệu lõi) hoặc `application`.
  - Do Domain tự định nghĩa để tuyên bố: *"Tôi cần lấy dữ liệu hoặc lưu dữ liệu, tôi không quan tâm dữ liệu nằm ở đâu"*.
  - *Ví dụ PWB_MiNi*: Interface `UserRepository.java` trong `domain/repository/` với phương thức `Optional<User> findByEmail(EmailAddress email)`.
- **Outbound Adapter (Persistence Adapter / Gateway)**:
  - Nằm ở: Tầng `infrastructure`.
  - Cài đặt (implements) Outbound Port bằng công nghệ cụ thể để giao tiếp với Database, Cloud Storage hoặc Message Broker.
  - *Ví dụ PWB_MiNi*:
    - `UserRepositoryImpl.java` (trong `infrastructure/persistence/adapter`): Implements `UserRepository`, bên trong dùng Spring Data JPA (`UserJpaRepository`) để truy vấn PostgreSQL và chuyển đổi `UserJpaEntity` sang `User`.
    - `S3StorageServiceImpl.java` (trong `infrastructure/storage/s3`): Implements `StoragePort`, bên trong gọi AWS SDK S3 để cấp Pre-signed URL.

---

#### 3. Giá trị thực tiễn lớn nhất của Ports & Adapters khi phỏng vấn

1. **Ứng dụng trở thành "Hộp đen có thể cắm-rút" (Pluggable Application)**:
   - Nghiệp vụ đăng nhập (`LoginUseCase`) hoàn toàn không biết nó đang được gọi từ HTTP REST hay từ dòng lệnh CLI hay từ Kafka Message. Ta có thể thay đổi cách người dùng tương tác mà không sửa logic lõi.
2. **Triệt tiêu sự phụ thuộc vào nhà cung cấp (Vendor Lock-in Elimination)**:
   - Nếu công ty muốn chuyển từ AWS S3 sang Google Cloud Storage hoặc MinIO, chúng em chỉ cần tạo một Outbound Adapter mới (`GcsStorageServiceImpl`) cài đặt `StoragePort`. Toàn bộ code nghiệp vụ upload bài hát không bị sửa đổi dù chỉ 1 dòng.
3. **Kiểm thử độc lập hoàn toàn (Testability)**:
   - Khi viết Unit Test cho `LoginUseCaseImpl`, em chỉ cần tạo một Fake Outbound Adapter (ví dụ dùng `HashMap` lưu user trong RAM) để truyền vào Port `UserRepository`. Test chạy tức thì trong 2 mili-giây mà không cần dựng database thật.
