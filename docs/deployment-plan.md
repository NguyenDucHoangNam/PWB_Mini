# Kế hoạch Deploy — PWB MiNi

**Mục tiêu**: 1 VPS + Docker Compose, phục vụ demo/bảo vệ đồ án, lưu trữ audio trên AWS S3 thật.
**Ngày lập**: 2026-08-08

---

## 1. Ràng buộc quyết định hình dạng kế hoạch

Ba điều dưới đây không phải lựa chọn — chúng là hệ quả của code hiện tại và mọi quyết định deploy phải chấp nhận:

| Ràng buộc | Nguồn | Hệ quả |
|---|---|---|
| Backend **chỉ chạy được 1 instance** | `WebSocketConfig.configureMessageBroker` dùng `enableSimpleBroker` (broker in-memory) | Không scale ngang, không blue-green 2 container cùng lúc. Deploy = downtime ngắn. |
| Không có TURN server | `LiveroomConfig.Rtc` chỉ khai báo STUN của Google | Voice chat WebRTC sẽ fail với người sau NAT đối xứng (4G, mạng công ty, một số wifi trường). |
| Chỉ có adapter S3 | Chỉ tồn tại `S3StorageServiceImpl`, không có local storage impl | Bắt buộc phải có bucket S3 hoạt động thì upload/stream mới chạy. |

**Về TURN cho buổi demo**: rủi ro thật nhưng chấp nhận được nếu tất cả người demo ngồi cùng một mạng LAN/wifi. Nếu có người tham gia từ 4G hoặc mạng khác, phải dựng coturn (xem §7).

---

## 2. Kiến trúc triển khai đích

```
                    Internet
                       │
                  [ DNS A record ]
        producerworkbench.online  ─┐
    api.producerworkbench.online  ─┤
                       │        │
              ┌────────▼────────▼────────┐
              │  Nginx (443) + certbot   │   ← container
              └────┬─────────────────┬───┘
                   │                 │
     frontend ───┘                 └─── api  (kèm /ws upgrade)
                   │                              │
           ┌───────▼────────┐          ┌──────────▼──────────┐
           │  frontend      │          │  backend            │
           │  Next.js :3000 │──────────▶  Spring Boot :8080  │
           │  (standalone)  │   SSR    │  1 instance duy nhất│
           └────────────────┘          └──────────┬──────────┘
                                                  │
              ┌─────────┬──────────┬─────────┼─────────┬──────────┐
              │         │          │         │         │          │
        ┌─────▼────┐ ┌──▼────┐ ┌───▼────┐ ┌──▼────────▼──┐  ┌────▼────┐
        │ Postgres │ │ Redis │ │ Kafka  │ │Elasticsearch │  │ AWS S3  │
        │  :5432   │ │ :6379 │ │ :29092 │ │    :9200     │  │ (ngoài) │
        └──────────┘ └───────┘ └────────┘ └──────────────┘  └─────────┘
                 (chỉ nội bộ, KHÔNG publish port ra host)
```

**MongoDB đã bị gỡ khỏi cả hai compose** (2026-08-08). Code không có một dependency mongo nào — không import, không annotation, không cấu hình; chat của live room nằm ở Postgres (`V303__create_liveroom_chat.sql`). Nó chạy không phục vụ gì và tốn ~300MB RAM.

**Giữ lại Elasticsearch** (`PWB_SEARCH_ENABLED=true`, mặc định). Tắt đi sẽ mất xếp hạng theo độ liên quan, autocomplete và tìm kiếm tiếng Việt có dấu — truy vấn rơi về Postgres thuần. Với VPS 8GB thì 1GB cho ES là trong tầm, và đây là tính năng demo được.

> ⚠️ Bắt buộc đặt `SPRING_ELASTICSEARCH_URIS=http://elasticsearch:9200`. Giá trị mặc định trong `application.yml` là `http://localhost:9200` — bên trong container backend, `localhost` là chính nó, nên thiếu biến này thì **mọi truy vấn ES đều fail rồi âm thầm rơi về Postgres**. Search vẫn "chạy", chỉ là không bao giờ dùng tới ES. Xác minh bằng log `SEARCH.* fallback to database` — nếu thấy dòng này ở mọi lần tìm kiếm là đã dính lỗi.

