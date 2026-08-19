# Cấu hình bucket S3

> Những thứ **không** nằm trong code Java được, và vì thế không có test nào bắt được nếu ai đó đổi.
> Bối cảnh: [infra-03 — Lưu trữ S3](../technical/infra-03-luu-tru-s3.md)

---

## Vì sao có tài liệu này

Ứng dụng chạy y hệt nhau dù bucket có bật mã hoá hay không, có chặn truy cập công khai hay không, có
lifecycle rule hay không. Không log nào khác đi, không test nào đỏ. Nghĩa là ba thiết lập dưới đây chỉ
được kiểm chứng bằng việc **có người mở console lên nhìn** — nên chúng được viết ra ở đây, kèm JSON dán
thẳng vào được.

Cách chắc chắn hơn là đưa cả ba vào Terraform/CloudFormation. Repo hiện chưa có IaC nào, nên tài liệu
này là bước trung gian, không phải đích đến.

---

## 1. Chặn truy cập công khai

Nguyên tắc ở [infra-03 §1](../technical/infra-03-luu-tru-s3.md) là **bucket kín, URL ký sẵn từng lượt**.
Nguyên tắc đó chỉ đúng nếu bucket thực sự kín.

```bash
aws s3api put-public-access-block \
  --bucket "$STORAGE_S3_BUCKET" \
  --public-access-block-configuration \
      BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

Kiểm chứng — lấy một URL ký sẵn, bỏ phần sau dấu `?` rồi mở:

```bash
curl -s -o /dev/null -w "%{http_code}\n" "https://<bucket>.s3.<region>.amazonaws.com/audio/originals/<userId>/<uuid>.mp3"
```

Phải là `403`. Nếu ra `200` thì toàn bộ tầng phân quyền của Audio và IAM đang không có tác dụng gì.

---

## 2. Mã hoá mặc định

Server-side encryption được đặt thẳng trong `PutObjectRequest` cho **các đường tải lên từ server**
(`upload`, `uploadFile`). Nhưng đường quan trọng nhất — bài hát, do client tự `PUT` bằng URL ký sẵn —
thì không: thêm `x-amz-server-side-encryption` vào chữ ký buộc trình duyệt phải gửi lại header đó y
nguyên, và sai một chữ là `403` không nói rõ lý do.

Nên đường đó dựa vào **mã hoá mặc định của bucket**:

```bash
aws s3api put-bucket-encryption \
  --bucket "$STORAGE_S3_BUCKET" \
  --server-side-encryption-configuration '{
    "Rules": [{
      "ApplyServerSideEncryptionByDefault": { "SSEAlgorithm": "AES256" },
      "BucketKeyEnabled": true
    }]
  }'
```

Bucket tạo mới trên AWS từ tháng 1/2023 đã bật sẵn cái này, nên phần lớn trường hợp là xác nhận chứ
không phải thay đổi. Vẫn nên kiểm, vì "phần lớn" không phải "tất cả":

```bash
aws s3api get-bucket-encryption --bucket "$STORAGE_S3_BUCKET"
```

---

## 3. Lifecycle rule — thứ duy nhất dọn được rác tải lên dở

Đây là mục quan trọng nhất trong tài liệu này.

`POST /songs/upload-url` cấp một khoá và một URL `PUT`, rồi **không ghi lại gì cả**. Nếu người dùng
`PUT` xong mà không bao giờ gọi `POST /songs` — đóng tab, mất mạng, đổi ý — thì đối tượng nằm trong
bucket mà **không hàng nào trong database trỏ tới nó**. Không code nào tìm lại được, nên không code nào
xoá được. Nó ở đó vĩnh viễn và bị tính tiền vĩnh viễn.

Lifecycle rule là cách duy nhất chạm tới nó mà không phải thêm bảng và một job quét. Và để rule đó an
toàn, code đã được sửa để **hai loại tệp không còn nằm chung một tiền tố**:

| Tiền tố | Chứa gì | Ai xoá |
|---|---|---|
| `audio/staging/` | Tệp client vừa `PUT` lên, **chưa** đăng ký | Lifecycle rule bên dưới |
| `audio/originals/` | Bản gốc của bài **đã** đăng ký, đang phát được | `deleteSong` |
| `audio/processed/` | Bản đã ghép voice tag | `deleteSong` |

`POST /songs` copy đối tượng từ `audio/staging/` sang `audio/originals/` rồi mới ghi hàng, và chỉ xoá
bản ở staging **sau khi** transaction commit. Copy là thao tác phía S3, bytes không đi qua backend.

Nhờ vậy **mọi thứ còn sót lại trong `audio/staging/` theo định nghĩa là rác** — không hàng nào trỏ tới
đó được, vì hàng nào cũng trỏ vào `audio/originals/`. Rule dưới đây vì thế bật được ngay:

```json
{
  "Rules": [
    {
      "ID": "abort-incomplete-multipart",
      "Status": "Enabled",
      "Filter": { "Prefix": "" },
      "AbortIncompleteMultipartUpload": { "DaysAfterInitiation": 1 }
    },
    {
      "ID": "expire-abandoned-staging",
      "Status": "Enabled",
      "Filter": { "Prefix": "audio/staging/" },
      "Expiration": { "Days": 1 }
    }
  ]
}
```

```bash
aws s3api put-bucket-lifecycle-configuration \
  --bucket "$STORAGE_S3_BUCKET" \
  --lifecycle-configuration file://lifecycle.json
