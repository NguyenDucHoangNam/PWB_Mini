# Kế hoạch Deploy — PWB MiNi

**Mục tiêu**: 1 VPS + Docker Compose, phục vụ demo/bảo vệ đồ án, lưu trữ audio trên AWS S3 thật.
**Ngày lập**: 2026-08-08

---

## 1. Ràng buộc quyết định hình dạng kế hoạch

Ba điều dưới đây không phải lựa chọn — chúng là hệ quả của code hiện tại và mọi quyết định deploy phải chấp nhận:

| Ràng buộc | Nguồn | Hệ quả |
|---|---|---|
| Backend **chỉ chạy được 1 instance** | `WebSocketConfig.configureMessageBroker` dùng `enableSimpleBroker` (broker in-memory) | Không scale ngang, không blue-green 2 container cùng lúc. Deploy = downtime ngắn. |
| Chỉ có adapter S3 | Chỉ tồn tại `S3StorageServiceImpl`, không có local storage impl | Bắt buộc phải có bucket S3 hoạt động thì upload/stream mới chạy. |

*(Ràng buộc thứ ba — "không có TURN server" — đã được gỡ bỏ ngày 2026-08-08 bằng cách dựng coturn; xem §5.4.)*

---

## 2. Kiến trúc triển khai đích

```
                                  Internet
                                     │
                            [ DNS A record ]
                  producerworkbench.online  ─┐
              api.producerworkbench.online  ─┤
             turn.producerworkbench.online ──┼──────────────┐
                                 │        │                 │
                                 │        │        media WebRTC, KHÔNG qua Nginx
                        ┌────────▼────────▼────────┐  ┌─────▼──────────────────┐
                        │  Nginx (443) + certbot   │  │ coturn                 │
                        └────┬─────────────────┬───┘  │ 3478 udp/tcp           │
                             │                 │      │ 49160-49200 udp        │
               frontend ─────┘                 └─ api │ network_mode: host     │
                             │                    │   └────────────────────────┘
                             │                    │
                     ┌───────▼────────┐  ┌────────▼────────────┐
                     │  frontend      │  │  backend            │
                     │  Next.js :3000 │──▶  Spring Boot :8080  │
                     │  (standalone)  │SSR│  1 instance duy nhất│
                     └────────────────┘  └──────────┬──────────┘
                                                    │
                        ┌─────────┬──────────┬──────┴──┬─────────┬──────────┐
                        │         │          │         │         │          │
                  ┌─────▼────┐ ┌──▼────┐ ┌───▼────┐ ┌──▼────────▼──┐  ┌────▼────┐
                  │ Postgres │ │ Redis │ │ Kafka  │ │Elasticsearch │  │ AWS S3  │
                  │  :5432   │ │ :6379 │ │ :29092 │ │    :9200     │  │ (ngoài) │
                  └──────────┘ └───────┘ └────────┘ └──────────────┘  └─────────┘
                           (chỉ nội bộ, KHÔNG publish port ra host)
```

**MongoDB đã bị gỡ khỏi cả hai compose** (2026-08-08). Code không có một dependency mongo nào — không import, không annotation, không cấu hình; chat của live room nằm ở Postgres (`V303__create_liveroom_chat.sql`). Nó chạy không phục vụ gì và tốn ~300MB RAM.

**Giữ lại Elasticsearch** (`PWB_SEARCH_ENABLED=true`, mặc định). Tắt đi sẽ mất xếp hạng theo độ liên quan, autocomplete và tìm kiếm tiếng Việt có dấu — truy vấn rơi về Postgres thuần. Với VPS 8GB thì 1.25GB cho ES là trong tầm, và đây là tính năng demo được.

> ⚠️ Bắt buộc đặt `SPRING_ELASTICSEARCH_URIS=http://elasticsearch:9200`. Giá trị mặc định trong `application.yml` là `http://localhost:9200` — bên trong container backend, `localhost` là chính nó, nên thiếu biến này thì **mọi truy vấn ES đều fail rồi âm thầm rơi về Postgres**. Search vẫn "chạy", chỉ là không bao giờ dùng tới ES.
>
> **Cách xác minh (đã sửa 2026-08-08 — hướng dẫn cũ ở đây không dùng được).** Bản trước bảo tìm log `SEARCH.* fallback to database`. Dòng đó có tồn tại, nhưng nó ở mức **DEBUG**, mà prod chạy `com.pwb: INFO` — nên nó **không bao giờ được in ra**. Bài kiểm tra vì thế luôn "đạt", kể cả khi ES chưa từng được gọi tới lần nào.
>
> Giờ backend log đúng một dòng dứt khoát lúc khởi động, cho cả hai kết quả:
>
> ```bash
> docker compose -f docker-compose.prod.yml logs backend | grep SEARCH.startup
> ```
>
> | Thấy gì | Nghĩa là |
> |---|---|
> | `SEARCH.startup Elasticsearch reachable: uris=...` | Đạt |
> | `SEARCH.startup Elasticsearch UNREACHABLE: uris=...` | Sai cấu hình — dòng log in ra đúng giá trị `uris` mà backend đang dùng, thường sẽ thấy `localhost:9200` |
> | Không có dòng nào | `pwb.search.enabled=false`, hoặc backend chưa khởi động xong |
>
> Phép thăm dò dùng `ping()` chứ không đọc trạng thái cụm, và nó **chỉ báo cáo, không quyết định** — hai điều này đều là sẹo từ lần chạy thử đầu tiên, xem ghi chú trong [SearchIndexBootstrapper](../Backend/shared/shared-infrastructure/src/main/java/com/pwb/infra/search/SearchIndexBootstrapper.java:55). Dự án chạy client 8.18 với server 8.12; API có schema (`cluster.health`) decode fail vì lệch phiên bản, còn `ping()` là HEAD nên không có gì để lệch.
>
> Ngoài ra khi cụm chết **giữa chừng** (chứ không phải sai cấu hình từ đầu), request đầu tiên thất bại giờ log `SEARCH.unavailable` ở mức WARN kèm lý do, và lúc hồi phục log `SEARCH.recovered`. Chỉ một dòng cho mỗi lần chuyển trạng thái — trước đây mọi thứ đều ở DEBUG nên một cụm chết hoàn toàn không để lại dấu vết nào.

