# Runbook Deploy — VPS `52.63.23.58`

**Đối tượng**: đúng một máy cụ thể, đã SSH vào kiểm tra ngày 2026-08-08.
**Quan hệ với [deployment-plan.md](deployment-plan.md)**: bản kế hoạch nói *tại sao*; file này nói *gõ gì, theo thứ tự nào, trên máy này*. Chỗ nào máy thật khác giả định của bản kế hoạch đều được đánh dấu ⚠️.

---

## 0. Hiện trạng máy — đã đo, không phải giả định

```
ssh -i nam.pem ubuntu@52.63.23.58
```

| Hạng mục | Giá trị thật | Kế hoạch giả định | |
|---|---|---|---|
| Nhà cung cấp | AWS EC2 `c5ad.xlarge`, region `ap-southeast-2` (Sydney) | "Vultr/DO/Hetzner" | |
| OS | **Ubuntu 26.04 LTS** (`resolute`), kernel 7.0.0-1006-aws | Ubuntu 24.04 LTS | ⚠️ |
| CPU | 4 vCPU AMD EPYC 7R32 | 4 vCPU | ✅ |
| RAM | 7.6 GiB — swap ban đầu **= 0**, đã bật 4 GB ở §2.2 | 8 GB | ⚠️ |
| Đĩa root | `/dev/nvme0n1p1` — **18.9 GB**; trống 16 GB lúc đầu, còn ~12 GB sau khi cài Docker + swap | 80 GB | ⚠️ |
| Đĩa phụ | `nvme1n1` **139.7 GB chưa format, chưa mount** — instance store | — | ⚠️ |
| cgroup | v2 (`cgroup2fs`) — `mem_limit` trong compose có hiệu lực | — | ✅ |
| Phần mềm đã có | `git 2.53.0` | — | |
| Phần mềm ban đầu **chưa có** | docker, docker compose, java, node, npm, nginx | — | ⚠️ |
| Đã cài (§2.3) | `docker-ce 29.7.2`, `docker compose v5.4.0`, `containerd 2.3.3` — repo chính chủ, codename `resolute` | — | ✅ |
| `ufw` | inactive | — | |
| Cổng đang listen | chỉ `22` (sshd) + DNS nội bộ | — | ✅ sạch |
| Security Group | `launch-wizard-1` — **mở toang, mọi cổng từ `0.0.0.0/0`** | "chỉ mở port cần" | ⚠️ |
| Quyền AWS Console | **không có** — máy của một người bạn, mình chỉ có SSH bằng `nam.pem` | có toàn quyền | ⚠️ |
| Loại instance | `on-demand` (không phải Spot) — AWS không thu hồi giữa chừng | — | ✅ |
| IP private (`ens5`) | `172.31.6.161` | — | |
| Instance | `i-00a614cc3293e5eff`, AZ `ap-southeast-2a` | — | |
| User | `ubuntu`, có `sudo`, chưa có group `docker` | — | |
| Kết nối ra ngoài | OK (Docker Hub, GitHub đều tới được) | — | ✅ |

**Kết luận: máy sạch hoàn toàn, chưa cài gì.** Toàn bộ phần dưới là cài từ đầu.

### 0.1 Ubuntu 26.04 — đã kiểm tra, KHÔNG phải vấn đề

Bản kế hoạch ghi Ubuntu 24.04. Máy này là 26.04, nên câu hỏi đầu tiên là repo Docker chính chủ có bản cho codename `resolute` chưa. **Có** — đã kiểm tra trực tiếp:

```
https://download.docker.com/linux/ubuntu/dists/resolute/Release  →  HTTP 200
```

Ngoài ra repo Ubuntu cũng có sẵn `docker.io 29.1.3` làm đường lui. Cứ cài theo cách chuẩn ở §2.3, không cần workaround nào.

### 0.2 Security Group — UDP đã thông, nhưng vì nó mở toang

Bản đầu của runbook ghi UDP bị chặn. **Đã thông**: `tcpdump` trên `ens5` bắt được **24/24** gói probe gửi từ ngoài vào cả `3478` lẫn `49170`.

Nhưng lý do không phải "ai đó đã thêm đúng hai rule UDP". Thử 6 cổng chẳng liên quan gì — `5432`, `6379`, `9200`, `9092`, `8080`, `12345` — **cả sáu đều trả `ConnectionRefused`**, tức gói tin tới được máy. Security Group `launch-wizard-1` đang là **All traffic từ `0.0.0.0/0`**.

> 🔴 **Thứ duy nhất che Postgres/Redis/Kafka/Elasticsearch lúc này là việc [docker-compose.prod.yml](../docker-compose.prod.yml) không publish port của chúng.** Thêm một dòng `ports: - "5432:5432"` để debug là database ra thẳng internet, và `ufw` **không** cứu được vì Docker đi vòng qua nó (§2.5). Đừng bao giờ publish port của service nội bộ, kể cả "chỉ để xem tí".
>
> Siết lại còn TCP `22/80/443/3478` + UDP `3478` và `49160-49200` là việc trong AWS Console, tức phải nhờ chủ tài khoản (§1.1).

Phép thử UDP vẫn đáng giữ lại vì triệu chứng khi nó hỏng thì im lặng tuyệt đối (§7). Trên VPS:

```bash
sudo timeout 30 tcpdump -n -i ens5 'udp and (port 3478 or portrange 49160-49200)'
```

Trên máy Windows, PowerShell (không cần cài gì):

```powershell
$c = New-Object System.Net.Sockets.UdpClient
$b = [Text.Encoding]::ASCII.GetBytes("probe")
1..12 | ForEach-Object {
  [void]$c.Send($b, $b.Length, "52.63.23.58", 3478)
  [void]$c.Send($b, $b.Length, "52.63.23.58", 49170)
  Start-Sleep -Milliseconds 400
}
$c.Close()
```

