# CI/CD — GitHub Actions

**Ngày lập**: 2026-08-08
**Liên quan**: [vps-deployment-guide.md](vps-deployment-guide.md) (dựng máy lần đầu) · [deployment-plan.md](deployment-plan.md) (lý do đằng sau kiến trúc)

---

## 1. Hình dạng pipeline

```
push → develop
     │
     ├─ ci.yml ──────────── backend: mvn test        (unit, ~2-3 ph)
     │                      frontend: lint + vitest  (~2 ph)
     │                            │
     │                     tất cả pass?
     │                            │
     ├─ build ──────────── GitHub runner (4 vCPU / 16GB)
     │                      docker build ×2 → push ghcr.io
     │                      tag: sha-<commit>  +  develop
     │                            │
     └─ deploy ─────────── ssh → VPS
                            git pull  (compose, nginx.conf, turnserver.conf)
                            docker compose pull backend frontend
                            docker compose up -d --no-build
                            nginx -s reload
                            chờ /actuator/health = UP  (tối đa 300s)
                            docker image prune
```

**Điểm quan trọng nhất: build ở runner, VPS chỉ pull.**

VPS là máy 4 vCPU / 7.6 GB RAM / **19 GB đĩa**, mà stack đang chạy đã chiếm ~6 GB RAM. Build ngay trên đó nghĩa là đặt một Maven reactor và một `next build` cạnh tranh gigabyte cuối cùng với Elasticsearch và Kafka, cộng thêm ~4.5 GB tầng build trên cái đĩa vốn không dư. Runner của GitHub có 4 vCPU / 16 GB và bị hủy sau khi xong, nên VPS chỉ còn phải xử lý một lần pull ~700 MB.

Đổi lại, [docker-compose.prod.yml](../docker-compose.prod.yml) giờ khai cả `image:` lẫn `build:` cho `backend` và `frontend`. Hai chiều đều dùng được: `docker compose build` gắn đúng tên image đó, nên nếu có lúc cần build tay trên VPS thì `up -d` vẫn tìm thấy.

---

## 2. Ba workflow

| File | Chạy khi | Chặn deploy? |
|---|---|---|
| [ci.yml](../.github/workflows/ci.yml) | mọi PR, mọi push lên `develop`/`main` | **Có** — `deploy.yml` gọi lại nó qua `workflow_call` |
| [deploy.yml](../.github/workflows/deploy.yml) | push lên `develop`, hoặc bấm tay | — |
| [integration-tests.yml](../.github/workflows/integration-tests.yml) | 02:00 UTC hằng ngày, hoặc bấm tay | **Không** — cố ý, xem §2.1 |

### 2.1 Vì sao integration test không chặn deploy

[Backend/pom.xml](../Backend/pom.xml) tách sẵn hai tầng: surefire loại trừ `**/*IT.java`, failsafe chỉ chạy đúng những file đó. Nghĩa là:

- `mvn test` — thuần unit, không cần dựng gì, vài phút. **Đây là cổng chặn.**
- `mvn verify` — dựng Postgres, Kafka, Redis, Elasticsearch thật qua Testcontainers. Hàng chục phút.

Đặt cái thứ hai trước mỗi lần deploy sẽ dẫn tới một trong hai kết cục: hoặc ngồi chờ, hoặc học cách bỏ qua nó. Với repo private, nó còn ăn vào quỹ Actions minutes mà chính deploy cần (§7). Chạy hằng đêm bắt được đúng những lỗi đó sớm hơn buổi demo một ngày, mà không nằm chắn đường.

---

## 3. Cần tạo gì trên GitHub

Repo → **Settings → Secrets and variables → Actions**.

### Secrets (tab *Secrets*)

| Tên | Giá trị |
|---|---|
| `VPS_HOST` | `52.63.23.58` — hoặc `producerworkbench.online` sau khi DNS xong |
| `VPS_SSH_KEY` | **toàn bộ nội dung** private key dành riêng cho CI (§4), gồm cả dòng `-----BEGIN...` và `-----END...` |