**Cấu hình VPS đề nghị**: 4 vCPU / 8GB RAM / 80GB SSD.

| Thành phần | RAM dự kiến |
|---|---|
| Backend (JVM, `MaxRAMPercentage=75`, limit 1.5GB) | ~1.5 GB |
| Kafka (KRaft, heap mặc định) | ~1.0 GB |
| Elasticsearch (`mem_limit: 1280m`, heap 512m) | ~1.25 GB |
| Postgres | ~0.5 GB |
| Frontend Next.js | ~0.4 GB |
| Redis + Nginx + certbot | ~0.2 GB |
| coturn (`mem_limit: 256m`, thực tế ~50MB) | ~0.1 GB |
| Dự phòng cho ffmpeg (job transcode audio) | ~1.0 GB |
| **Tổng** | **~5.95 GB** |

**8GB giờ là mức tối thiểu thực tế, không còn là mức thoải mái** — 4GB không đủ. Nếu buộc phải dùng VPS nhỏ hơn, ES là thứ đầu tiên nên cắt (`PWB_SEARCH_ENABLED=false`), vì nó là thành phần duy nhất đã có sẵn đường lui hoàn chỉnh.

#### `mem_limit` của ES: 1g → 1280m (2026-08-08), dựa trên số đo

Đo trên container 4 vCPU chạy `elasticsearch:8.12.0`, cùng heap `-Xms512m -Xmx512m`, sau khi index 10.000 document tiếng Việt có dấu và chạy 300 truy vấn:

| `mem_limit` | Bộ nhớ anonymous | Còn dư | Kết quả |
|---|---|---|---|
| `1g` *(cũ)* | 917 MiB | **90 MiB — 8.8%** | vẫn sống, không bị OOM-kill |
| `1280m` *(mới)* | 945 MiB | 335 MiB — 26% | vẫn sống |

**Nói cho đúng: tôi không tái hiện được vụ OOM-kill.** Cấu hình cũ chịu được đúng tải đó. Đây là nới một biên độ mỏng, không phải vá một lỗi đã thấy.

Cái làm biên độ đó đáng nới là **gần như không có gì thu hồi được**: page cache chỉ 5 MiB so với ~950 MiB anonymous. Khi chạm trần, kernel không có gì để evict — bước tiếp theo là `SIGKILL`, không phải chậm đi. Và vì `-Xms` bằng `-Xmx` (khuyến nghị của chính ES), 512m heap đã bị chiếm trước khi có document đầu tiên; phần còn lại là thread stack, metaspace và direct buffer, đều phình theo tải.

Hai điều đã **cân nhắc rồi bỏ**:

- **`node.processors=4`** — ES cấp thread pool theo số CPU, và đo trên máy 16 nhân cho anonymous cao hơn máy 4 nhân 61 MiB. Nhưng JVM từ JDK 10 đã đọc được giới hạn cgroup, nên trên VPS 4 vCPU nó vốn đã thấy 4. Khai thêm biến này **không đổi gì cả** trên đúng phần cứng đang nhắm tới. Chỉ đáng thêm nếu sau này chuyển sang VPS nhiều nhân hơn.
- **`xpack.ml.enabled=false`** — vẫn thêm, nhưng chỉ tiết kiệm ~8 MiB (một tiến trình native `controller`). Nó rẻ và ta không bao giờ dùng ML, chứ không phải một khoản đáng kể.

Nếu ES vẫn bị OOM-kill trên VPS thật, nó sẽ vào vòng restart liên tục — nhưng backend **không** `depends_on` health của ES, nên nó không kéo theo gì cả. Và giờ vòng lặp đó có dấu vết: mỗi lần cụm mất là một dòng `SEARCH.unavailable` WARN trong log backend.

---

## 3. Giai đoạn 0 — Sửa blocker trong code ✅ HOÀN THÀNH 2026-08-08

Tám blocker đã sửa và kiểm chứng bằng cách khởi động ứng dụng thật (chạy jar trên cổng phụ, cả profile `dev` lẫn `prod`). Chi tiết nằm trong lịch sử git; dưới đây chỉ giữ lại kết quả để tra cứu.

| Sửa | Kiểm chứng |
|---|---|
| `dev-users` chỉ kích hoạt cùng profile `dev` (qua `spring.profiles.group`) | `dev` → 2 profile active; `prod` → 1 profile, không dòng seeder nào |
| `application-prod.yml` dùng tên biến chuẩn Spring Boot | prod khởi động hoàn tất trong 14.4s |
| `pwb.cors.allowed-origins` chuyển từ danh sách YAML sang chuỗi, prod bỏ default | thiếu biến → dừng ngay với `PlaceholderResolutionException` |
| Bỏ default của `APP_JWT_SECRET` và `APP_PASSWORD_RESET_TOKEN_SECRET` | thiếu biến → không khởi động được |
| `/actuator/health` vào `public-endpoints`, chỉ expose `health`, `show-details: never` | `health` → 200 `{"status":"UP"}`; `env` và `beans` → 401 |
| *(sửa lại)* Ba mục trên **đều trùng với mặc định sẵn có** — `SecurityConfig.DEFAULT_PUBLIC_ENDPOINTS` vốn đã permit `/actuator/health` và gộp với danh sách trong yml, còn Spring Boot mặc định chỉ expose `health` với `show-details: never`. Lo ngại ban đầu của tôi rằng healthcheck sẽ trả 401 là **không đúng**. Giữ lại vì viết tường minh có ích, nhưng đây không phải blocker. | |
| `SPRING_ELASTICSEARCH_URIS` bắt buộc ở prod | — |
| `output: "standalone"` cho Next.js | — |
| `credentials-path` của Google TTS trỏ vào secret mount | — |
| Thêm `modules/liveroom/pom.xml` vào cache layer của Dockerfile | — |

> **Phát hiện kèm theo**: `pwb.cors.allowed-origins` trước đây viết dạng danh sách YAML nhưng cả hai nơi đọc đều dùng `@Value`, vốn không nhìn thấy các khóa `[0]`, `[1]`. Giá trị trong file cấu hình **chưa từng có tác dụng** — trùng hợp là default viết cứng trong annotation cũng là `localhost:3000` nên ở dev không ai nhận ra.

---

