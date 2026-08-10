# Hạ tầng — Đóng gói & triển khai

> `.github/workflows/{ci,deploy,integration-tests}.yml` · `docker-compose.prod.yml` · `nginx/nginx.conf` · `Backend/Dockerfile` · Flyway
> File cuối của bộ tài liệu. Bối cảnh: [bản đồ hệ thống](00-ban-do-he-thong.md)

---

## 1. Ràng buộc quyết định mọi thứ: một VPS nhỏ

Comment mở đầu `deploy.yml` nói thẳng con số:

> *The VPS is a 4 vCPU / 7.6GB box whose running stack already accounts for roughly 6GB, and its root disk is 19GB.*

Trên một chiếc máy như vậy phải chạy: Postgres, Redis, Kafka, coturn, backend, frontend, nginx, certbot. Gần như mọi quyết định triển khai dưới đây đều là hệ quả của việc **6GB trong 7.6GB đã có chủ**, và đĩa còn chưa tới 19GB.

Đó là lý do:

- Build trên GitHub runner, VPS **chỉ pull**
- Mọi container có `mem_limit`
- Dọn image sau mỗi lần deploy

> Sức ép RAM cũng chính là lý do **Elasticsearch bị gỡ hẳn ngày 2026-08-10**: nó chiếm `mem_limit: 1280m` — nhiều hơn cả Kafka — cho một tính năng mà Postgres đã trả lời được. Đọc [iam-06](iam-06-tim-kiem-nguoi-dung.md) để biết cái giá phải trả.

---

## 2. Ba workflow, chia theo tốc độ

| Workflow | Chạy khi | Nội dung |
|---|---|---|
| `ci.yml` | PR bất kỳ, push `develop`, và được `deploy.yml` gọi | Unit test backend + lint/test frontend |
| `integration-tests.yml` | Riêng | Testcontainers — quá chậm để đứng trước một lần deploy |
| `deploy.yml` | Push `main` | `test` → `build` → `deploy` → chờ health |

Phân chia này dựa trên một sự thật ở `Backend/pom.xml`: surefire **loại `**/*IT.java`**, nên `mvn test` là unit test thuần, không cần dựng gì. Các bộ Testcontainers chạy dưới failsafe ở workflow riêng.

### 2.1. Vì sao `main` vắng mặt trong trigger của `ci.yml`

Comment giải thích một cái bẫy CI thật:

> *A push there already runs this workflow as deploy.yml's `test` job, and a second standalone run lands in the same concurrency group below — same ref, so the one that starts second cancels the other. Which one loses is a race decided by the scheduler.*

Hậu quả nếu để `main` trong trigger: hai lần chạy cùng `concurrency group`, một cái huỷ cái kia. Nếu cái thua là cái **nằm trong Deploy** thì `build` và `deploy` bị bỏ qua qua `needs:`, và merge báo **"cancelled"** chứ không phải "failed" — production lặng lẽ ở lại phiên bản cũ.

Độ phủ không giảm: PR vào `main` chạy qua `pull_request`, push thẳng vào `main` chạy qua Deploy.

### 2.2. Cài ffmpeg trên runner

```yaml
- name: Install ffmpeg
  run: sudo apt-get update && sudo apt-get install -y ffmpeg
```

`AudioProbeService` test bằng **ffprobe thật**, không mock. Comment: binary không có sẵn trên mọi thế hệ runner, và khi thiếu thì test hỏng với thông báo về *tiến trình*, không phải về *gói phần mềm* — cài vô điều kiện rẻ hơn chẩn đoán sau.

Đây là hệ quả của một lựa chọn ở tầng test: kiểm thứ thật thay vì mock, đổi lấy việc môi trường CI phải giống môi trường chạy.

---

## 3. Build ở runner, VPS chỉ pull

```
GitHub runner (4 vCPU / 16GB, dùng xong vứt)
  ├─ build image backend  → ghcr.io/nguyenduchoangnam/pwb-backend:<tag>
  └─ build image frontend → ghcr.io/nguyenduchoangnam/pwb-frontend:<tag>
                              ↓
VPS: docker compose pull  (~700MB)
```

Build trên VPS nghĩa là Maven reactor và Next build tranh RAM với Postgres và Kafka, cộng vài GB layer trên một đĩa không có chỗ. Runner thì miễn phí và bị vứt sau đó.