Phải thấy các dòng `IP ... > 172.31.6.161.3478: UDP`. Không thấy dòng nào là rule đã bị gỡ mất — dải `49160-49200` phải khớp `min-port`/`max-port` trong [docker/coturn/turnserver.conf](../docker/coturn/turnserver.conf).

### 0.3 Bucket S3 — đã tạo đúng, đã đo lại

| Kiểm | Kết quả |
|---|---|
| Bucket `pwb-prod-bucket` tồn tại | ✅ `x-amz-bucket-region: ap-southeast-2` — **cùng region với EC2**, không phát sinh phí liên vùng |
| Block public access | ✅ GET ẩn danh trả `403` (code dùng presigned URL) |
| CORS | ✅ preflight ẩn danh trả đúng `Allow-Origin: https://producerworkbench.online`, `Allow-Methods: GET, PUT`, `Expose: ETag, Content-Range, Content-Length` |

Kiểm lại bất cứ lúc nào, không cần credential:

```bash
curl -s -i -X OPTIONS "https://pwb-prod-bucket.s3.ap-southeast-2.amazonaws.com/probe-object" \
  -H "Origin: https://producerworkbench.online" -H "Access-Control-Request-Method: PUT"
```

Còn **IAM user** và **lifecycle rule** thì không kiểm ẩn danh được — xem §1.2.

### 0.4 DNS — đã trỏ đúng, đã đo lại

Domain mua ở **iNET** (`portal.inet.vn`), DNS quản lý bằng **OneShield** — nameserver `hoalu.vclouddns.com` / `ninhbinh.vclouddns.com`. Thêm bản ghi ở nút **Quản lý OneShield**, không phải ở trang chi tiết domain.

| Type | Name | Value | TTL | Bảo vệ |
|---|---|---|---|---|
| A | `@` | `52.63.23.58` | 5 phút | **Tắt** |
| A | `api` | `52.63.23.58` | 5 phút | **Tắt** |
| A | `turn` | `52.63.23.58` | 5 phút | **Tắt** |

> 🔴 **Cột "Bảo vệ" phải Tắt cả ba.** Bật lên là OneShield đứng làm proxy và **ẩn IP gốc** — DNS trả về IP của họ chứ không phải VPS. `turn` chết hẳn (TURN chạy UDP, proxy HTTP không đẩy UDP qua được), `api` mất WebSocket và rate limiting đếm nhầm IP, `@` làm certbot fail vì Let's Encrypt không gọi thẳng được vào cổng 80. Nhìn cột "Bảo vệ" trong bảng là chưa đủ — phải `dig` ra đúng `52.63.23.58` mới chắc.

Đã xác nhận qua **hai** resolver độc lập (`8.8.8.8` và `1.1.1.1`), và **không có bản ghi AAAA** nào — đúng, vì máy chỉ có IPv4; có AAAA thì trình duyệt thử IPv6 trước rồi mới lùi về, chậm vô cớ.

```bash
for h in producerworkbench.online api.producerworkbench.online turn.producerworkbench.online; do
  echo -n "$h → "; dig +short A "$h" @8.8.8.8
done
```

**Giữ TTL 5 phút, đừng nâng lên 3600** — không có Elastic IP thì TTL thấp chính là cần gạt phục hồi khi IP đổi (§1.1).

Bản ghi `turn` là tùy chọn về mặt kỹ thuật (không có TLS nên không cần chứng chỉ khớp tên), nhưng `.env.prod` đang dùng nên cứ tạo cho khớp. **Đừng thêm `turn` vào danh sách domain xin chứng chỉ** ở §6.1.

---

## 1. Hai việc còn lại — và cả hai đều nằm trong AWS Console

**Máy này là của một người bạn.** Mình có `nam.pem` và `sudo` trên máy, nhưng **không có quyền vào AWS Console**. Nên hai mục dưới đây không tự làm được — phải nhờ, hoặc chấp nhận sống thiếu.

Tin tốt: **không mục nào chặn việc deploy.** Toàn bộ §2 → §8 làm được ngay.

> Security Group, bucket S3 và DNS từng là blocker ở đây. **Cả ba đã xong và đã đo lại** — chuyển sang §0.2, §0.3 và §0.4 cùng lệnh kiểm lại.

### 1.1 🟡 Elastic IP — không có cũng deploy được

IP hiện tại `52.63.23.58`. Nếu đây là public IP mặc định (không phải Elastic IP) thì nó đổi mỗi lần stop/start instance. **Không kiểm từ xa được**: IMDS trả cùng một giá trị ở `meta-data/public-ipv4` cho cả hai loại, reverse DNS cũng giống hệt.

**IP chỉ đổi khi có người bấm Stop rồi Start trong console.** Reboot, kernel update, container chết, host bảo trì — đều không đổi. Máy lại là `on-demand` chứ không phải Spot nên AWS không tự tắt.

Và nếu nó đổi thật thì **không mất gì**: EBS root còn nguyên nên database, volume, image đều còn; chứng chỉ TLS gắn với tên miền chứ không gắn IP. Chỉ phải sửa lại 4 chỗ, tất cả đều trong tầm tay:

| Việc | Thời gian |
|---|---|
| Sửa 3 bản ghi A ở OneShield | 2 ph + 5 ph chờ TTL |
| Sửa nửa public của `TURN_EXTERNAL_IP`, tạo lại coturn (§4) | 3 ph |
| Sửa secret `VPS_HOST` trên GitHub ([cicd.md §3](cicd.md)) | 2 ph |
| SSH bằng IP mới | — |

Rủi ro thật không phải "IP đổi thì khổ", mà là **nó hỏng im lặng và email retire instance của AWS gửi cho chủ tài khoản, không phải mình**. Ba biện pháp thay thế, rẻ hơn nhiều so với chờ:

1. **Nhắn chủ máy một câu**: *"đừng Stop instance, reboot thì thoải mái; bắt buộc phải stop thì báo trước."* Hiệu quả nhất, tốn 10 giây.
2. **Giữ TTL 5 phút vĩnh viễn** (§0.4) — đó là cần gạt phục hồi.
3. **Kiểm 10 giây trước mỗi lần demo** — hai dòng phải khớp nhau:

```bash
echo "DNS  : $(dig +short A producerworkbench.online @8.8.8.8)"
echo "Thuc : $(curl -s -H "X-aws-ec2-metadata-token: $(curl -sX PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')" http://169.254.169.254/latest/meta-data/public-ipv4)"
```

Nếu nhờ được: EC2 Console → Elastic IPs, tìm `52.63.23.58`; không có thì **Allocate → Associate** vào instance `i-00a614cc3293e5eff`. ⚠️ **Làm xong IP sẽ đổi** — AWS cấp một địa chỉ mới, phải chạy đúng bảng 4 việc ở trên. Elastic IP gắn vào máy đang chạy thì miễn phí; chỉ tính tiền khi allocate mà để không.

### 1.2 🟡 S3 — còn IAM user và lifecycle rule

Bucket, region và CORS **đã xong và đã đo lại** (§0.3). Hai thứ còn lại không kiểm ẩn danh được nên phải tự xác nhận trong console, theo đúng [deployment-plan.md §4.3](deployment-plan.md):

- **IAM user riêng** chỉ với `s3:GetObject/PutObject/DeleteObject/AbortMultipartUpload` trên đúng `pwb-prod-bucket` — access key của user này là hai biến `STORAGE_S3_ACCESS_KEY`/`STORAGE_S3_SECRET_KEY` trong `.env.prod`.
- **Lifecycle rule** xóa multipart dở dang sau 7 ngày. Thiếu nó thì mỗi lần upload lỗi giữa chừng để lại một phần dữ liệu vẫn tính tiền mà không hiện ra khi list bucket.

---

## 2. Chuẩn bị máy

### 2.1 🔴 Đĩa: 16 GB trống là KHÔNG đủ thoải mái

Bản kế hoạch tính cho 80 GB. Máy này có 18.9 GB, còn trống 16 GB. Ước lượng thực tế cho một lần `build` sạch:

| Hạng mục | Ước lượng |
|---|---|
| `elasticsearch:8.12.0` | ~1.3 GB |
| `confluentinc/cp-kafka:7.6.0` | ~1.4 GB |
| `postgres:16-alpine` + redis + nginx + coturn + certbot | ~0.6 GB |
| Tầng build backend (`maven:3.9-eclipse-temurin-21` + toàn bộ `.m2`) | ~2.5 GB |
| Tầng build frontend (`node:22-alpine` + `node_modules` + `.next`) | ~2 GB |
| Image cuối backend (JRE + ffmpeg + jar) + frontend | ~0.7 GB |
| Volume dữ liệu (postgres, ES, kafka) lúc demo | ~1 GB |
| **Tổng** | **~9.5 GB, đỉnh trong lúc build cao hơn** |

Vừa đủ, nhưng không còn chỗ cho một lần build lại thất bại giữa chừng.

> **Trên máy này thì bảng trên phần lớn không áp dụng.** Cách làm đã chốt là build trên GitHub Actions rồi VPS chỉ pull (§5), nên toàn bộ ~4.5 GB tầng build không bao giờ chạm tới đĩa này. 16 GB trống là **đủ** — đừng nhờ chủ máy tăng đĩa cho việc này. Chỉ đọc tiếp nếu buộc phải build ngay trên VPS.

Chọn một trong hai:

**Cách A — tăng đĩa.** Cần AWS Console nên phải nhờ chủ tài khoản (§1.1): EC2 Console → Volumes → chọn volume root → Actions → Modify volume → **50 GiB**. Resize online, không cần stop máy. Sau đó trên VPS:

```bash
sudo growpart /dev/nvme0n1 1
sudo resize2fs /dev/nvme0n1p1     # nếu là xfs: sudo xfs_growfs /
df -h /                            # phải thấy ~50G
```

**Cách B — giữ 20 GB, dọn sau mỗi lần build.** Chấp nhận được nhưng phải kỷ luật:

```bash
docker builder prune -af      # xóa cache build sau mỗi lần build xong
docker image prune -f
```

> **Về đĩa `nvme1n1` 139.7 GB**: đó là **instance store** của `c5ad` — SSD gắn trực tiếp, rất nhanh, nhưng **toàn bộ dữ liệu bị xóa sạch mỗi lần stop/start instance** (reboot thì không sao). Tuyệt đối **KHÔNG** đặt `/var/lib/docker` hay volume Postgres lên đó: một lần stop/start là mất sạch database lẫn image. Nó chỉ hợp với swap và thư mục tạm — xem §2.2.

### 2.2 🔴 Không có swap

7.6 GiB RAM, mà [deployment-plan.md §2](deployment-plan.md) tính tổng steady-state ~5.95 GB. Phần dư ~1.6 GB sẽ bị `pnpm build` và `mvn package` ăn hết trong lúc build. Không có swap thì đụng trần là **OOM-killer**, không phải chậm đi.

```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
# giảm xu hướng swap khi vẫn còn RAM — swap ở đây là lưới an toàn, không phải bộ nhớ chính
sudo sysctl vm.swappiness=10
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swappiness.conf
free -h                            # phải thấy Swap: 4.0Gi
```