## 4. Giai đoạn 1 — Chuẩn bị tài nguyên bên ngoài

Phần lớn là thời gian chờ (DNS propagate, duyệt domain), nên bắt đầu càng sớm càng tốt.

### 4.1 VPS
- Thuê VPS 4 vCPU / 8GB / 80GB (Vultr, DigitalOcean, Hetzner, hoặc VNG/Viettel nếu cần latency thấp ở VN).
- Ubuntu 24.04 LTS.
- Tạo user thường + SSH key, tắt đăng nhập bằng password và tắt SSH root.
- Firewall (`ufw`): mở **22, 80, 443**, cộng thêm phần cho TURN ở dưới. Tất cả port DB/Kafka/Redis **không** publish ra host.
- Cài Docker Engine + Docker Compose plugin.

**Port cho coturn** — không mở đủ thì TURN im lặng không hoạt động, không có lỗi ở đâu cả:

```bash
sudo ufw allow 3478/udp && sudo ufw allow 3478/tcp && sudo ufw allow 49160:49200/udp
```

> Dải `49160-49200` là relay port, khớp với `min-port`/`max-port` trong [docker/coturn/turnserver.conf](../docker/coturn/turnserver.conf). Mở 3478 mà quên dải này là kiểu hỏng khó chẩn đoán nhất: client bắt tay được với TURN server, xin được allocation, nhưng gói media không bao giờ tới nơi.
>
> **Với nhà cung cấp có firewall riêng ở tầng cloud** (AWS Security Group, GCP VPC firewall, Vultr Firewall), phải mở đúng ba dòng trên ở đó nữa — `ufw` trong máy không thay thế được.

### 4.2 Domain & DNS

Domain đã chốt: **`producerworkbench.online`**.

| Bản ghi | Trỏ về | Dùng cho |
|---|---|---|
| `@` (A) | IP VPS | `https://producerworkbench.online` — frontend |
| `api` (A) | IP VPS | `https://api.producerworkbench.online` — backend, gồm cả WebSocket |
| `turn` (A) | IP VPS | `turn:turn.producerworkbench.online:3478` — TURN relay |

Tương ứng trong `.env.prod`: `FRONTEND_DOMAIN=producerworkbench.online`, `API_DOMAIN=api.producerworkbench.online`, và `TURN_URLS` trỏ vào `turn.producerworkbench.online`.

> Bản ghi `turn` là **tùy chọn** — có thể để `TURN_URLS` trỏ thẳng vào IP. Không có TLS ở đây nên không có chứng chỉ nào phải khớp tên miền. Dùng subdomain chỉ để sau này đổi VPS thì không phải rebuild gì (`TURN_URLS` đọc lúc runtime, khác `NEXT_PUBLIC_*`). Cũng vì thế **certbot không cần biết tới `turn`** — đừng thêm nó vào danh sách domain xin chứng chỉ.

- Chờ DNS propagate **trước khi** chạy `init-letsencrypt.sh` — Let's Encrypt phải gọi ngược về domain để xác minh, và thất bại nhiều lần sẽ đụng rate limit (5 chứng chỉ/domain/tuần).

### 4.3 AWS S3
- Tạo bucket ở region gần người dùng (`ap-southeast-1`).
- **Chặn toàn bộ public access** — code dùng presigned URL, bucket không cần public.
- Tạo IAM user riêng, policy chỉ gồm `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject`, `s3:AbortMultipartUpload` giới hạn trên đúng bucket đó. Lấy access key / secret key.
- **Cấu hình CORS cho bucket** — bước này hay bị quên và làm hỏng cả upload lẫn phát nhạc, vì trình duyệt gọi thẳng S3 qua presigned URL:

```json
[{
  "AllowedOrigins": ["https://producerworkbench.online"],
  "AllowedMethods": ["GET", "PUT"],
  "AllowedHeaders": ["*"],
  "ExposeHeaders": ["ETag", "Content-Range", "Content-Length"],
  "MaxAgeSeconds": 3000
}]
```

- Lifecycle rule xóa multipart upload dở dang sau 7 ngày (tránh phát sinh phí ẩn).

### 4.4 Dịch vụ bên thứ ba
- **SMTP**: Gmail app password dùng được cho demo. Nếu cần ổn định hơn → Brevo/Resend (free tier đủ dùng). Backend gửi OTP qua Kafka rồi mới ra SMTP, nên SMTP hỏng sẽ làm nghẽn cả luồng đăng ký.
- **Google OAuth**: thêm `https://producerworkbench.online` vào Authorized JavaScript origins trong Google Cloud Console. Thiếu bước này thì nút "Đăng nhập với Google" fail trên prod dù dev vẫn chạy.
- **Cloudflare Turnstile**: tạo site key/secret key mới cho domain prod (key dev gắn với `localhost`).

---

## 5. Giai đoạn 2 — Artifact deploy ✅ ĐÃ TẠO 2026-08-08

Đã tạo và kiểm chứng cú pháp: `docker compose config` báo hợp lệ, `nginx -t` báo *configuration file test is successful*. Phần dưới ghi lại các quyết định thiết kế, không phải việc còn phải làm.

| File | Vai trò |
|---|---|
| [Frontend/Dockerfile](../Frontend/Dockerfile) | Build 3 tầng, pnpm 11, chạy bằng `node server.js` |
| [Frontend/.dockerignore](../Frontend/.dockerignore) | Chặn `node_modules`, `.next` và `.env*` lọt vào build context |
| [docker-compose.prod.yml](../docker-compose.prod.yml) | Toàn bộ stack; chỉ Nginx publish port |
| [nginx/nginx.conf](../nginx/nginx.conf) | Reverse proxy, chặn `/actuator/*`, WebSocket cho live room |
| [nginx/init-letsencrypt.sh](../nginx/init-letsencrypt.sh) | Bootstrap chứng chỉ, chạy **một lần** trên VPS |
| [docker/coturn/turnserver.conf](../docker/coturn/turnserver.conf) | TURN relay cho WebRTC — xem §5.4 |

### 5.1 `Frontend/Dockerfile`
Multi-stage, pnpm, tận dụng `output: standalone` đã bật ở §3. Lưu ý: mọi biến `NEXT_PUBLIC_*` được **nhúng vào bundle lúc build**, không đọc lúc runtime — nên chúng phải là build args, không phải env của container.