**Cấu hình VPS đề nghị**: 4 vCPU / 8GB RAM / 80GB SSD.

| Thành phần | RAM dự kiến |
|---|---|
| Backend (JVM, `MaxRAMPercentage=75`, limit 1.5GB) | ~1.5 GB |
| Kafka (KRaft, heap mặc định) | ~1.0 GB |
| Elasticsearch (`mem_limit: 1g`, heap 512m) | ~1.0 GB |
| Postgres | ~0.5 GB |
| Frontend Next.js | ~0.4 GB |
| Redis + Nginx + certbot | ~0.2 GB |
| Dự phòng cho ffmpeg (job transcode audio) | ~1.0 GB |
| **Tổng** | **~5.6 GB** |

**8GB giờ là mức tối thiểu thực tế, không còn là mức thoải mái** — 4GB không đủ. Nếu buộc phải dùng VPS nhỏ hơn, ES là thứ đầu tiên nên cắt (`PWB_SEARCH_ENABLED=false`), vì nó là thành phần duy nhất đã có sẵn đường lui hoàn chỉnh.

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
- Firewall (`ufw`): chỉ mở **22, 80, 443**. Tất cả port DB/Kafka/Redis **không** publish ra host.
- Cài Docker Engine + Docker Compose plugin.

### 4.2 Domain & DNS

Domain đã chốt: **`producerworkbench.online`**.

| Bản ghi | Trỏ về | Dùng cho |
|---|---|---|
| `@` (A) | IP VPS | `https://producerworkbench.online` — frontend |
| `api` (A) | IP VPS | `https://api.producerworkbench.online` — backend, gồm cả WebSocket |

Tương ứng trong `.env.prod`: `FRONTEND_DOMAIN=producerworkbench.online`, `API_DOMAIN=api.producerworkbench.online`.

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

### 5.4 `.env.prod` trên VPS
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

> Chỉ **hai** secret đầu được code đọc. Các biến `AUDIO_AES_MASTER_KEY`, `AUDIO_IP_HASH_SALT`, `AUDIO_STREAM_COOKIE_SECRET`, `PWB_AUDIO_COOKIE_SECRET`, `PWB_AUDIO_PLAYLIST_SIGNING_KEY`, `IAM_OUTBOX_ENCRYPTION_KEY`, `APP_JWT_REFRESH_SECRET` có trong `.env` hiện tại nhưng **không được tham chiếu ở bất kỳ đâu trong code** — tàn dư của các tính năng chưa triển khai (xem mục Định hướng phát triển trong README). Không cần đưa sang prod.

**Các biến bắt buộc khác, dễ bị đặt sai tên:**

