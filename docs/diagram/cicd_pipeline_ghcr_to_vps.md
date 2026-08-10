# Pipeline CI/CD từ GitHub Actions qua GHCR tới VPS

> **Phân loại**: Không thuộc C4 — Pipeline Diagram. Sơ đồ tự động hóa quy trình CI/CD tích hợp từ GitHub Actions, lưu trữ image tại GitHub Container Registry (`ghcr.io`) và tự động triển khai tới VPS qua SSH.

---

## 1. Biểu đồ Mermaid

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

---

## 2. Mô tả Chi tiết Các Bước Pipeline

| Giai đoạn | Thành phần / Công cụ | Mô tả Chi tiết |
|---|---|---|
| **Trigger** | GitHub Webhook | Kích hoạt khi `push` vào branch `main` hoặc chạy thủ công (`workflow_dispatch`). Cấu hình `concurrency: 1` đảm bảo chỉ một pipeline chạy tại một thời điểm. |
| **CI (Test)** | `ci.yml` Job trên GitHub Runner | Chạy `mvn test` (kèm gói `ffmpeg`) cho Backend Java 21 và `pnpm lint + test` cho Frontend Next.js. |
| **Build & Push** | Docker Buildx | Đóng gói 2 Docker Images (`pwb-backend`, `pwb-frontend`), sử dụng `cache type=gha` và push lên `ghcr.io` với tag `sha-<short>` và `main`. |
| **Container Registry** | `ghcr.io` | Lưu trữ 2 container image cho frontend và backend sẵn sàng cho VPS kéo về. |
| **Pull qua SSH** | `appleboy/ssh-action` | Kết nối SSH vào VPS, chạy `git reset --hard` và tạo file `.env.deploy`. |
| **Compose Up** | Docker Compose | Chạy `docker compose up -d` kéo 2 images mới nhất và reload cấu hình Nginx (`nginx -s reload`). |
| **Chờ Health** | Script Kiểm tra Healthcheck | Thực hiện vòng lặp 30 lần × 10s kiểm tra `/actuator/health` trả về `UP` trước khi đánh dấu hoàn thành. |