`NEXT_PUBLIC_SITE_URL` bắt buộc phải truyền: thiếu nó, `next.config.ts` rơi về `PRODUCTION_SITE_URL` trong `Frontend/src/lib/site-defaults.ts`. Giá trị đó nay đã là domain thật nên không còn hỏng, nhưng vẫn nên truyền tường minh.

Các biến cần truyền lúc build: `NEXT_PUBLIC_API_BASE_URL`, `NEXT_PUBLIC_SITE_URL`, `NEXT_PUBLIC_GOOGLE_CLIENT_ID`, `NEXT_PUBLIC_TURNSTILE_SITE_KEY`, `NEXT_PUBLIC_STORAGE_BUCKET_NAME`, `NEXT_PUBLIC_STORAGE_REGION`, `NEXT_PUBLIC_STORAGE_PUBLIC_URL_PREFIX`, `NEXT_PUBLIC_AUTH_OTP_EXPIRY_SECONDS`, `NEXT_PUBLIC_AUTH_OTP_RESEND_COOLDOWN_SECONDS`.

> Hệ quả cần nhớ: đổi domain hoặc đổi bucket ⇒ **phải build lại image frontend**, restart không đủ.

### 5.2 `docker-compose.prod.yml`
Khác bản dev hiện tại ở những điểm:
- Thêm service `backend`, `frontend`, `nginx`, `certbot`.
- **Giữ `elasticsearch`** kèm `mem_limit: 1g` như bản dev (§2).
- **Bỏ toàn bộ `ports:` của postgres/redis/kafka/elasticsearch** — chỉ giao tiếp qua network nội bộ. Bản dev đang publish cả 5 service ra host; giữ nguyên trên VPS công cộng là phơi Postgres, Redis và một Elasticsearch **không bật xác thực** (`xpack.security.enabled=false`) ra Internet.
- Mọi mật khẩu đọc từ `.env` prod, không hardcode như `docker-compose.yml:10` và `:41`.
- `depends_on` với `condition: service_healthy` cho backend, để Flyway không chạy trước khi Postgres sẵn sàng.
- `KAFKA_ADVERTISED_LISTENERS` bỏ listener `PLAINTEXT_HOST://localhost:9092` — trên prod không có client nào ngoài network Docker.
- `restart: unless-stopped` cho tất cả.
- Giới hạn `mem_limit` cho backend (1500m) và kafka (1g).

### 5.3 `nginx/nginx.conf` + certbot

Đã kiểm chứng bằng `nginx -t` trên image `nginx:1.27-alpine`: *configuration file test is successful*.

**Ba directive quyết định live room sống hay chết** — Nginx không làm thứ nào trong số này mặc định:

| Directive | Thiếu thì sao |
|---|---|
| `proxy_http_version 1.1` | Mặc định là HTTP/1.0, **không mang được** header `Upgrade` → WebSocket không bắt tay được |
| `Upgrade` + `Connection` qua `map $http_upgrade` | Nginx không tự chuyển tiếp → handshake fail |
| `proxy_read_timeout 3600s` | Mặc định 60s → phòng nào im lặng quá một phút là **đứt kết nối**, mà đang nghe nhạc thì hầu hết là im lặng |

Ngoài ra `X-Forwarded-Proto` phải khai báo tay (Caddy gửi sẵn, Nginx thì không). Backend đọc header này nhờ `server.forward-headers-strategy: framework`; thiếu nó thì cookie mất cờ `Secure` và link đặt lại mật khẩu sinh ra `http://`.

**Hai chi tiết dễ vấp khác đã xử lý sẵn trong file:**

- `resolver 127.0.0.11` + đặt upstream vào biến. Không có nó, Nginx phân giải tên container **một lần lúc khởi động** và giữ mãi địa chỉ đó — lần `up -d --build` đầu tiên tạo lại container là Nginx trả 502 cho tới khi restart tay.
- Khi target của `proxy_pass` chứa biến, Nginx **ngừng tự nối đường dẫn**, nên phải viết rõ `$request_uri`. Quên thì mọi request đều bị proxy về `/`.

**Bootstrap chứng chỉ — chạy một lần:**

```bash
./nginx/init-letsencrypt.sh you@example.com
```

Script giải quyết thế bí: Nginx không khởi động được khi thiếu file chứng chỉ, nhưng certbot lại cần Nginx đang chạy để phục vụ `/.well-known/acme-challenge/`. Cách gỡ là tạo chứng chỉ tự ký tạm, khởi động Nginx, xin chứng chỉ thật rồi reload.

> Chạy thử với `STAGING=1` trước. Let's Encrypt chỉ cho **5 chứng chỉ mỗi domain mỗi tuần**; sai vài lần là khóa tới hết tuần — rất tệ nếu gần ngày demo.

**Gia hạn tự động** do 2 service lo, không cần cron trên host: `certbot` thử `renew` mỗi 12 giờ, `nginx` reload mỗi 6 giờ để nạp chứng chỉ mới. Chứng chỉ sống 90 ngày và certbot gia hạn trong 30 ngày cuối, nên có khoảng 60 lần thử trước khi hết hạn.

### 5.4 TURN — `coturn` + credential HMAC

**Vấn đề nó giải quyết.** WebRTC ưu tiên đi thẳng peer-to-peer. Sau NAT đối xứng — 4G, phần lớn mạng công ty, một số wifi trường — không đi thẳng được, và triệu chứng đặc biệt khó chịu: người đó **vào phòng được, chat được, thấy playlist chạy đồng bộ**, chỉ riêng không ai nghe thấy ai. Không có lỗi nào ở backend log lẫn console.

**Điểm mấu chốt: đường ống ICE đã có sẵn trong code từ trước.** `RtcConfigResponse` vốn đã mang `urls`/`username`/`credential`, và [peer-connection-manager.ts:180](../Frontend/src/features/liveroom/lib/peer-connection-manager.ts:180) đã map thẳng chúng vào `RTCPeerConnection`. Nên phần frontend **không sửa một dòng nào**.

#### Credential là ephemeral, không phải tĩnh