> Nếu chọn **Cách B** ở §2.1 (không tăng đĩa), đặt swapfile lên instance store thay vì root để khỏi tốn 4 GB trong 16 GB ít ỏi. Swap vốn là thứ mất được nên tính ephemeral không thành vấn đề — nhưng **bắt buộc** dùng `nofail` trong fstab, không thì một lần stop/start là máy không boot lên được:
>
> ```bash
> sudo mkfs.ext4 -F /dev/nvme1n1
> sudo mkdir -p /mnt/scratch
> sudo mount /dev/nvme1n1 /mnt/scratch
> echo '/dev/nvme1n1 /mnt/scratch ext4 defaults,nofail 0 2' | sudo tee -a /etc/fstab
> sudo fallocate -l 4G /mnt/scratch/swapfile && sudo chmod 600 /mnt/scratch/swapfile
> sudo mkswap /mnt/scratch/swapfile && sudo swapon /mnt/scratch/swapfile
> ```
>
> Cách này **không** thêm được vào fstab dạng swap thông thường (đĩa bị format lại sau stop/start thì file biến mất). Chấp nhận bật tay sau mỗi lần stop, hoặc dùng Cách A cho gọn.

### 2.3 Cài Docker

```bash
sudo apt-get update && sudo apt-get upgrade -y
sudo apt-get install -y ca-certificates curl

sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

sudo usermod -aG docker ubuntu
```

`$VERSION_CODENAME` trên máy này là `resolute`, và repo có bản cho nó (§0.1).

> ⚠️ **Group `docker` chỉ có hiệu lực ở phiên đăng nhập mới.** Thoát ra rồi SSH lại, đừng chỉ chạy `newgrp`:
> ```bash
> exit
> ssh -i nam.pem ubuntu@52.63.23.58
> docker compose version     # chạy được, không cần sudo
> ```

### 2.4 Giới hạn log ngay từ đầu

Làm **trước** khi khởi động container đầu tiên. Container đã tạo rồi thì không nhận cấu hình này cho đến khi bị tạo lại — và với đĩa 19 GB, log không giới hạn là một cách thầm lặng để làm đầy đĩa giữa buổi demo.

```bash
sudo tee /etc/docker/daemon.json > /dev/null <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "50m", "max-file": "3" }
}
EOF
sudo systemctl restart docker
```

### 2.5 `ufw` — trên AWS thì đừng, và đây là lý do

[deployment-plan.md §4.1](deployment-plan.md) bảo bật `ufw`. Lời khuyên đó viết cho VPS **không có** firewall tầng cloud. Máy này có Security Group, và trên Docker thì `ufw` vừa thừa vừa nguy hiểm theo một kiểu bất đối xứng rất khó đoán:

| Service | Đường đi của gói tin | `ufw` có chặn được không? |
|---|---|---|
| `nginx` (publish `80:80`, `443:443`) | DNAT ở `nat/PREROUTING` → chain `FORWARD`/`DOCKER` | **Không.** Docker đi vòng qua `ufw` — rule `deny` không có tác dụng gì |
| `coturn` (`network_mode: host`) | thẳng vào chain `INPUT` | **Có.** |

Nghĩa là bật `ufw` mà quên mở port TURN sẽ **giết đúng coturn** trong khi nginx vẫn chạy ngon lành — một kiểu hỏng im lặng y hệt việc quên mở UDP trên Security Group, và `tcpdump` ở §0.2 sẽ **vẫn thấy gói** nên không phát hiện ra được (nó bắt trước chain `INPUT`).

**Khuyến nghị: để `ufw` inactive, quản lý toàn bộ ở Security Group.** Chỉ có nginx và coturn mở ra ngoài; postgres/redis/kafka/elasticsearch đều không publish port (xem đầu file [docker-compose.prod.yml](../docker-compose.prod.yml)).

Nếu vẫn muốn bật `ufw` để phòng thủ nhiều lớp thì **mở SSH trước tiên**, không thì tự khóa mình ra ngoài:

```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp && sudo ufw allow 443/tcp
sudo ufw allow 3478/udp && sudo ufw allow 3478/tcp && sudo ufw allow 49160:49200/udp
sudo ufw enable
```

---

## 3. Đưa code và secret lên máy

### 3.1 Clone repo

⚠️ Repo `https://github.com/NguyenDucHoangNam/PWB_Mini` trả **HTTP 404** khi gọi từ VPS → **repo đang ở chế độ private**, `git clone` trần sẽ fail. Chọn một cách:

**Cách 1 — Personal Access Token** (nhanh nhất). Tạo token ở GitHub → Settings → Developer settings → Personal access tokens → Fine-grained, quyền `Contents: Read` trên đúng repo này:

```bash
git clone https://<TOKEN>@github.com/NguyenDucHoangNam/PWB_Mini.git ~/PWB_MiNi
cd ~/PWB_MiNi && git checkout main
# xóa token khỏi remote để nó không nằm lại trong .git/config
git remote set-url origin https://github.com/NguyenDucHoangNam/PWB_Mini.git
```

**Cách 2 — Deploy key** (sạch hơn nếu định `git pull` nhiều lần). Tạo key trên VPS, dán public key vào repo → Settings → Deploy keys:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/deploy_key -N ""
cat ~/.ssh/deploy_key.pub          # dán vào GitHub Deploy keys (read-only là đủ)
echo -e "Host github.com\n  IdentityFile ~/.ssh/deploy_key" >> ~/.ssh/config
git clone git@github.com:NguyenDucHoangNam/PWB_Mini.git ~/PWB_MiNi
cd ~/PWB_MiNi && git checkout main
```

### 3.2 Chép hai file không nằm trong git

`.gitignore` chặn cả `.env.prod` (dòng 23) lẫn cả thư mục `secrets/` (dòng 29) — đúng như mong muốn, nhưng nghĩa là **hai file này phải chép tay**. Từ máy Windows, tại thư mục repo:

```powershell
# Thư mục secrets chưa tồn tại trên VPS
ssh -i nam.pem ubuntu@52.63.23.58 "mkdir -p ~/PWB_MiNi/secrets"