Không cần secret cho GHCR: `GITHUB_TOKEN` được cấp tự động cho mỗi lần chạy, và deploy.yml chuyển nó sang VPS để `docker login` trong đúng thời gian job sống.

### Variables (tab *Variables*, KHÔNG phải Secrets)

| Tên | Giá trị ví dụ |
|---|---|
| `FRONTEND_DOMAIN` | `producerworkbench.online` |
| `API_DOMAIN` | `api.producerworkbench.online` |
| `GOOGLE_CLIENT_ID` | `....apps.googleusercontent.com` |
| `TURNSTILE_SITE_KEY` | site key của domain prod |
| `STORAGE_BUCKET` | tên bucket |
| `STORAGE_REGION` | `ap-southeast-2` ⚠️ *(không phải `ap-southeast-1` như `.env.prod.example`)* |
| `STORAGE_PUBLIC_URL_PREFIX` | `https://<bucket>.s3.ap-southeast-2.amazonaws.com` |

> **Vì sao là Variables chứ không phải Secrets.** Bảy giá trị này đều là `NEXT_PUBLIC_*` — chúng được `next build` **nhúng thẳng vào bundle client**, tức là ai mở DevTools cũng đọc được. Đánh dấu chúng là secret không giấu được gì, chỉ khiến Actions thay chúng bằng `***` trong log build, và bạn sẽ mất thời gian gấp đôi khi cần biết vì sao CSP chặn một origin.
>
> **Hệ quả cần nhớ**: đổi bất kỳ giá trị nào ở trên ⇒ phải **build lại image frontend** (đẩy một commit mới hoặc bấm *Run workflow*). Restart container không có tác dụng.

### Environment (tùy chọn)

Job `deploy` khai `environment: production`. Nếu tạo environment tên `production` ở **Settings → Environments**, bạn có thể thêm *Required reviewers* để mỗi lần deploy phải bấm duyệt. Hữu ích quanh ngày demo — [deployment-plan.md](deployment-plan.md) đã cảnh báo restart backend làm rớt mọi live room đang mở.

---

## 4. SSH key riêng cho CI

**Đừng dùng lại `nam.pem`.** Key đó là quyền truy cập cá nhân của bạn; key cho CI cần thu hồi được độc lập mà không khóa chính mình ra ngoài.

Trên máy Windows:

```powershell
ssh-keygen -t ed25519 -f gha_deploy -N '""' -C "github-actions"
```

Đưa public key lên VPS:

```powershell
$pub = Get-Content gha_deploy.pub
ssh -i nam.pem ubuntu@52.63.23.58 "echo '$pub' >> ~/.ssh/authorized_keys"
```

Dán **private key** (`gha_deploy`, file không có đuôi `.pub`) vào secret `VPS_SSH_KEY`, rồi xóa nó khỏi máy. Kiểm tra key chạy được trước khi tin vào workflow:

```powershell
ssh -i gha_deploy ubuntu@52.63.23.58 "docker compose version"
```

### ⚠️ Đánh đổi: port 22 phải mở cho GitHub runner

Runner của GitHub dùng dải IP rất rộng và thay đổi liên tục. Danh sách có công bố (`https://api.github.com/meta`, khóa `actions`) nhưng gồm hàng nghìn CIDR — vượt xa giới hạn mặc định 60 rule mỗi Security Group. Thực tế nghĩa là **`22/tcp` phải mở `0.0.0.0/0`**, yếu hơn so với khóa vào riêng IP nhà bạn — xem bảng Security Group ở [vps-deployment-guide.md §0](vps-deployment-guide.md).

Giảm thiểu — nên làm cả ba, tốn khoảng 5 phút:

```bash
sudo sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin no/'               /etc/ssh/sshd_config
sudo systemctl restart ssh

sudo apt-get install -y fail2ban
sudo systemctl enable --now fail2ban
```

