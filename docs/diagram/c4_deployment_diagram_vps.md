# Sơ đồ triển khai trên VPS

> **Chuẩn C4 — Supplementary: Deployment**: Sơ đồ kiến trúc triển khai chi tiết trên môi trường máy chủ VPS (Ubuntu, Docker Engine, 7.6GB RAM), phân chia rõ ràng mạng nội bộ `pwb-network` và `host network`.

---

## 1. Biểu đồ Mermaid

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

## 2. Mô tả Chi tiết Hạ tầng Triển khai VPS

| Node / Component | Cấu hình Mạng & Volume | Chức năng Triển khai |
|---|---|---|
| **Máy chủ VPS** | Ubuntu OS, Docker Engine, 7.6GB RAM | Hạ tầng vật lý/ảo hóa chạy toàn bộ các Docker Containers của hệ thống. |
| **Nginx 1.27** | `pwb-network` (bridge), publish `80:80`, `443:443`<br/>`vol certbot_conf + certbot_www (ro)` | Container duy nhất công khai cổng ra ngoài Internet, đảm nhận SSL và định tuyến. |
| **Certbot** | `pwb-network`, không publish, `vol certbot_conf (ghi)` | Tự động tạo và cập nhật chứng chỉ SSL Let's Encrypt. |
| **Next.js 16** | `pwb-network`, cổng `3000` nội bộ, không volume | Server-Side Rendering Frontend container. |
| **Spring Boot 3.5** | `pwb-network`, cổng `8080` nội bộ, mem `1500m`<br/>`vol audio_work`, `secret gcp-tts.json` | Backend Application Container (JRE 21 + ffmpeg tích hợp). |
| **Postgres 16** | `pwb-network`, cổng `5432` nội bộ, `vol postgres_data` | Database container chính lưu dữ liệu quan hệ. |
| **Redis 7.2** | `pwb-network`, cổng `6379` nội bộ, `vol redis_data` | Cache & Session container. |
| **Kafka 7.6** | `pwb-network`, cổng `29092`, mem `1g`, `vol kafka_data` | Message Broker container phục vụ xử lý bất đồng bộ. |
| **coturn 4.6** | `network_mode host`, UDP `3478 + 49160-49200`<br/>`mount turnserver.conf (ro)` | TURN/STUN server nằm ngoài `pwb-network` chạy trực tiếp ở host network phục vụ WebRTC. |
