# C4 mức 2 — Sơ đồ container

> **Chuẩn C4 — Level 2: Container**: Thể hiện bức tranh toàn cảnh các Container ứng dụng (Trình duyệt SPA, Nginx Proxy, Next.js Frontend, Spring Boot Backend, Postgres, Redis, Kafka, Coturn) và các hệ thống Cloud bên ngoài.

---

## 1. Biểu đồ Mermaid

```mermaid
graph TD
    classDef userBox fill:#963E2E,stroke:#D96B52,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;
    classDef appBox fill:#483D8B,stroke:#7A70D6,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;
    classDef dbBox fill:#0D6B56,stroke:#22A385,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;
    classDef extBox fill:#3D3D3D,stroke:#707070,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;

    BROWSER["<b>Trình duyệt</b><br/>[Client · SPA]<br/><i>React 19 · STOMP · WebRTC</i>"]:::userBox

    subgraph VPS ["Máy chủ VPS — Docker Compose · một instance duy nhất"]
        direction TD
        
        subgraph ENTRY ["Cổng vào & Relay"]
            direction LR
            NGINX["<b>Nginx 1.27</b><br/>[Container: reverse proxy]<br/><i>TLS · định tuyến 80/443</i>"]:::appBox
            COTURN["<b>coturn 4.6</b><br/>[Container]<br/><i>TURN relay WebRTC</i>"]:::appBox
        end

        subgraph BACKEND_SERVICES ["Dịch vụ ứng dụng"]
            direction LR
            NEXT["<b>Next.js 16</b><br/>[Container]<br/><i>SSR · cổng 3000</i>"]:::appBox
            SPRING["<b>Spring Boot 3.5</b><br/>[Container: Java 21]<br/><i>66 REST + 11 STOMP · cổng 8080</i>"]:::appBox
        end

        subgraph DATA_TIER ["Hạ tầng Dữ liệu"]
            direction LR
            PG["<b>Postgres 16</b><br/>[Database]<br/><i>18 bảng · 48 migration</i>"]:::dbBox
            REDIS["<b>Redis 7.2</b><br/>[Cache]<br/><i>Phiên · khoá · đếm</i>"]:::dbBox
            KAFKA["<b>Kafka 7.6</b><br/>[Message broker]<br/><i>2 topic + 1 DLT</i>"]:::dbBox
        end

        NGINX --> NEXT
        NGINX --> SPRING
        SPRING --> PG
        SPRING --> REDIS
        SPRING --> KAFKA
    end

    subgraph EXTERNALS ["Hệ thống bên ngoài"]
        direction LR
        S3["<b>Amazon S3</b><br/><i>File nhạc · ảnh</i>"]:::extBox
        OAUTH["<b>Google OAuth</b><br/><i>Đăng nhập</i>"]:::extBox
        TTS["<b>Google TTS</b><br/><i>Sinh voice tag</i>"]:::extBox
        SMTP["<b>SMTP</b><br/><i>Gửi email OTP</i>"]:::extBox
    end

    BROWSER --> NGINX
    BROWSER --> COTURN
    BROWSER --> S3
    BROWSER --> OAUTH
    SPRING --> S3
    SPRING --> OAUTH
    SPRING --> TTS
    SPRING --> SMTP
```

---

## 2. Mô tả Chi tiết Các Container

| Container / Dịch vụ | Loại Container | Công nghệ & Chi tiết | Vai trò trong hệ thống |
|---|---|---|---|
| **Trình duyệt** | Client SPA | React 19, STOMP, WebRTC | Giao diện người dùng tương tác trực tiếp qua HTTPS, WebSocket STOMP và WebRTC audio. |
| **Nginx 1.27** | Container: reverse proxy | TLS, định tuyến 80/443 | Cổng vào duy nhất tiếp nhận traffic HTTPS, xử lý TLS termination và định tuyến về Next.js / Spring Boot. |
| **coturn 4.6** | Container | TURN relay WebRTC | Đóng vai trò STUN/TURN server hỗ trợ kết nối âm thanh realtime qua WebRTC NAT traversal. |
| **Next.js 16** | Container | SSR, cổng 3000 nội bộ | Giao diện Frontend Server-Side Rendering (SSR). |
| **Spring Boot 3.5** | Container | Java 21, 66 REST + 11 STOMP | Backend API & Realtime WebSockets giao tiếp với DB, Cache, Kafka và Cloud APIs. |
| **Postgres 16** | Database | 18 bảng, 48 migration | Lưu trữ toàn bộ dữ liệu quan hệ của hệ thống. |
| **Redis 7.2** | Cache | Phiên, khoá, đếm | Caching, lưu trữ session người dùng, rate limit và lock phân tán. |
| **Kafka 7.6** | Message broker | 2 topic + 1 DLT | Hàng chờ xử lý sự kiện bất đồng bộ (chuyển đổi định dạng nhạc). |
| **Hệ thống bên ngoài** | External Services | S3, OAuth, TTS, SMTP | Dịch vụ lưu trữ file, xác thực, tổng hợp giọng nói và gửi mail OTP. |