scp -i nam.pem .env.prod              ubuntu@52.63.23.58:~/PWB_MiNi/.env.prod
scp -i nam.pem secrets/gcp-tts.json   ubuntu@52.63.23.58:~/PWB_MiNi/secrets/gcp-tts.json
```

Đặt quyền trên VPS — **hai file này quyền khác nhau, và đó là cố ý**:

```bash
cd ~/PWB_MiNi
chmod 600 .env.prod
chmod 644 secrets/gcp-tts.json
```

> **Vì sao `gcp-tts.json` là 644 chứ không phải 600.** Container backend chạy bằng user không phải root (`USER pwb` trong [Backend/Dockerfile](../Backend/Dockerfile#L53)). Docker Compose ở chế độ thường chỉ *bind-mount* file secret và **giữ nguyên owner/permission của host** — các khóa `uid`/`gid`/`mode` chỉ có tác dụng trong Swarm. File `600 ubuntu:ubuntu` trên host sẽ khiến tiến trình trong container đọc **không được**.
>
> Và thất bại đó đi vào một nhánh `catch` chỉ ghi **đúng một dòng WARN**: app vẫn `healthy`, Flyway vẫn chạy, mọi thứ bình thường, riêng TTS trả `AUDIO_016` cho mọi request. Xác minh bằng chính container chứ đừng tin `ls -l` trên host (§6, bước 6).

---

## 4. Soạn `.env.prod`

> `.env.prod` **đã soạn xong trên máy Windows** và cả bốn giá trị riêng của máy này đã được kiểm lại ngày 2026-08-08. Đường đi bình thường là `scp` nó lên theo §3.2 rồi nhảy thẳng xuống hai lệnh xác nhận ở cuối mục này. Phần dưới giữ lại cho trường hợp phải dựng lại từ đầu, và vì mục `TURN_EXTERNAL_IP` giải thích một cái bẫy đã thực sự cắn một lần.

Nếu chưa có sẵn thì bắt đầu từ template, ngay trên VPS:

```bash
cd ~/PWB_MiNi
cp .env.prod.example .env.prod && chmod 600 .env.prod
```

Sinh secret **mới**, mỗi biến một lần chạy riêng — đừng tái dùng giá trị của dev:

```bash
openssl rand -base64 48   # APP_JWT_SECRET
openssl rand -base64 48   # APP_PASSWORD_RESET_TOKEN_SECRET
openssl rand -base64 24   # POSTGRES_PASSWORD
openssl rand -base64 24   # REDIS_PASSWORD
openssl rand -base64 32   # TURN_STATIC_AUTH_SECRET
```

### Bốn giá trị phải sửa khác template cho máy này

| Biến | Giá trị cho VPS này | Vì sao khác template |
|---|---|---|
| `STORAGE_S3_REGION` | `ap-southeast-2` | Template ghi `ap-southeast-1`; EC2 ở Sydney — xem §0.3 |
| `STORAGE_PUBLIC_URL_PREFIX` | `https://<bucket>.s3.ap-southeast-2.amazonaws.com` | Phải khớp region ở trên, **và nó là build arg** — sai thì CSP chặn phát nhạc |
| `TURN_URLS` | `turn:turn.producerworkbench.online:3478?transport=udp,turn:turn.producerworkbench.online:3478?transport=tcp` | Chỉ hợp lệ sau khi bản ghi `turn` ở §1.2 đã propagate |
| `TURN_EXTERNAL_IP` | `52.63.23.58/172.31.6.161` — **đã đo**, xác nhận lại bằng lệnh dưới | Template chỉ có placeholder. **Đây là máy EC2**, xem giải thích bên dưới |

#### `TURN_EXTERNAL_IP` — cái bẫy đã cắn một lần

coturn chạy `network_mode: host`, nên nếu không được chỉ định nó đọc địa chỉ trên `ens5` — trên EC2 là địa chỉ **private** `172.31.x.x` — rồi đưa **chính địa chỉ đó** cho trình duyệt làm relay candidate. Im lặng tuyệt đối: allocation thành công, credential hợp lệ, firewall đã mở, log coturn sạch — chỉ là media được gửi tới một địa chỉ không tồn tại ngoài VPC.

Điểm khiến nó khó hơn mọi lỗi khác trong file này: **Trickle ICE vẫn hiện dòng `relay`**, nên §7 sẽ báo "Đạt" trong khi thực tế không ai nghe được ai. Phải nhìn vào cột địa chỉ của dòng đó.

> 🔴 **Nửa private không phải trang trí — nó là khóa tra cứu.** Ngày 2026-08-08 file này đang mang giá trị `52.63.23.58/172.31.0.10`, tức nửa sau bị copy nguyên từ dòng ví dụ trong template. Máy thật là `172.31.6.161`. coturn dùng nửa private để tìm xem interface nào cần map: không khớp thì không map, và nó rơi thẳng về hành vi quảng bá địa chỉ private ở trên — **đúng cái mà biến này sinh ra để ngăn**. Compose cũng không đỡ được, vì `${TURN_EXTERNAL_IP:?}` chỉ kiểm biến có tồn tại chứ không kiểm nội dung. Đã sửa. Sau **mỗi lần stop/start instance** phải đọc lại cả hai nửa.

Lấy giá trị, chạy trên VPS (IMDSv2 nên phải xin token trước):

```bash
TOKEN=$(curl -sX PUT http://169.254.169.254/latest/api/token \
  -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')
PUB=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4)
PRIV=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/local-ipv4)
echo "TURN_EXTERNAL_IP=$PUB/$PRIV"
```

`$PUB` phải ra `52.63.23.58`. Nếu ra IP khác thì §1.1 chưa xong — gắn Elastic IP trước, vì giá trị này **phải sửa lại mỗi lần public IP đổi**.