Ảnh được **gắn tag theo commit**, và tag ấy ghi vào một file riêng trên VPS:

```bash
printf 'IMAGE_TAG=%s\n' "$TAG" > .env.deploy
```

Comment: để riêng ra file thay vì export vào shell, **để một lệnh `docker compose` chạy tay sau này cũng phân giải đúng ảnh đang chạy**. Không có nó, người đăng nhập vào máy gõ `docker compose up` sẽ vô tình kéo `main` mới nhất.

---

## 4. `git reset --hard` chứ không `git pull`

```bash
git fetch origin main
git checkout main
git reset --hard origin/main
```

Nhìn thô bạo, nhưng comment lập luận chặt:

> *Everything machine-specific lives in files git does not track — `.env.prod`, `secrets/`, `.env.deploy` — and reset leaves those alone… A pull refuses to run when any tracked file differs, including a bare mode change from a `chmod`, and that jams every later deploy until someone logs in and clears it by hand.*

Cây làm việc trên VPS **phải là bản sao chính xác của `main`, không hơn**. Một `chmod` vô tình sẽ làm `pull --ff-only` từ chối và **kẹt mọi lần deploy sau đó**. Cái giá là chỉnh sửa trực tiếp trên VPS bị vứt không báo — đúng kết cục cho một cây không được phép có chỉnh sửa riêng.

Vì sao vẫn cần mã nguồn trên VPS dù ảnh đã chứa ứng dụng: `nginx.conf`, `turnserver.conf` và chính `docker-compose.prod.yml` được **đọc từ đĩa**, không nằm trong ảnh.

---

## 5. Hai chi tiết dễ bỏ sót khi deploy

**Nginx không tự nạp lại config.**

```bash
$COMPOSE exec -T nginx nginx -s reload
```

Một file cấu hình bind-mount thay đổi trên đĩa **không** khiến compose tạo lại container. Không có dòng này, một sửa đổi `nginx.conf` sẽ lên tới máy và **không làm gì cả**. `nginx -s reload` không làm rớt kết nối.

**Không dùng `script_stop`.**

> *ssh-action v1 dropped that input and only warns that it is unrecognised, so relying on it would mean a script that silently runs past its first failure.*

Một input bị bỏ mà chỉ *cảnh báo* là thứ nguy hiểm hơn một input báo lỗi. `set -euo pipefail` ở đầu mỗi script là thứ thật sự dừng nó.

**Dọn ảnh cũ, giữ một tuần:**

```bash
docker image prune -af --filter "until=168h"
```

19GB đĩa và một cặp ảnh mới mỗi lần deploy. Cửa sổ 168 giờ giữ lại tag của tuần trước để **rollback** được.

---

## 6. Cổng chất lượng: chờ backend báo UP

```bash
for i in $(seq 1 30); do
  if $COMPOSE exec -T backend wget -q -O - http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "backend UP after $((i * 10))s"; exit 0
  fi
  sleep 10
done
echo "::error::backend did not report UP within 300s"
```

Comment nói rõ vì sao bước này tồn tại:

> *Flyway runs during startup, so a broken migration surfaces here rather than as a half-working site.*

Migration hỏng không làm `docker compose up` thất bại — container khởi động rồi chết, hoặc chạy nhưng không phục vụ được. Không có bước này thì deploy báo xanh trong khi site đã hỏng.

Ngân sách: Dockerfile cho `start_period` 90 giây, và vòng lặp chờ 300 giây — chừa chỗ cho một JVM khởi động lạnh trên máy đang tải.

---

## 7. Nginx: hai tên miền, một chỗ đặc biệt

```
producerworkbench.online       → frontend
api.producerworkbench.online   → backend
```

TLS: chỉ `TLSv1.2` và `TLSv1.3`, session cache dùng chung 10m, **tắt session tickets**. Chứng chỉ Let's Encrypt do container `certbot` gia hạn, với route `/.well-known/acme-challenge/` mở trên cổng 80.

`client_max_body_size 100m` — phải khớp với `spring.servlet.multipart.max-file-size` của backend. Lệch là nginx từ chối trước khi backend kịp trả lỗi tử tế.

