# Bộ Sơ đồ Kiến trúc Hệ thống Producer Workbench (PWB_MiNi)

Tài liệu này tổng hợp toàn bộ **5 sơ đồ kiến trúc chuẩn hóa** của dự án **Producer Workbench (PWB_MiNi)** được đặt tên và phân loại chuẩn xác theo tiêu chuẩn C4 Model và Deployment/Pipeline specifications.

---

## 📌 Bảng Quy chuẩn Tên hình & Chuẩn C4

| # | Tên hình (Filename) | Tên Tiêu đề Sơ đồ | Chuẩn C4 |
|---|---|---|---|
| **1** | [c4_level1_system_context.md](file:///d:/Learning/Project/PWB_MiNi/docs/diagram/c4_level1_system_context.md) | **C4 mức 1 — Sơ đồ ngữ cảnh hệ thống** | Level 1: System Context |
| **2** | [c4_level2_container_diagram.md](file:///d:/Learning/Project/PWB_MiNi/docs/diagram/c4_level2_container_diagram.md) | **C4 mức 2 — Sơ đồ container** | Level 2: Container |
| **3** | [c4_level3_backend_layers.md](file:///d:/Learning/Project/PWB_MiNi/docs/diagram/c4_level3_backend_layers.md) | **C4 mức 3 — Bốn tầng bên trong container backend** | Level 3: Component |
| **4** | [c4_deployment_diagram_vps.md](file:///d:/Learning/Project/PWB_MiNi/docs/diagram/c4_deployment_diagram_vps.md) | **Sơ đồ triển khai trên VPS** | Supplementary: Deployment |
| **5** | [cicd_pipeline_ghcr_to_vps.md](file:///d:/Learning/Project/PWB_MiNi/docs/diagram/cicd_pipeline_ghcr_to_vps.md) | **Pipeline CI/CD từ GitHub Actions qua GHCR tới VPS** | Không thuộc C4 — pipeline diagram |

---

## 1. C4 mức 1 — Sơ đồ ngữ cảnh hệ thống

> **Chuẩn C4 — Level 1: System Context**: Biểu diễn mối quan hệ giữa người dùng (Producer, Khách nghe demo, Admin), ứng dụng trung tâm Producer Workbench và 4 hệ thống ngoài (S3, OAuth, TTS, SMTP).

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

## 2. C4 mức 2 — Sơ đồ container

> **Chuẩn C4 — Level 2: Container**: Biểu diễn toàn bộ cụm Container vận hành hệ thống (Trình duyệt SPA, Nginx Proxy, Next.js Frontend, Spring Boot Backend, Postgres DB, Redis Cache, Kafka Message Broker, Coturn Relay Server và các dịch vụ Cloud bên ngoài).

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

## 3. C4 mức 3 — Bốn tầng bên trong container backend

> **Chuẩn C4 — Level 3: Component**: Chi tiết giải phẫu 4 tầng cấu trúc bên trong container backend (`api` -> `application` -> `domain` <- `infrastructure`). Cả 3 module (`iam`, `audio`, `liveroom`) đều lặp lại bốn tầng này.

```mermaid
graph TD
    classDef appBox fill:#483D8B,stroke:#7A70D6,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;
    classDef domainBox fill:#0D6B56,stroke:#22A385,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;

    subgraph BACKEND ["pwb-backend — cả ba module iam · audio · liveroom đều lặp lại bốn tầng này"]
        direction TD
        
        API["<b>api</b><br/>[Tầng]<br/><i>11 REST controller + 4 STOMP controller · DTO request & response</i>"]:::appBox
        
        APP["<b>application</b><br/>[Tầng]<br/><i>57 use case impl · command → view/dto · @Transactional đặt ở method</i>"]:::appBox
        
        DOM["<b>domain</b><br/>[Tầng — Java thuần, không Spring, không JPA]<br/><i>model · vo · enums · 17 repository port · service port</i>"]:::domainBox
        
        INFRA["<b>infrastructure</b><br/>[Tầng]<br/><i>18 JPA entity · mapper viết tay · adapter · realtime STOMP · scheduler</i>"]:::appBox

        API --> APP
        APP --> DOM
        INFRA --> DOM
    end
```

---

## 4. Sơ đồ triển khai trên VPS

> **Chuẩn C4 — Supplementary: Deployment**: Sơ đồ triển khai chi tiết các Docker Containers trên VPS Ubuntu (7.6GB RAM), thể hiện rõ ràng mạng bridge `pwb-network`, volume persistent và host network cho Coturn.

```mermaid
graph TD
    classDef userBox fill:#963E2E,stroke:#D96B52,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;
    classDef appBox fill:#483D8B,stroke:#7A70D6,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;
    classDef dbBox fill:#0D6B56,stroke:#22A385,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;

    BROWSER["<b>Trình duyệt</b><br/>[Thiết bị người dùng]<br/><i>HTTPS · WSS · WebRTC</i>"]:::userBox

    subgraph VPS ["Máy chủ VPS — Ubuntu · Docker Engine · 7.6GB RAM"]
        direction TD

        subgraph PWB_NET ["Mạng pwb-network — bridge, chỉ Nginx publish cổng ra ngoài"]
            direction TD
            
            subgraph WEB_PROXY ["Tầng Web & Proxy SSL"]
                direction LR
                NGINX["<b>Nginx 1.27</b><br/>[Container]<br/>Publish 80:80 và 443:443<br/><i>vol certbot_conf + certbot_www (ro)</i>"]:::appBox
                CERTBOT["<b>Certbot</b><br/>[Container]<br/>Không publish<br/><i>vol certbot_conf (ghi)</i>"]:::appBox
            end

            subgraph APPS ["Tầng Ứng dụng"]
                direction LR
                NEXT["<b>Next.js 16</b><br/>[Container]<br/>3000 — chỉ nội bộ<br/><i>không volume</i>"]:::appBox
                SPRING["<b>Spring Boot 3.5</b><br/>[Container: JRE 21 + ffmpeg]<br/>8080 — chỉ nội bộ · mem 1500m · 1 instance<br/><i>vol audio_work · secret gcp-tts.json</i>"]:::appBox
            end

            subgraph DATABASES ["Tầng Dữ liệu & Message Broker"]
                direction LR
                PG["<b>Postgres 16</b><br/>[Container]<br/>5432 nội bộ<br/><i>vol postgres_data</i>"]:::dbBox
                REDIS["<b>Redis 7.2</b><br/>[Container]<br/>6379 nội bộ<br/><i>vol redis_data</i>"]:::dbBox
                KAFKA["<b>Kafka 7.6</b><br/>[Container]<br/>29092 · mem 1g<br/><i>vol kafka_data</i>"]:::dbBox
            end

            NGINX --> NEXT
            NGINX --> SPRING
            SPRING --> PG
            SPRING --> REDIS
            SPRING --> KAFKA
        end

        subgraph HOST_NET ["Host network — ngoài pwb-network, backend không gọi tới"]
            COTURN["<b>coturn 4.6</b><br/>[Container · network_mode host]<br/>UDP 3478 + 49160-49200<br/><i>mount turnserver.conf (ro)</i>"]:::appBox
        end
    end

    BROWSER --> NGINX
    BROWSER --> COTURN
```

---

## 5. Pipeline CI/CD từ GitHub Actions qua GHCR tới VPS

> **Không thuộc C4 — Pipeline Diagram**: Quy trình tự động hóa CI/CD từ lúc push code main, chạy test, buildx image, push `ghcr.io` và pull qua SSH triển khai lên VPS.

```mermaid
graph TD
    classDef appBox fill:#483D8B,stroke:#7A70D6,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;
    classDef registryBox fill:#3D3D3D,stroke:#707070,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;
    classDef vpsBox fill:#963E2E,stroke:#D96B52,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;

    subgraph GHA ["GitHub Actions — ubuntu-latest"]
        direction LR
        TRIGGER["<b>push lên main</b><br/>[Trigger]<br/>hoặc chạy tay<br/><i>concurrency: 1</i>"]:::appBox
        CI["<b>CI — ci.yml</b><br/>[Job: test]<br/>mvn test + ffmpeg<br/><i>pnpm lint + test</i>"]:::appBox
        BUILD["<b>Build & push</b><br/>[Job: build]<br/>docker buildx<br/><i>cache type=gha</i>"]:::appBox

        TRIGGER --> CI
        CI --> BUILD
    end

    GHCR["<b>ghcr.io</b><br/>[Container registry]<br/>pwb-backend · pwb-frontend<br/><i>tag sha-&lt;short&gt; và tag main</i>"]:::registryBox

    subgraph VPS_DEPLOY ["Máy chủ VPS — điều khiển qua SSH"]
        direction LR
        PULL["<b>Pull qua SSH</b><br/>[appleboy/ssh]<br/>git reset --hard<br/><i>ghi .env.deploy</i>"]:::vpsBox
        UP["<b>compose up -d</b><br/>[Docker Compose]<br/>pull 2 image<br/><i>nginx -s reload</i>"]:::vpsBox
        HEALTH["<b>Chờ health</b><br/>[Kiểm tra]<br/>30 lần × 10s<br/><i>/actuator/health UP</i>"]:::vpsBox

        PULL --> UP
        UP --> HEALTH
    end

    BUILD --> GHCR
    GHCR --> PULL
```
