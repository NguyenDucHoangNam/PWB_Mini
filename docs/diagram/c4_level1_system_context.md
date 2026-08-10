# C4 mức 1 — Sơ đồ ngữ cảnh hệ thống

> **Chuẩn C4 — Level 1: System Context**: Thể hiện ranh giới hệ thống Producer Workbench, các nhóm đối tượng người dùng tương tác và các dịch vụ bên ngoài tích hợp.

---

## 1. Biểu đồ Mermaid

```mermaid
graph TD
    classDef userBox fill:#963E2E,stroke:#D96B52,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;
    classDef appBox fill:#483D8B,stroke:#7A70D6,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;
    classDef extBox fill:#3D3D3D,stroke:#707070,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;

    subgraph USERS ["NGƯỜI DÙNG TƯƠNG TÁC"]
        direction LR
        P["<b>Producer</b><br/>[Người dùng]<br/><i>Tải nhạc, mở phòng</i>"]:::userBox
        K["<b>Khách nghe demo</b><br/>[Người dùng]<br/><i>Vào phòng, góp ý</i>"]:::userBox
        Q["<b>Quản trị viên</b><br/>[Người dùng]<br/><i>Quản lý tài khoản</i>"]:::userBox
    end

    SYS["<b>Producer Workbench</b><br/>[Hệ thống phần mềm]<br/><i>Tải nhạc · gắn voice tag · nghe chung realtime</i>"]:::appBox

    subgraph EXTERNALS ["HỆ THỐNG BÊN NGOÀI"]
        direction LR
        S3["<b>Amazon S3</b><br/>[Hệ thống ngoài]<br/><i>Lưu file nhạc</i>"]:::extBox
        OAUTH["<b>Google OAuth</b><br/>[Hệ thống ngoài]<br/><i>Đăng nhập Google</i>"]:::extBox
        TTS["<b>Google TTS</b><br/>[Hệ thống ngoài]<br/><i>Sinh voice tag</i>"]:::extBox
        TURN["<b>coturn</b><br/>[Hệ thống ngoài]<br/><i>Chuyển tiếp media WebRTC</i>"]:::extBox
        SMTP["<b>Máy chủ SMTP</b><br/>[Hệ thống ngoài]<br/><i>Gửi email OTP</i>"]:::extBox
    end

    P -->|"Tải nhạc, mở phòng nghe<br/>[HTTPS]"| SYS
    K -->|"Nghe chung, chat, góp ý<br/>[HTTPS · WSS]"| SYS
    Q -->|"Quản lý tài khoản<br/>[HTTPS]"| SYS

    SYS -->|"Lưu / đọc file nhạc<br/>[S3 API · presigned URL]"| S3
    SYS -->|"Xác minh ID token<br/>[HTTPS]"| OAUTH
    SYS -->|"Sinh voice tag<br/>[gRPC]"| TTS
    SYS -->|"Chuyển tiếp media<br/>[TURN · STUN]"| TURN
    SYS -->|"Gửi email OTP<br/>[SMTP]"| SMTP
```

---

## 2. Mô tả Chi tiết Thành phần

| Thành phần | Loại | Chức năng chính |
|---|---|---|
| **Producer** | Người dùng | Tải nhạc lên hệ thống, tạo và quản lý phòng nghe nhạc trực tiếp (Live Room). |
| **Khách nghe demo** | Người dùng | Tham gia vào các phòng nghe demo, trò chuyện, phát nhạc đồng bộ và đưa ra góp ý. |
| **Quản trị viên** | Người dùng | Quản lý tài khoản, phân quyền và giám sát hệ thống. |
| **Producer Workbench** | Hệ thống phần mềm | Hệ thống cốt lõi: Tải nhạc, gắn voice tag, nghe chung realtime. |
| **Amazon S3** | Hệ thống ngoài | Lưu trữ file nhạc và tài nguyên truyền thông. |
| **Google OAuth** | Hệ thống ngoài | Xác thực đăng nhập qua Google. |
| **Google TTS** | Hệ thống ngoài | Sinh voice tag tự động. |
| **coturn** | Hệ thống ngoài | Máy chủ TURN/STUN, chuyển tiếp luồng media WebRTC giữa các thành viên trong phòng khi kết nối ngang hàng trực tiếp không thiết lập được. |
| **Máy chủ SMTP** | Hệ thống ngoài | Gửi email chứa mã OTP. |

---

## 3. Ghi chú về phạm vi mức 1

Sơ đồ này cố ý **không** tách frontend khỏi backend: ở C4 mức 1, ứng dụng web chạy trong trình duyệt vẫn nằm bên trong ranh giới Producer Workbench. Hệ quả là hai chi tiết dưới đây đúng với code nhưng thuộc về mức 2 (Container), không vẽ ở đây:

- **File nhạc không đi qua backend.** Backend chỉ ký presigned URL (`PutObjectPresignRequest` / `GetObjectPresignRequest` trong `S3StorageServiceImpl`), còn trình duyệt đẩy và tải file trực tiếp với S3. Ở mức 1 điều này gộp thành một quan hệ `Producer Workbench → Amazon S3`, nhãn ghi rõ `presigned URL`.
- **Luồng đăng nhập Google bắt đầu ở trình duyệt.** Frontend nạp Google Identity Services rồi gửi ID token về backend; backend chỉ xác minh token bằng `GoogleIdTokenVerifier`. Ở mức 1 vẫn là một quan hệ `Producer Workbench → Google OAuth`.

Giao thức trên nhãn lấy từ code, không suy từ tài liệu: Google TTS đi **gRPC** (client dựng bằng `TextToSpeechSettings` mặc định trong `GoogleTtsAdapter`), không phải REST.