`GetRtcConfigUseCaseImpl` gọi [TurnCredentialFactory](../Backend/modules/liveroom/src/main/java/com/pwb/liveroom/application/support/TurnCredentialFactory.java) sinh cặp credential mới cho **từng request**, theo `draft-uberti-behave-turn-rest-00`:

```
username   = <unix-timestamp-hết-hạn>:<userId>
credential = base64(HMAC-SHA1(secret, username))
```

coturn tính lại đúng HMAC đó và kiểm tra hạn. Đổi lại một hàm ~10 dòng, **secret không bao giờ rời khỏi server** — thứ đi vào trình duyệt chỉ là một chữ ký hết hạn được. Với credential tĩnh thì bất kỳ ai vào phòng cũng đọc được nó qua DevTools và dùng relay vô thời hạn.

Đã đối chiếu chữ ký sinh ra từ Java với tham chiếu độc lập, khớp từng byte:

```bash
printf '%s' "1786271790:11111111-2222-3333-4444-555555555555" | openssl dgst -sha1 -hmac "dummysecret" -binary | openssl base64
# 6q7dCoMJJT6lvHjDRneGjOx6+/A=   ← đúng bằng giá trị TurnCredentialFactory trả về
```

> **TTL phải dài — 24h, không phải 5 phút.** [rtc-config.ts:29](../Frontend/src/features/liveroom/api/rtc-config.ts:29) cache config với `staleTime: Infinity`, và `PeerConnectionManager` chụp danh sách ICE server đúng một lần lúc khởi tạo. Không có cơ chế nào làm mới credential giữa phiên. Đặt TTL ngắn thì phòng vẫn chạy bình thường cho tới khi credential hết hạn, rồi **mọi peer join sau thời điểm đó** lặng lẽ mất voice trong khi những người đã kết nối vẫn nghe nhau — kiểu lỗi gần như không thể chẩn đoán trên sân khấu.

#### Bốn quyết định trong `turnserver.conf`

| Quyết định | Lý do |
|---|---|
| `network_mode: host`, không `ports:` | Publish dải UDP qua Docker sinh **một tiến trình `docker-proxy` cho mỗi port**. Nặng hơn nữa: coturn quảng bá địa chỉ nó thấy trên interface làm relay candidate — sau bridge đó là `172.x`, trình duyệt không dùng được. |
| `min-port=49160` / `max-port=49200` | Mặc định là 49152–65535. Thu về 40 port đủ cho phòng 7 người mà rule ufw đọc được bằng mắt. |
| `denied-peer-ip` cho toàn bộ dải private | **Đây là điểm bảo mật thật.** TURN relay tới bất cứ đâu client yêu cầu. Chạy trên host namespace, nếu không chặn thì một thành viên phòng có thể trỏ nó vào `127.0.0.1` hoặc mạng bridge Docker để chạm tới Postgres, Redis, và Elasticsearch **đang tắt xác thực**. |
| `no-tls` + `no-dtls` | Media đã mã hóa DTLS-SRTP end-to-end; `turns:` chỉ giấu phần signalling tới relay khỏi DPI. Đổi lại phải có subdomain riêng, chứng chỉ, và **restart coturn mỗi lần certbot gia hạn** (coturn không tự nạp lại cert). Không tương xứng. Thiếu hai dòng này coturn vẫn cố bind 5349 và đòi chứng chỉ. |

Hai cạm bẫy nữa đã xử lý sẵn, cả hai đều tự phát hiện được lúc chạy thử image `coturn/coturn:4.6-alpine`:

- **`-n` không được có trong `command`**, dù chính image mặc định dùng nó. `-n` nghĩa là *bỏ qua file cấu hình, lấy tất cả từ dòng lệnh* — nó sẽ **âm thầm vứt toàn bộ file mount vào**, kể cả mọi dòng `denied-peer-ip`.
- **Không khai `lt-cred-mech` cạnh `use-auth-secret`.** coturn in warning ngay lúc khởi động rằng hai cơ chế xung khắc (shared secret thắng) — một dòng cảnh báo trông y hệt lỗi cấu hình mỗi lần đọc log.

Secret và realm truyền qua **flag dòng lệnh** trong compose chứ không nằm trong file config, nên repo không chứa credential nào.

> `TURN_STATIC_AUTH_SECRET` phải **giống hệt từng byte** ở hai nơi: flag `--static-auth-secret` của coturn và `pwb.liveroom.rtc.turn.secret` của backend. Compose lấy cả hai từ cùng một biến trong `.env.prod` nên không lệch được — nhưng nếu sau này ai tách ra thì lệch nhau là kiểu hỏng **hoàn toàn im lặng**: backend ký bình thường, coturn từ chối, trình duyệt đơn giản là không bao giờ gom được relay candidate.

#### Băng thông — con số cần biết trước

Mesh 7 người là 42 luồng có hướng, và TURN nằm giữa nên mỗi luồng relay tính hai lần (vào + ra).

| Kịch bản (giả sử **tất cả** đều phải relay) | Băng thông tại VPS | Một giờ demo |
|---|---|---|
| Chỉ audio (~50 kbps/luồng) | ~4 Mbps | ~1.8 GB |
| Có video (~800 kbps/luồng) | ~67 Mbps | **~30 GB** |

Thực tế thấp hơn nhiều: relay candidate có độ ưu tiên thấp nhất trong ICE, chỉ cặp nào không đi thẳng được mới dùng tới. Nhưng đây chính là lý do của cảnh báo ngay dưới.

#### Cạm bẫy cuối: STUN phải được khai lại

`application-prod.yml` khai `pwb.liveroom.rtc.ice-servers` với đúng một mục STUN, **trùng y hệt default đã viết cứng trong `LiveroomConfig.Rtc`**. Nhìn thì thừa, nhưng bắt buộc: Spring Boot **thay thế** một `List` khi bind chứ không merge, nên khoảnh khắc khóa đó tồn tại trong yml là default kia biến mất.

Bỏ nó đi thì hệ thống vẫn **chạy hoàn toàn bình thường** — chỉ khác là mọi cặp peer đều phải đi qua relay thay vì thử đi thẳng trước, tức là trả tiền băng thông VPS theo bảng trên cho những kết nối lẽ ra miễn phí. Không có log nào, không có lỗi nào; thứ duy nhất lộ ra là hóa đơn egress. Cùng một kiểu hỏng-im-lặng với `SPRING_ELASTICSEARCH_URIS` ở §2.