```

Một ngày là hạn nhỏ nhất lifecycle nhận, và thoải mái so với URL tải lên sống 1 giờ: tệp bỏ dở nằm lại
nhiều nhất khoảng một ngày.

> **Vì sao tiền tố mới tên là `staging` chứ không phải `originals`:** cách hiển nhiên hơn là để tệp mới
> lên vẫn vào `audio/originals/` rồi chuyển bài đã đăng ký sang chỗ khác. Nhưng làm vậy thì **mọi bài
> hát đã có từ trước** vẫn đang nằm trong `audio/originals/`, và bật rule là xoá sạch nhạc thật sau một
> ngày — trừ khi chạy thêm một đợt backfill copy chúng đi. Đảo lại tên giữ cho `audio/originals/` mang
> đúng nghĩa cũ, dữ liệu cũ không phải đụng tới, và tiền tố bị quét là tiền tố **chưa từng tồn tại**
> trước thay đổi này, nên chắc chắn rỗng sạch lúc bắt đầu.

**Chỗ vẫn còn hở:** nếu copy xong mà commit hỏng đúng lúc (không phải lỗi ứng dụng bắt được, mà là
database ngã lúc commit), bản copy nằm lại trong `audio/originals/` — ngoài tầm rule. Một đối tượng
mỗi lần, hiếm, và nhỏ hơn nhiều so với chuyện trước đây **mọi** tệp bỏ dở đều nằm lại vĩnh viễn.

---

## 4. CORS

Trình duyệt `PUT` thẳng lên S3 ([song-upload-form.tsx](../../Frontend/src/features/voice/components/song-upload-form.tsx)),
nên bucket phải cho phép origin của frontend. `ExposeHeaders` cần `ETag` để client đọc được kết quả.

```json
[
  {
    "AllowedOrigins": ["https://<FRONTEND_DOMAIN>"],
    "AllowedMethods": ["PUT", "GET"],
    "AllowedHeaders": ["Content-Type", "Content-Length"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3000
  }
]
```

`AllowedHeaders` phải có đúng hai header trên, vì đó là hai header được ký vào URL tải lên. Bỏ sót một
cái thì preflight trượt và mọi lượt tải lên hỏng — với thông báo trong console trình duyệt chứ không
phải trong log server.

Đừng để `AllowedOrigins: ["*"]` ở production: URL ký sẵn là credential, và `*` cho phép trang bất kỳ
dùng nó từ trình duyệt của nạn nhân.

---

## 5. Bảng kiểm nhanh

| Thiết lập | Lệnh kiểm | Kỳ vọng |
|---|---|---|
| Chặn công khai | `aws s3api get-public-access-block --bucket "$B"` | Cả bốn cờ `true` |
| Mã hoá mặc định | `aws s3api get-bucket-encryption --bucket "$B"` | `AES256` |
| Lifecycle | `aws s3api get-bucket-lifecycle-configuration --bucket "$B"` | Cả `abort-incomplete-multipart` lẫn `expire-abandoned-staging` |
| Staging rỗng dần | `aws s3 ls "s3://$B/audio/staging/" --recursive \| wc -l` | Chỉ còn tệp của hôm nay; con số tăng đều là dấu hiệu `POST /songs` đang hỏng |
| CORS | `aws s3api get-bucket-cors --bucket "$B"` | Origin cụ thể, không phải `*` |
| Quyền IAM của app | `aws iam get-user-policy …` | Chỉ `s3:GetObject`, `PutObject`, `DeleteObject`, `ListBucket` trên đúng bucket này |
