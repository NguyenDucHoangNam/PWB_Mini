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
        SMTP["<b>Máy chủ SMTP</b><br/>[Hệ thống ngoài]<br/><i>Gửi email OTP</i>"]:::extBox
    end

    P --> SYS
    K --> SYS
    Q --> SYS

    SYS --> S3
    SYS --> OAUTH
    SYS --> TTS
    SYS --> SMTP
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
| **Máy chủ SMTP** | Hệ thống ngoài | Gửi email chứa mã OTP. |