**Chỗ đặc biệt là WebSocket**, và comment ghi lại hai bẫy:

```nginx
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}
```

Hard-code `Connection: upgrade` sẽ **hỏng mọi request keep-alive thường**. Map này bật `upgrade` khi đang bắt tay, `close` khi không.

Bẫy thứ hai nằm ở đường dẫn:

> *`/ws`, with no trailing slash. Written as `location /ws/` this block never sees the [URL the client actually uses]*

Client kết nối tới đúng `/ws`, không có dấu gạch cuối ([13 §3.1](13-realtime-stomp.md)). Một `location` tiền tố kết thúc bằng `/` sẽ không bao giờ khớp — và WebSocket im lặng rơi xuống route khác.

`/actuator/` có block riêng để giới hạn phạm vi phơi ra ngoài — khớp với `management.endpoints.web.exposure.include: health` bên backend, kèm comment cảnh báo rằng một `include: "*"` thêm vào để debug *phải trông có vẻ sai*.

---

## 8. Compose production

Tám service, mỗi cái có `restart: unless-stopped` và phần lớn có `healthcheck` + `mem_limit`:

| Service | Ảnh | `mem_limit` |
|---|---|---|
| postgres | `postgres:16-alpine` | — |
| redis | `redis:7.2-alpine` | — |
| kafka | `confluentinc/cp-kafka:7.6.0` | 1g |
| backend | `ghcr.io/…/pwb-backend:${IMAGE_TAG:-main}` | 1500m |
| coturn | `coturn/coturn:4.6-alpine` | 256m |
| frontend | `ghcr.io/…/pwb-frontend:${IMAGE_TAG:-main}` | — |
| nginx | `nginx:1.27-alpine` | — |
| certbot | `certbot/certbot` | — |

`${IMAGE_TAG:-main}` là chỗ file `.env.deploy` ở mục 3 được dùng.

**coturn có mặt** — nghĩa là hạ tầng TURN đã sẵn sàng trên production, dù `pwb.liveroom.rtc.turn.enabled` mặc định `false` ([liveroom-06 §6](liveroom-06-webrtc.md)). Hai thứ phải bật cùng nhau.

Một comment về bí mật:

> *Never baked into the image: `Backend/.dockerignore` excludes the credential from the build*

Credential Google (cho TTS) được gắn vào lúc chạy, không nằm trong ảnh. Ảnh đẩy lên GHCR thì ai kéo được cũng đọc được mọi layer.

---

## 9. Flyway: chạy lúc khởi động, chia dải theo module

Migration nằm trong `src/main/resources/db/migration` của **từng module**, gộp vào một classpath khi đóng gói:

| Dải | Chủ |
|---|---|
| `V1`–`V99` | `iam` |
| `V100`–`V199` | `shared-infrastructure` |
| `V200`–`V299` | `audio` |
| `V300`–`V399` | `liveroom` |

`spring.flyway.out-of-order: true` — bắt buộc, vì các module tiến độc lập nên hoàn toàn có chuyện `V304` đã chạy trước khi `V12` xuất hiện.

Migration chạy **trong lúc backend khởi động**, nên một migration hỏng lộ ra ở bước chờ health (mục 6). Không có bước migration riêng trước khi triển khai, và **không có đường lùi tự động** — Flyway ở đây không dùng `undo`.

---

## 10. Quyết định & đánh đổi