Chỉ còn xác thực bằng key, không đăng nhập root, và IP nào dò mật khẩu sẽ bị chặn. Nếu sau này muốn đóng hẳn port 22, hai đường thoát là self-hosted runner đặt trong VPC, hoặc AWS SSM (agent đã cài sẵn và đang chạy trên máy, chỉ thiếu IAM instance profile).

---

## 5. Bootstrap lần đầu

CI chỉ tiếp quản được sau khi VPS đã ở trạng thái chạy được. Thứ tự:

**Bước 1 — dựng VPS** theo [vps-deployment-guide.md](vps-deployment-guide.md) §1 đến §4: Elastic IP, DNS, swap, Docker, clone repo bằng **deploy key** (CI sẽ chạy `git pull` nên phải là cách 2 trong §3.1), `.env.prod` và `secrets/gcp-tts.json`. Security Group và bucket S3 đã xong rồi (§0.2, §0.3).

**Bước 2 — tạo secrets/variables** ở §3 và §4 phía trên.

**Bước 3 — chạy workflow Deploy một lần để có image.**

> Lần chạy đầu tiên này **job `deploy` sẽ đỏ**, và đó là bình thường: VPS chưa có chứng chỉ TLS nên nginx chưa khởi động được. Ta chỉ cần job `build` đẩy image lên GHCR. Chuyện này chỉ xảy ra đúng một lần.

**Bước 4 — bootstrap trên VPS** (không build gì cả, chỉ pull):

```bash
cd ~/PWB_MiNi

# GHCR là private theo repo. Cần PAT có quyền read:packages cho lần login thủ công này;
# các lần deploy sau CI tự cấp token ngắn hạn nên không cần lưu gì lâu dài trên máy.
echo <PAT> | docker login ghcr.io -u NguyenDucHoangNam --password-stdin

echo "IMAGE_TAG=develop" > .env.deploy
export COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env.prod --env-file .env.deploy"

$COMPOSE pull backend frontend
$COMPOSE up -d postgres redis kafka elasticsearch     # chờ cả 4 (healthy)
$COMPOSE up -d backend
$COMPOSE logs -f backend                              # Flyway apply đủ 24 migration
$COMPOSE up -d frontend

./nginx/init-letsencrypt.sh you@example.com           # STAGING=1 trước
$COMPOSE up -d coturn
```

**Bước 5 — từ đây trở đi**, mỗi lần push lên `develop` là một lần deploy tự động.

> Chú ý biến `$COMPOSE` giờ có **hai** `--env-file`. Mọi lệnh compose thủ công trên VPS phải dùng đúng dạng này, nếu không `IMAGE_TAG` rơi về `develop` và bạn sẽ thao tác nhầm phiên bản.

---

## 6. Rollback

Deploy nào cũng ghi lại đúng commit đang chạy, nên quay lui là đổi một dòng — **không cần GitHub, không cần build lại**:

```bash
cd ~/PWB_MiNi
docker compose -f docker-compose.prod.yml --env-file .env.prod --env-file .env.deploy ps --format '{{.Image}}'
# → ghcr.io/nguyenduchoangnam/pwb-backend:sha-1b94583   ← phiên bản đang chạy

echo "IMAGE_TAG=sha-be03b58" > .env.deploy              # tag muốn quay về
docker compose -f docker-compose.prod.yml --env-file .env.prod --env-file .env.deploy up -d --no-build
```

Image cũ còn nằm trên máy 7 ngày (`docker image prune --filter until=168h` trong workflow), sau đó phải pull lại từ GHCR — vẫn được, chỉ chậm hơn.

> ⚠️ **Flyway không tự rollback migration.** Nếu bản mới có migration phá vỡ tương thích thì đổi tag image là chưa đủ — phải restore Postgres từ backup. Đây là lý do backup hằng ngày ở [vps-deployment-guide.md §8](vps-deployment-guide.md) không phải thứ để làm sau.