| Biến | Giá trị | Hỏng thế nào nếu sai |
|---|---|---|
| `PWB_CORS_ALLOWED_ORIGINS` | `https://producerworkbench.online` | Tên cũ `APP_CORS_ALLOWED_ORIGINS` trong `.env` dev **không còn tác dụng**. Thiếu biến → backend không khởi động (đã fail-fast) |
| `SPRING_ELASTICSEARCH_URIS` | `http://elasticsearch:9200` | Xem cảnh báo ở §2 |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/pwb_db` | Không còn dùng tên `POSTGRES_URL` |
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
| 8 | Smoke test thủ công (§6.1) | Toàn bộ pass |

> Bước 5 là bước dễ trượt nhất: Flyway phải quét migration nằm trong các JAR module lồng bên trong fat jar (`iam`, `audio`, `liveroom` mỗi module một bộ). Cách này chạy tốt khi dev từ IDE nhưng cần xác nhận lại trên jar đóng gói. Nếu thiếu migration, `ddl-auto: validate` sẽ làm app fail ngay — tốt, vì lỗi lộ ra sớm chứ không âm thầm.

### 6.1 Smoke test bắt buộc trước khi coi là xong

1. Đăng ký tài khoản mới → **nhận được email OTP** (kiểm tra cả luồng Kafka → SMTP).
2. Xác thực OTP → đăng nhập → refresh token hoạt động (để tab mở >15 phút, xem có bị đá ra không).
3. Đăng nhập bằng Google.
4. Upload 1 file audio → job transcode chạy xong → phát được (xác nhận ffmpeg trong container và S3 đều OK).
5. Tạo live room từ máy A, join từ máy B → **chat hoạt động, playback sync hoạt động** (xác nhận WebSocket qua Nginx không bị đứt — đây là chỗ Nginx dễ sai nhất, xem §5.3).
6. Voice chat giữa 2 máy → nghe được (đây là bước dễ fail nhất, xem §7).
7. **Tìm kiếm bài hát bằng từ khóa tiếng Việt có dấu** → ra kết quả, và log **không** xuất hiện `SEARCH.* fallback to database`. Nếu có dòng đó nghĩa là ES không được dùng (thường do thiếu `SPRING_ELASTICSEARCH_URIS`, xem §2) — kết quả vẫn trả về nên lỗi này không tự lộ ra ở giao diện.
8. Mở DevTools → Console **không có lỗi CSP nào**.

---

## 7. Rủi ro đã biết và cách xử lý

| Rủi ro | Khả năng | Cách xử lý |
|---|---|---|
| **Voice chat fail** vì không có TURN | Cao nếu người demo ở khác mạng | Dựng `coturn` trong compose, thêm vào `pwb.liveroom.rtc.ice-servers` kèm credential. Nếu không kịp: yêu cầu tất cả ngồi chung wifi và **test trước buổi demo ít nhất 1 ngày**. |
| Backend restart làm rớt hết live room | Chắc chắn xảy ra | Broker in-memory, không tránh được. Đừng deploy trong lúc demo; chốt code trước 1 ngày. |
| Flyway không tìm thấy migration trong fat jar | Trung bình | Phát hiện ngay ở bước 5 nhờ `ddl-auto: validate`. Nếu xảy ra, khai báo `spring.flyway.locations` liệt kê tường minh. |
| CSP chặn tài nguyên S3 | Trung bình | `next.config.ts` sinh `media-src`/`connect-src` từ `NEXT_PUBLIC_STORAGE_*` **lúc build**. Set thiếu ⇒ trình duyệt chặn phát nhạc. Kiểm tra ở smoke test bước 7. |
| Hết RAM khi ffmpeg render | Trung bình nếu VPS dưới 8GB | Đặt `mem_limit` cho backend, và `PWB_AUDIO_PROCESSOR_WORKING_DIR` trỏ vào volume có đủ dung lượng (cần ~2.5× kích thước file lớn nhất, tức ~500MB). |
| **ES chạy nhưng không bao giờ được gọi tới** vì thiếu `SPRING_ELASTICSEARCH_URIS` | Cao — đây là lỗi mặc định nếu quên | Không tự lộ ra: search vẫn trả kết quả từ Postgres. Chỉ phát hiện được qua log. Kiểm tra ở smoke test bước 7. |
| ES bị OOM-kill, kéo container restart liên tục | Thấp | Giữ `mem_limit: 1g` **và** `ES_JAVA_OPTS=-Xms512m -Xmx512m` — heap chỉ là một nửa nhu cầu của ES, Lucene cần thêm bộ nhớ ngoài heap. Health check của ES đã tắt sẵn nên nó không kéo theo backend. |
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
| 3 | Thuê VPS, trỏ DNS, tạo bucket S3 + IAM + CORS | 2h | — |
| 4 | Soạn `.env.prod` + đặt `secrets/gcp-tts.json` theo §5.4 | 30ph | 3 |
| 5 | Deploy lần đầu + smoke test | 2–3h | 3, 4 |
| 6 | Backup + cron + logging limit | 1h | 5 |
| 7 | *(nếu cần)* coturn cho TURN | 2h | 5 |

**Còn lại: khoảng 5–7 giờ làm việc, phần lớn là chờ DNS và thao tác trên console AWS.** Nên hoàn tất trước buổi demo tối thiểu 3 ngày, chừa thời gian cho các lỗi chỉ lộ ra trên môi trường thật (CORS, CSP, OAuth origin, TURN) — đây đều là nhóm lỗi không thể phát hiện được khi chạy localhost.