Đã kiểm chứng bằng cách bind trực tiếp `application-prod.yml` vào `LiveroomConfig`: `ICE_COUNT=1`, `TURN_URLS` tách đúng hai phần tử (giữ nguyên `?transport=`), `credential-ttl` → `PT24H`.

### 5.5 `.env.prod` trên VPS
Không commit vào git (`.gitignore` đã chặn `.env`, đã kiểm tra: file `.env` hiện tại **không** bị track — tốt). Đặt quyền `chmod 600`, chủ sở hữu là user chạy Docker.

**Sinh mới, không tái dùng giá trị của dev:**

```bash
openssl rand -base64 48   # chạy riêng cho từng biến
```

| Biến | Ghi chú |
|---|---|
| `APP_JWT_SECRET` | Tối thiểu 32 byte — dưới mức đó `jjwt` từ chối khởi động với `WeakKeyException` |
| `APP_PASSWORD_RESET_TOKEN_SECRET` | |
| `POSTGRES_PASSWORD`, `REDIS_PASSWORD` | Dùng chung giữa compose và backend |
| `TURN_STATIC_AUTH_SECRET` | Dùng chung giữa coturn và backend — §5.4. `openssl rand -base64 32` là đủ |

> Chỉ **hai** secret đầu được code đọc trực tiếp qua `@Value`. Các biến `AUDIO_AES_MASTER_KEY`, `AUDIO_IP_HASH_SALT`, `AUDIO_STREAM_COOKIE_SECRET`, `PWB_AUDIO_COOKIE_SECRET`, `PWB_AUDIO_PLAYLIST_SIGNING_KEY`, `IAM_OUTBOX_ENCRYPTION_KEY`, `APP_JWT_REFRESH_SECRET` có trong `.env` hiện tại nhưng **không được tham chiếu ở bất kỳ đâu trong code** — tàn dư của các tính năng chưa triển khai (xem mục Định hướng phát triển trong README). Không cần đưa sang prod.

**Các biến bắt buộc khác, dễ bị đặt sai tên:**

| Biến | Giá trị | Hỏng thế nào nếu sai |
|---|---|---|
| `PWB_CORS_ALLOWED_ORIGINS` | `https://producerworkbench.online` | Tên cũ `APP_CORS_ALLOWED_ORIGINS` trong `.env` dev **không còn tác dụng**. Thiếu biến → backend không khởi động (đã fail-fast) |
| `SPRING_ELASTICSEARCH_URIS` | `http://elasticsearch:9200` | Xem cảnh báo ở §2 |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/pwb_db` | Không còn dùng tên `POSTGRES_URL` |
| `TURN_URLS` | `turn:turn.producerworkbench.online:3478?transport=udp,turn:...?transport=tcp` | Ngăn cách bằng dấu phẩy. Bỏ trống mà `TURN_ENABLED` vẫn `true` → backend **dừng ngay lúc khởi động** với thông báo chỉ đúng tên khóa thiếu |
| `GCP_TTS_CREDENTIALS_PATH` | `/run/secrets/gcp-tts.json` | **Đường dẫn trần, tuyệt đối không thêm tiền tố `file:`** — xem ô ngay dưới |

> **Đính chính (bản trước của tài liệu này ghi ngược).** Trước đây mục trên khẳng định tiền tố
> `file:` là bắt buộc. Sai — chính tiền tố đó mới là thứ làm hỏng.
>
> `GoogleTtsAdapter.openCredentials` **không** đi qua `ResourceLoader` của Spring. Nó tự phân giải:
> chỉ đặc biệt hoá `classpath:`, còn lại rơi xuống `new File(path)`, rồi mới thử `ClassPathResource`.
> Nên `file:/run/secrets/gcp-tts.json` không khớp nhánh nào — `new File("file:/run/...")` là đường dẫn
> *tương đối* mang tên chứa dấu hai chấm, không tồn tại — và fallback classpath phía sau ném
> `FileNotFoundException`.
>
> Adapter bắt exception đó, ghi **đúng một dòng WARN** rồi để `client = null`. Hệ quả: app vẫn
> `healthy`, Flyway vẫn chạy, mọi thứ khác bình thường, chỉ riêng TTS trả `AUDIO_016` cho mọi
> request. Đây là kiểu hỏng không lộ ra ở `/actuator/health` — muốn biết chắc thì tìm dòng
> `Failed to initialize Google TTS client` trong log lúc khởi động, hoặc gọi thử
> `POST /api/v1/voice-tags/tts/preview`.
>
> Ở môi trường dev cũng dùng đường dẫn trần, trỏ vào `secrets/gcp-tts.json` tương đối so với thư mục
> gốc repo (nhớ chạy backend kèm `-Dspring-boot.run.workingDirectory`).

> **Quyền file của secret — cạm bẫy cùng kiểu.** Bước 2 ở §6 yêu cầu `chmod 600`, nhưng container
> backend chạy bằng user **không phải root** (`USER pwb` trong `Backend/Dockerfile`). Docker Compose
> ở chế độ thường (không Swarm) chỉ *bind-mount* file secret, giữ nguyên owner/permission của host —
> các khoá `uid`/`gid`/`mode` chỉ có tác dụng trong Swarm. Nên file `600 root:root` trên host sẽ
> khiến tiến trình trong container đọc **không được**, và thất bại đó đi vào đúng nhánh `catch` ở
> trên: một dòng WARN, TTS tắt lặng lẽ.
>
> Dùng `chmod 644` cho `secrets/gcp-tts.json` (VPS chuyên dụng, chấp nhận được), hoặc `chown` file
> sang đúng uid của user `pwb` trong image. Kiểm chứng trực tiếp thay vì tin vào `ls -l` trên host:
>
> ```
> docker compose -f docker-compose.prod.yml exec backend head -c 40 /run/secrets/gcp-tts.json
> ```

---

## 6. Giai đoạn 3 — Quy trình deploy lần đầu

Thực hiện tuần tự, dừng lại ở bất kỳ bước nào không đạt.

| # | Bước | Cách xác nhận đạt |
|---|---|---|
| 1 | Push code đã sửa Giai đoạn 0 lên nhánh deploy | CI/local build ra jar thành công |
| 2 | Clone repo lên VPS, đặt `.env.prod` và `secrets/gcp-tts.json` | `.env.prod` quyền 600. Riêng `gcp-tts.json` phải để container (chạy non-root) đọc được — xem cảnh báo về quyền file ở §5, xác nhận bằng `exec … head -c 40` sau khi backend lên |
| 3 | `docker compose -f docker-compose.prod.yml build` | Cả 2 image build xong, không lỗi |
| 4 | Khởi động **chỉ** hạ tầng: `up -d postgres redis kafka elasticsearch` | `docker compose ps` — cả 4 `healthy` |
| 5 | Khởi động backend | Log thấy Flyway apply **đủ 24 migration** (V1→V304), không có lỗi validate |
| 6 | Kiểm tra `docker compose exec backend wget -qO- localhost:8080/actuator/health` | `{"status":"UP"}` |
| 7 | Khởi động frontend, rồi chạy `./nginx/init-letsencrypt.sh <email>` | `curl -I https://producerworkbench.online` trả 200, chứng chỉ hợp lệ |
| 8 | Khởi động coturn: `up -d coturn` | Log có `Relay ports initialization done`, **không** có dòng `WARNING` nào ngoài hai dòng `NO EXPLICIT ... ADDRESS(ES) ARE CONFIGURED` (đó là bình thường — coturn tự dò IP của host) |
| 9 | Xác minh TURN từ bên ngoài (§6.2) | Trickle ICE gom được candidate `relay` |
| 10 | Smoke test thủ công (§6.1) | Toàn bộ pass |