[docker-compose.prod.yml](../docker-compose.prod.yml#L212) khai biến này bằng cú pháp `${TURN_EXTERNAL_IP:?...}`: thiếu nó thì **mọi** lệnh compose dừng ngay với thông báo rõ ràng, không chỉ riêng `up -d coturn`. Cố ý như vậy — kiểu hỏng này không đáng được phép âm thầm trôi qua tới lúc có người thật vào phòng.

Phần còn lại giữ nguyên template. Kiểm tra nhanh không còn placeholder nào sót:

```bash
grep -n '^[^#]*<' .env.prod        # không được ra dòng nào
```

Phải bỏ qua dòng comment: bản thân template có vài dòng chú thích chứa `<...>` (kể cả dòng hướng dẫn ở đầu file), nên `grep -n '<'` trần sẽ luôn ra vài dòng và bạn sẽ quen mắt bỏ qua nó.

> Ba biến `SPRING_ELASTICSEARCH_URIS`, `PWB_CORS_ALLOWED_ORIGINS`, `GCP_TTS_CREDENTIALS_PATH` **không** đặt trong `.env.prod` — [docker-compose.prod.yml](../docker-compose.prod.yml#L123-L145) tự sinh chúng từ `FRONTEND_DOMAIN`/`API_DOMAIN`. Đặt tay vào `.env.prod` chỉ tạo ra hai nguồn sự thật.

Xác nhận compose đọc được hết trước khi build:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod config > /dev/null && echo OK
```

Lệnh này nổ ngay nếu thiếu biến — rẻ hơn nhiều so với phát hiện sau 15 phút build.

---

## 5. Lấy image

**Khuyến nghị: đừng build trên máy này.** GitHub Actions build ở runner 4 vCPU / 16 GB rồi đẩy lên GHCR, VPS chỉ pull ~700 MB — xem [cicd.md](cicd.md). Lý do nằm ở đúng hai ràng buộc của máy này: 16 GB đĩa trống không đủ thoải mái cho ~4.5 GB tầng build (§2.1), và một Maven reactor cạnh tranh RAM với Elasticsearch cùng Kafka trên 7.6 GB là cách nhanh nhất để gặp OOM-killer.

Sau khi CI đã đẩy image lên lần đầu:

```bash
cd ~/PWB_MiNi
echo <PAT có quyền read:packages> | docker login ghcr.io -u NguyenDucHoangNam --password-stdin
echo "IMAGE_TAG=main" > .env.deploy

export COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env.prod --env-file .env.deploy"
$COMPOSE pull backend frontend
```

> ⚠️ Từ đây trở đi biến `$COMPOSE` có **hai** `--env-file`. Thiếu `.env.deploy` thì `IMAGE_TAG` rơi về tag `main` và bạn sẽ thao tác nhầm phiên bản mà không nhận ra.

<details>
<summary><b>Nếu cần build ngay trên VPS</b> (chưa dựng CI, hoặc muốn thử nhanh)</summary>

Vẫn làm được — [docker-compose.prod.yml](../docker-compose.prod.yml) giữ nguyên `build:` bên cạnh `image:`, và `docker compose build` gắn đúng tên image đó nên `up -d` sau đó vẫn tìm thấy.

**Build tuần tự, đừng build song song.** `docker compose build` mặc định chạy các service song song, mà backend (Maven) và frontend (Next.js) đều ngốn RAM. Trên 7.6 GB, hai cái cùng lúc sẽ khiến OOM-killer giết một trong hai và để lại lỗi không liên quan gì tới nguyên nhân thật.

```bash
$COMPOSE build backend      # ~8–15 phút, phần lớn là tải dependency Maven
$COMPOSE build frontend     # ~5–10 phút
docker builder prune -af    # bắt buộc nếu chọn Cách B ở §2.1
```

Theo dõi ở phiên SSH thứ hai: `free -h -s 5` và `df -h /`.

</details>

> **Nhớ**: mọi biến `NEXT_PUBLIC_*` được **nhúng vào bundle lúc build**. Đổi domain, đổi bucket, đổi region ⇒ **phải build lại `frontend`** (đẩy commit mới, hoặc bấm *Run workflow*), `restart` không có tác dụng gì.

---

## 6. Khởi động lần đầu — theo thứ tự, dừng ở bước nào không đạt

| # | Lệnh | Đạt khi |
|---|---|---|
| 1 | `$COMPOSE up -d postgres redis kafka elasticsearch` | `$COMPOSE ps` — cả 4 `(healthy)`. Kafka lâu nhất, ~40–60s |
| 2 | `$COMPOSE up -d --no-build backend` | xem bước 3–6 |
| 3 | `$COMPOSE logs -f backend` | Flyway apply **đủ 24 migration** (V1→V304), không lỗi validate |
| 4 | `$COMPOSE exec backend wget -qO- localhost:8080/actuator/health` | `{"status":"UP"}` |
| 5 | `$COMPOSE logs backend \| grep SEARCH.startup` | `Elasticsearch reachable: uris=http://elasticsearch:9200` |
| 6 | `$COMPOSE exec backend head -c 40 /run/secrets/gcp-tts.json` | in ra JSON. `Permission denied` → quay lại §3.2 |
| 7 | `$COMPOSE up -d --no-build frontend` | `$COMPOSE ps` thấy `pwb-frontend` running |
| 8 | `./nginx/init-letsencrypt.sh <email>` | xem §6.1 |
| 9 | `$COMPOSE up -d coturn` | log có `Relay ports initialization done` |
| 10 | Xác minh TURN (§7) | Trickle ICE gom được candidate `relay` |
| 11 | Smoke test ([plan §6.1](deployment-plan.md)) | toàn bộ pass |

Bước 3 là bước dễ trượt nhất: Flyway phải quét migration nằm trong JAR của từng module lồng trong fat jar. Nếu thiếu, `ddl-auto: validate` làm app fail ngay — tốt, vì lỗi lộ ra sớm. Cách chữa: khai `spring.flyway.locations` liệt kê tường minh.

Bước 5 không bỏ qua được bằng cách "thử tìm kiếm trên giao diện": search vẫn trả kết quả từ Postgres kể cả khi ES chưa từng được gọi tới. Chi tiết ở [deployment-plan.md §2](deployment-plan.md).

### 6.1 Chứng chỉ TLS

**Chạy thử ở staging trước.** Let's Encrypt chỉ cho 5 chứng chỉ/domain/tuần; sai vài lần là khóa tới hết tuần.

```bash
STAGING=1 ./nginx/init-letsencrypt.sh you@example.com
```

Chạy trót lọt thì xóa chứng chỉ staging và làm thật:

```bash
$COMPOSE run --rm --entrypoint "rm -rf /etc/letsencrypt/live /etc/letsencrypt/archive /etc/letsencrypt/renewal" certbot
./nginx/init-letsencrypt.sh you@example.com

curl -I https://producerworkbench.online         # 200, chứng chỉ hợp lệ
curl -I https://api.producerworkbench.online
```

Script tự xử lý thế bí "nginx cần chứng chỉ để khởi động, certbot cần nginx để xin chứng chỉ" bằng một chứng chỉ tự ký tạm — xem [nginx/init-letsencrypt.sh](../nginx/init-letsencrypt.sh).

Gia hạn về sau **tự động**, không cần cron trên host: service `certbot` thử `renew` mỗi 12 giờ, `nginx` reload mỗi 6 giờ.

### 6.2 Ghi chú về log coturn

Hai dòng `WARNING: NO EXPLICIT ... ADDRESS(ES) ARE CONFIGURED` là **bình thường** — coturn tự dò IP host. Mọi `WARNING` khác thì không.

Hai dòng đó nói về `listening-ip`/`relay-ip`, **không** phải `external-ip`, nên chúng vẫn xuất hiện kể cả khi §4 đã làm đúng. Đừng dùng chúng để kết luận `TURN_EXTERNAL_IP` có hiệu lực hay chưa; kiểm thẳng vào tham số của process:

```bash
docker inspect pwb-coturn --format '{{join .Config.Cmd " "}}' | tr ' ' '\n' | grep external-ip
```

Phải in ra **đúng** `--external-ip=52.63.23.58/172.31.6.161` — đối chiếu từng ký tự với `local-ipv4` ở §4, đừng chỉ liếc thấy có cờ `--external-ip` là qua. Cách này chắc hơn đọc log vì coturn không in lại giá trị đó một cách nhất quán giữa các bản.

---

## 7. Xác minh TURN — làm riêng, và trên máy này thì bắt buộc

⚠️ Phải làm riêng chứ đừng gộp vào smoke test: *"voice chat chạy được"* **không chứng minh** TURN hoạt động — hai máy cùng wifi luôn đi thẳng peer-to-peer, relay không bao giờ bị đụng tới. Đây là mục duy nhất bắt được sai sót ở `TURN_EXTERNAL_IP` (§4), và cũng chỉ bắt được nếu đọc đúng thứ cần đọc — xem bảng dưới.

**Bước 1 — lấy credential thật.** Vào một live room → DevTools → Network → response của `GET /liveroom/rooms/<id>/rtc/config`. Phải thấy **hai** phần tử `iceServers`: STUN của Google, và mục TURN kèm `username` dạng `<số>:<uuid>`.

**Bước 2 — Trickle ICE.** Mở [webrtc.github.io/samples/.../trickle-ice](https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/), xóa hết server mặc định, điền `turn:turn.producerworkbench.online:3478?transport=udp` cùng username/credential vừa lấy → *Gather candidates*.

| Kết quả | Nghĩa là |
|---|---|
| Có dòng loại `relay`, địa chỉ là **`52.63.23.58`** | **Đạt.** |
| Có dòng loại `relay` nhưng địa chỉ là **`172.31.x.x`** | 🔴 Bẫy: nhìn thì "đạt" nhưng relay không dùng được. `TURN_EXTERNAL_IP` sai hoặc chưa đặt — xem §4. **Phải đọc cột địa chỉ, không chỉ đọc chữ `relay`** |
| Chỉ có `host` / `srflx` | Không tới được relay. Nghi ngờ theo thứ tự: `TURN_STATIC_AUTH_SECRET` lệch giữa coturn và backend → có ai đó gỡ mất rule UDP trên Security Group (kiểm bằng §0.2) → `ufw` đã bị bật mà quên mở port TURN (§2.5) |
| `401` trong log coturn | Secret lệch, hoặc credential đã hết hạn |

**Bước 3 — thử thật.** Hai máy, **một trong hai dùng 4G chứ không chung wifi**. Cùng wifi thì không chứng minh được gì.

---

## 8. Vận hành

### Backup Postgres

```bash
mkdir -p ~/backups
cat > ~/backup-db.sh <<'EOF'
#!/bin/bash
set -eu
cd "$HOME/PWB_MiNi"
source .env.prod
STAMP=$(date +%F-%H%M)
docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" | gzip > "$HOME/backups/pwb-$STAMP.sql.gz"
find "$HOME/backups" -name 'pwb-*.sql.gz' -mtime +7 -delete
EOF
chmod +x ~/backup-db.sh
( crontab -l 2>/dev/null; echo "0 3 * * * $HOME/backup-db.sh" ) | crontab -
```

> **Thử restore một lần.** Backup chưa từng restore không tính là backup.

### Cập nhật code

Code trên `develop` → mở PR → **merge vào `main` là một lần deploy tự động** — xem [cicd.md](cicd.md). Không cần thao tác tay trên VPS. Push thẳng lên `develop` chỉ chạy test, không deploy.

Downtime ~30–60s, và **mọi live room đang mở sẽ rớt** — broker STOMP nằm in-memory nên không tránh được. **Đừng deploy trong lúc demo**; chốt code trước ít nhất 1 ngày. Nếu muốn chắc chắn hơn quanh ngày demo, tạo environment `production` kèm *Required reviewers* để mỗi lần deploy phải bấm duyệt ([cicd.md §3](cicd.md)).

Quay lui một phiên bản chỉ là đổi một dòng, không cần GitHub và không build lại — xem [cicd.md §6](cicd.md).

### Theo dõi

```bash
docker stats --no-stream          # RAM từng container so với mem_limit
df -h /                           # đĩa — theo dõi sát trên máy 19 GB
free -h                           # swap có bị dùng nhiều không
$COMPOSE logs -f backend
```

---

## 9. Bảng tra lỗi nhanh

| Triệu chứng | Nguyên nhân nhiều khả năng nhất |
|---|---|
| Vào phòng được, chat được, playlist sync — nhưng không ai nghe thấy ai (đặc biệt khi một bên dùng 4G) | **`TURN_EXTERNAL_IP` sai (§4)** — coturn đang quảng bá `172.31.x.x`. Nghi ngờ đầu tiên, vì nửa private của biến này đã sai một lần rồi. Kiểm bằng cột địa chỉ của dòng `relay` ở §7, và bằng `docker inspect` ở §6.2 |
| Y hệt dòng trên, nhưng `--external-ip` đã đúng | UDP không tới được máy. Chạy `tcpdump` ở §0.2: không thấy gói → rule Security Group bị gỡ; thấy gói mà vẫn hỏng → `ufw` đã bị bật (§2.5) |
| `docker compose ...` báo `required variable TURN_EXTERNAL_IP is missing` | Đúng như thiết kế — điền biến đó vào `.env.prod` (§4), đừng xóa dấu `:?` trong compose |
| Tìm kiếm vẫn ra kết quả nhưng không có dấu vết ES | `SPRING_ELASTICSEARCH_URIS` sai — `grep SEARCH.startup` để biết chắc |
| TTS trả `AUDIO_016` cho mọi request, nhưng app vẫn `healthy` | `secrets/gcp-tts.json` container đọc không được (§3.2), hoặc đường dẫn có tiền tố `file:` |
| Nút "Đăng nhập với Google" fail trên prod, dev vẫn chạy | Chưa thêm `https://producerworkbench.online` vào Authorized JavaScript origins |
| DevTools báo lỗi CSP khi phát nhạc | `STORAGE_PUBLIC_URL_PREFIX` sai region (§4) — và phải **build lại** frontend, không phải restart |
| Nginx trả 502 sau `up -d --build` | Bình thường trong vài giây đầu; nếu kéo dài, xem `$COMPOSE logs backend` |
| Container bị giết không rõ lý do lúc build | Hết RAM — kiểm tra swap đã bật chưa (§2.2), và build tuần tự (§5) |
| `no space left on device` | §2.1 — `docker builder prune -af`, hoặc tăng đĩa |
| Sau khi stop/start instance, mọi thứ hỏng | IP đã đổi (§1.1 — chưa gắn Elastic IP), hoặc đã lỡ đặt dữ liệu lên instance store (§2.1). Nhớ là **cả hai nửa** của `TURN_EXTERNAL_IP` đều có thể đổi (§4) |

---

## 10. Tóm tắt việc phải làm

**Đã xong, không còn phải làm**: rule UDP trên Security Group (§0.2), bucket S3 + region + CORS (§0.3), **3 bản ghi DNS** (§0.4), và `.env.prod` (§4). Tăng đĩa thì không cần vì build trên CI (§2.1).

| # | Việc | Ở đâu | Ước lượng |
|---|---|---|---|
| 1 | Bật swap 4 GB | VPS | 5 ph |
| 2 | Cài Docker + `daemon.json` | VPS | 10 ph |
| 3 | Clone repo bằng **deploy key** (CI cần `git pull`), chép `.env.prod` + `gcp-tts.json` | VPS + GitHub | 20 ph |
| 4 | Xác nhận `.env.prod` trên VPS: `TURN_EXTERNAL_IP` khớp IMDS, không sót placeholder, `compose config` chạy sạch (§4) | VPS | 5 ph |
| 5 | Tạo secrets/variables + SSH key cho CI ([cicd.md §3–4](cicd.md)) — **làm trước khi push** | GitHub + VPS | 20 ph |
| 6 | Commit + push `.github/` → workflow Deploy chạy lần đầu, image lên GHCR | GitHub | 15 ph |
| 7 | Pull image, khởi động theo §6 + chứng chỉ TLS | VPS | 30 ph |
| 8 | Xác minh TURN (§7) — **đọc cột địa chỉ, không chỉ đọc chữ `relay`** | VPS + trình duyệt | 30 ph |
| 9 | Smoke test | trình duyệt | 1 h |
| 10 | Backup + cron | VPS | 20 ph |
| — | 🟡 *Nhờ chủ máy nếu được*: Elastic IP (§1.1), siết Security Group (§0.2), IAM user + lifecycle (§1.2) | AWS Console | — |

**Tổng ~3.5 giờ làm việc.** Không còn việc nào phải chờ bên ngoài — đường đi thông suốt từ #1 tới #10.

> ⚠️ **Thứ tự #5 trước #6 là bắt buộc.** [deploy.yml](../.github/workflows/deploy.yml) chạy khi `push` vào `main` — tức mỗi lần merge PR. Merge trước khi có secrets thì workflow chạy ngay và fail.

Từ sau lần đầu, mỗi lần cập nhật code chỉ còn là một lần `git push` — xem [cicd.md](cicd.md).