---

## 7. Chi phí Actions minutes

Repo private → quỹ miễn phí **2.000 phút/tháng** (gói Free).

| Workflow | Mỗi lần chạy | Ghi chú |
|---|---|---|
| `ci.yml` | ~5 ph | chạy cả ở PR lẫn ở push |
| `deploy.yml` (build + deploy) | ~8–12 ph | có cache layer; lần đầu lâu hơn nhiều |
| `integration-tests.yml` | ~15–25 ph | ×30 lần/tháng ≈ 450–750 ph |

Nhân hằng đêm vào thì integration test là khoản tốn nhất. Nếu sắp chạm trần, đổi `cron` sang chạy tuần (`0 2 * * 1`) trước khi cắt thứ gì khác — nó là workflow duy nhất đụng tới Postgres, Kafka và Elasticsearch thật.

---

## 8. CI này **không** bắt được gì

Quan trọng không kém phần nó bắt được. Không lỗi nào dưới đây lộ ra ở `mvn test`, `vitest`, hay `/actuator/health` — tất cả chỉ xuất hiện trên môi trường thật:

| Lỗi | Chỉ phát hiện bằng |
|---|---|
| TURN không hoạt động (thường là `TURN_EXTERNAL_IP` sai) | Trickle ICE — [vps-deployment-guide.md §7](vps-deployment-guide.md), đọc **cột địa chỉ** của dòng `relay` |
| CORS của bucket S3 thiếu | thử upload thật trên trình duyệt |
| CSP chặn origin S3 | DevTools Console |
| Google OAuth origin chưa khai báo | bấm thử nút đăng nhập Google |
| `SPRING_ELASTICSEARCH_URIS` sai | `grep SEARCH.startup` — workflow đã in dòng này ra sau mỗi lần deploy, nhưng **không** coi là lỗi |

Bốn cái đầu đều là kiểu hỏng im lặng: hệ thống báo khỏe mạnh, log sạch, chỉ tính năng là không chạy. Chúng nằm trong smoke test thủ công ở [deployment-plan.md §6.1](deployment-plan.md), và smoke test đó vẫn phải làm bằng tay ít nhất một lần trước buổi demo.

---

## 9. Thay đổi kèm theo khi dựng CI/CD này

Ghi lại để sau này đọc git log không phải đoán:

| Thay đổi | Lý do |
|---|---|
| [Frontend/vitest.config.ts](../Frontend/vitest.config.ts) — thêm `test.env` | `login-form.test.tsx` fail vì container Google chỉ render khi `NEXT_PUBLIC_GOOGLE_CLIENT_ID` có giá trị, mà vitest không có. Test đúng, môi trường test thiếu cấu hình |
| [login-form.tsx](../Frontend/src/features/auth/components/login-form.tsx) — thêm `data-google-button-container` | Test vốn đã tìm attribute này trước rồi mới rơi về class Tailwind. Có nó thì assertion không còn phụ thuộc vào tên class utility |
| [Frontend/package.json](../Frontend/package.json) — thêm script `test` | Chưa từng có, dù `vitest.config.ts` đã tồn tại. Test chỉ chạy được bằng `npx vitest` |
| [OutboxJpaWriterIT.java](../Backend/shared/shared-infrastructure/src/test/java/com/pwb/infra/outbox/persistence/writer/OutboxJpaWriterIT.java) — `truncatedTo(MICROS)` | Postgres `timestamp` lưu microsecond nên cắt phần nano; cận dưới chưa cắt nằm cao hơn giá trị đã ghi tới 999ns. Fail hay không tùy độ phân giải đồng hồ của máy chạy test |
| [docker-compose.prod.yml](../docker-compose.prod.yml) — thêm `image:` | Để VPS pull được image CI build sẵn, mà vẫn build tay được khi cần |
| [.gitignore](../.gitignore) — thêm `.env.deploy` | Trạng thái riêng của từng máy, không phải secret |