> Bước 5 là bước dễ trượt nhất: Flyway phải quét migration nằm trong các JAR module lồng bên trong fat jar (`iam`, `audio`, `liveroom` mỗi module một bộ). Cách này chạy tốt khi dev từ IDE nhưng cần xác nhận lại trên jar đóng gói. Nếu thiếu migration, `ddl-auto: validate` sẽ làm app fail ngay — tốt, vì lỗi lộ ra sớm chứ không âm thầm.

### 6.1 Smoke test bắt buộc trước khi coi là xong

1. Đăng ký tài khoản mới → **nhận được email OTP** (kiểm tra cả luồng Kafka → SMTP).
2. Xác thực OTP → đăng nhập → refresh token hoạt động (để tab mở >15 phút, xem có bị đá ra không).
3. Đăng nhập bằng Google.
4. Upload 1 file audio → job transcode chạy xong → phát được (xác nhận ffmpeg trong container và S3 đều OK).
5. Tạo live room từ máy A, join từ máy B → **chat hoạt động, playback sync hoạt động** (xác nhận WebSocket qua Nginx không bị đứt — đây là chỗ Nginx dễ sai nhất, xem §5.3).
6. Voice chat giữa 2 máy, **một trong hai dùng 4G chứ không dùng chung wifi** → nghe được. Cùng wifi thì kết nối đi thẳng và không chứng minh được gì về TURN; đây là toàn bộ mục đích của §6.2.
7. **Tìm kiếm bài hát bằng từ khóa tiếng Việt có dấu** → ra kết quả, **và** `logs backend | grep SEARCH.startup` phải có dòng `reachable` (xem bảng ở §2). Chỉ nhìn giao diện là không đủ: kết quả vẫn trả về từ Postgres kể cả khi ES chưa từng được gọi tới.
8. Mở DevTools → Console **không có lỗi CSP nào**, và tab Network cho thấy file audio tải từ đúng host S3 — nếu CSP chặn thì request không bao giờ rời khỏi trình duyệt và server không log gì cả.

### 6.2 Xác minh TURN — làm riêng, đừng tin vào "voice chat chạy được"

Voice chat chạy được **không** chứng minh TURN hoạt động: hai máy cùng wifi luôn đi thẳng, relay không bao giờ được đụng tới. Phải kiểm riêng, và kiểm **trước** buổi demo — đây là thành phần duy nhất trong toàn hệ thống không tự lộ lỗi ở bất kỳ log nào.

**Bước 1 — lấy credential thật.** Vào một live room, mở DevTools → Network → response của `GET /liveroom/rooms/<id>/rtc/config`. Phải thấy **hai** phần tử `iceServers`: STUN của Google, và mục TURN kèm `username` dạng `<số>:<uuid>`. Thiếu mục thứ hai nghĩa là backend không bật TURN — kiểm `TURN_ENABLED` / `TURN_URLS`, và nhớ là backend lẽ ra đã phải *dừng lúc khởi động* nếu bật mà thiếu cấu hình.

**Bước 2 — Trickle ICE.** Mở [webrtc.github.io/samples/src/content/peerconnection/trickle-ice](https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/), xóa hết server mặc định, điền `turn:turn.producerworkbench.online:3478?transport=udp` cùng username/credential vừa lấy, rồi *Gather candidates*.

| Kết quả | Nghĩa là |
|---|---|
| Có dòng loại `relay` | **Đạt.** TURN hoạt động đầu-cuối. |
| Chỉ có `host` / `srflx`, không có `relay` | Không tới được relay. Theo thứ tự nghi ngờ: dải `49160-49200/udp` chưa mở (kể cả firewall tầng cloud), rồi tới `TURN_STATIC_AUTH_SECRET` lệch giữa coturn và backend. |
| `401` trong log coturn | Secret lệch, hoặc credential đã hết hạn. |

**Bước 3 — ép dùng relay.** Sửa tạm `iceTransportPolicy: "relay"` trong [peer-connection-manager.ts:180](../Frontend/src/features/liveroom/lib/peer-connection-manager.ts:180), chạy lại live room: nếu voice vẫn thông thì **mọi** kết nối đều relay được, kể cả trường hợp xấu nhất. **Nhớ gỡ ra trước khi deploy bản cuối** — để nguyên thì mọi kết nối đều ăn băng thông VPS theo bảng ở §5.4.

---

## 7. Rủi ro đã biết và cách xử lý