| Quyết định | Thay vì | Vì sao | Cái giá |
|---|---|---|---|
| Build ở runner, VPS chỉ pull | Build trên VPS | VPS không đủ RAM và đĩa | Phụ thuộc GHCR; deploy cần mạng tốt |
| Gắn tag theo commit + ghi `.env.deploy` | Dùng `:latest` | Rollback được; lệnh chạy tay khớp cái đang chạy | Thêm một file trạng thái trên VPS |
| `git reset --hard` | `git pull --ff-only` | Một `chmod` không kẹt mọi deploy sau | Sửa trực tiếp trên VPS bị vứt không báo |
| `main` vắng khỏi trigger `ci.yml` | Để cả `main` | Không có cuộc đua huỷ chéo làm deploy im lặng bỏ qua | Phải hiểu mới không "sửa lại" |
| Integration test workflow riêng | Chung với CI | Testcontainers quá chậm để chắn deploy | Deploy không chạy integration test |
| Cài ffmpeg trên runner | Mock ffprobe | Test kiểm thứ thật | Mỗi lần CI tốn thời gian cài |
| `nginx -s reload` sau deploy | Tin compose tạo lại container | Sửa `nginx.conf` mới có tác dụng | Một dòng dễ bị xoá khi dọn script |
| `set -euo pipefail` thay `script_stop` | Tin vào input của action | Input đã bị bỏ và chỉ cảnh báo | Phải nhớ viết ở đầu mỗi script |
| Giữ ảnh 168 giờ | Dọn sạch | Rollback được trong một tuần | Chiếm đĩa cho các bản không dùng |
| Chờ health 300 giây | Deploy xong là báo xanh | Migration hỏng lộ ra ngay | Deploy chậm hơn tới 5 phút |
| `mem_limit` cho service nặng | Để tự do | Một service không nuốt hết RAM của máy | Chạm trần là bị OOM kill |
| ES ngoài healthcheck | Tính như phụ thuộc bắt buộc | Container không bị thay vì thứ có dự phòng | Cluster chết trở nên vô hình |
| Credential không nằm trong ảnh | Bake vào | Ai kéo ảnh cũng đọc được layer | Phải quản lý file trên VPS |

---

## 11. Tự kiểm chứng

**Xem stack local so với production:**

```bash
docker ps --format "{{.Names}}\t{{.Image}}\t{{.Status}}"
```

Local có 4 container hạ tầng; production có thêm backend, frontend, nginx, certbot, coturn.

**Xem lịch sử migration đã chạy:**

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT installed_rank, version, description, success, execution_time FROM flyway_schema_history ORDER BY installed_rank;"
```

Thứ tự `installed_rank` **không** trùng thứ tự `version` — đó chính là `out-of-order: true` đang hoạt động.

**Xem dải version chia theo module:**

```bash
ls Backend/modules/*/src/main/resources/db/migration Backend/shared/*/src/main/resources/db/migration
```

**Kiểm chính cái mà cổng deploy kiểm:**

```bash
curl -s http://localhost:8080/actuator/health
```

```bash
curl -s http://localhost:8080/actuator/env
```

Cái thứ hai phải trả **404** — chỉ `health` được phơi ra.

**Xem cấu hình WebSocket của nginx** — hai bẫy ở mục 7:

```bash
grep -n "location /ws" -B4 -A12 nginx/nginx.conf
```

**Xem giới hạn bộ nhớ đã đặt:**

```bash
grep -n "mem_limit" -B6 docker-compose.prod.yml | grep -E "mem_limit|^[0-9]+-  [a-z]+:"
```

**Đọc lại lập luận trong chính workflow** — đây là nơi có mật độ comment giải thích cao nhất trong cả dự án:

```bash
grep -n "^\s*#" .github/workflows/deploy.yml | head -40
```

---

## 12. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| **Deploy có gián đoạn** | `compose up -d` tạo lại container; không có rolling update hay blue-green |
| Không có rollback tự động | Health thất bại thì báo lỗi và **để nguyên trạng thái hỏng**; phải vào tay đổi `IMAGE_TAG` |
| Migration không lùi được | Flyway không dùng `undo`; migration hỏng phải chữa tiến |
| Một máy, một instance | Không phải chỉ vì phần cứng — [broker STOMP, bộ đếm, sổ session và scheduler đều trong bộ nhớ](13-realtime-stomp.md) |
| Deploy không chạy integration test | Chỉ unit test chắn cửa — mục 2 |
| Không có staging | `main` đi thẳng ra production |
| Không có giám sát | Chỉ `/actuator/health`; không log tập trung, không cảnh báo, không dashboard |
| Không có sao lưu tự động trong compose | Volume Postgres không có job backup nào |
| Sửa trực tiếp trên VPS bị vứt im lặng | Hệ quả cố ý của `reset --hard` — mục 4 |
| TURN có hạ tầng nhưng tắt | `coturn` chạy mà `turn.enabled=false`; hai chỗ phải bật cùng nhau — mục 8 |

---

## 13. Hết bộ tài liệu

Đây là file cuối. Quay lại [bản đồ hệ thống](00-ban-do-he-thong.md) để xem toàn cảnh và mục lục đầy đủ.