| Rủi ro | Khả năng | Cách xử lý |
|---|---|---|
| **TURN dựng rồi nhưng không hoạt động** | Trung bình — và không tự lộ ra ở đâu | Ba nguyên nhân, theo thứ tự khả năng: dải `49160-49200/udp` chưa mở trên ufw *hoặc* trên firewall tầng cloud; `TURN_STATIC_AUTH_SECRET` lệch giữa coturn và backend; TTL credential quá ngắn nên hết hạn giữa phiên. Không cái nào tạo ra log ở backend. Bắt buộc chạy §6.2 trước ngày demo. |
| Băng thông VPS cạn vì mọi kết nối đều relay | Thấp | Xảy ra khi mục STUN bị bỏ khỏi `ice-servers`, hoặc `iceTransportPolicy: "relay"` của §6.2 bước 3 quên gỡ. ~30GB/giờ với mesh video 7 người — xem bảng §5.4. |
| Backend restart làm rớt hết live room | Chắc chắn xảy ra | Broker in-memory, không tránh được. Đừng deploy trong lúc demo; chốt code trước 1 ngày. |
| Flyway không tìm thấy migration trong fat jar | Trung bình | Phát hiện ngay ở bước 5 nhờ `ddl-auto: validate`. Nếu xảy ra, khai báo `spring.flyway.locations` liệt kê tường minh. |
| CSP chặn tài nguyên S3 | ~~Trung bình~~ **Không còn im lặng** | `next.config.ts` giờ **ném lỗi và dừng build** nếu không suy ra được origin nào cho storage, hoặc nếu `NEXT_PUBLIC_API_BASE_URL` không parse được. Không thể ra image hỏng nữa. Build lúc thiếu biến in đúng tên biến cần đặt và nhắc rằng chúng là *build arg* chứ không phải env của container. |
| Hết RAM khi ffmpeg render | Trung bình nếu VPS dưới 8GB | Đặt `mem_limit` cho backend, và `PWB_AUDIO_PROCESSOR_WORKING_DIR` trỏ vào volume có đủ dung lượng (cần ~2.5× kích thước file lớn nhất, tức ~500MB). |
| **ES chạy nhưng không bao giờ được gọi tới** vì thiếu `SPRING_ELASTICSEARCH_URIS` | ~~Cao~~ **Giờ phát hiện được bằng một lệnh grep** | Vẫn không tự lộ ra ở giao diện — bản chất fallback là như vậy và đó là thiết kế đúng. Nhưng backend giờ log `SEARCH.startup` lúc khởi động cho **cả hai** kết quả, kèm giá trị `uris` thật. Cách kiểm cũ (`grep` dòng fallback) là vô nghĩa: dòng đó ở mức DEBUG nên prod không bao giờ in. Xem §2. |
| ES bị OOM-kill, kéo container restart liên tục | Thấp | `mem_limit` nâng 1g → **1280m** sau khi đo dưới tải: cấu hình cũ chỉ còn dư 8.8%, và gần như không có phần nào thu hồi được. Không tái hiện được vụ OOM-kill nào — đây là nới biên độ, không phải vá lỗi đã thấy. Số đo đầy đủ ở §2. Backend không `depends_on` health của ES nên vòng restart không kéo theo gì, và giờ mỗi lần mất cụm để lại một dòng WARN. |
| Gmail SMTP bị rate limit | Thấp–trung bình | Đổi sang Brevo/Resend nếu demo có nhiều lượt đăng ký. |
| Mất dữ liệu Postgres | Thấp nhưng hậu quả nặng | §8. |

---

## 8. Vận hành tối thiểu (đủ cho đồ án)

- **Backup**: cron hằng ngày `pg_dump` ra file, giữ 7 bản, đồng bộ lên S3 (dùng lại bucket, prefix `backups/`). Quan trọng nhất là **thử restore một lần** — backup chưa từng restore không tính là backup.
- **Log**: `docker compose logs -f backend`. Đặt `max-size: 50m` / `max-file: 3` cho logging driver để log không ăn hết đĩa.
- **Cập nhật code**: `git pull` → `build` → `up -d` (downtime ~30–60s, chấp nhận được).
- **Rollback**: gắn tag Docker image theo commit SHA thay vì `latest`, để rollback chỉ là đổi tag và `up -d`. **Lưu ý**: Flyway không tự rollback migration — nếu bản mới có migration phá vỡ tương thích thì phải restore từ backup.
- **Theo dõi**: với quy mô demo, `docker stats` và `/actuator/health` là đủ. Chưa cần Prometheus/Grafana.

---

## 9. Thứ tự thực hiện đề nghị

| Thứ tự | Việc | Ước lượng | Chặn bởi |
|---|---|---|---|
| ~~1~~ | ~~Giai đoạn 0 — sửa 8 blocker trong code~~ | ✅ xong 2026-08-08 | — |
| ~~2~~ | ~~Viết Dockerfile frontend, compose prod, nginx.conf + certbot~~ | ✅ xong 2026-08-08 | — |
| ~~3~~ | ~~coturn + credential HMAC (§5.4)~~ | ✅ xong 2026-08-08 | — |
| 4 | Thuê VPS, trỏ DNS (gồm bản ghi `turn`), tạo bucket S3 + IAM + CORS | 2h | — |
| 5 | Mở port TURN trên ufw **và** firewall tầng cloud (§4.1) | 15ph | 4 |
| 6 | Soạn `.env.prod` + đặt `secrets/gcp-tts.json` theo §5.5 | 30ph | 4 |
| 7 | Deploy lần đầu + smoke test | 2–3h | 4, 5, 6 |
| 8 | **Xác minh TURN bằng Trickle ICE (§6.2)** | 30ph | 7 |
| 9 | Backup + cron + logging limit | 1h | 7 |

**Còn lại: khoảng 6–7 giờ làm việc, phần lớn là chờ DNS và thao tác trên console AWS.** Nên hoàn tất trước buổi demo tối thiểu 3 ngày, chừa thời gian cho các lỗi chỉ lộ ra trên môi trường thật (CORS, CSP, OAuth origin, TURN) — đây đều là nhóm lỗi không thể phát hiện được khi chạy localhost.

> Bước 8 đáng được tách riêng thay vì gộp vào smoke test. Nó là thứ duy nhất trong danh sách mà **kết quả "chạy được" không chứng minh được gì**: hai máy cùng wifi luôn nghe thấy nhau bất kể TURN sống hay chết, nên nếu chỉ test kiểu đó thì lỗi sẽ để dành tới đúng lúc có người vào phòng bằng 4G.
