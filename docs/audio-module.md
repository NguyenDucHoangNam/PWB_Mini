# 🎵 Module Audio — Hướng dẫn sử dụng

## Tổng quan

Module **Audio** là trung tâm quản lý nội dung âm thanh của PWB. Module này cho phép bạn tải lên bài hát, tạo và quản lý **Voice Tag** (đoạn âm thanh nhận diện thương hiệu), sau đó tự động **ghép Voice Tag vào bài hát** (watermark) theo chu kỳ bạn cấu hình — tất cả đều được xử lý hoàn toàn trên server.

---

## 1. Quản lý Bài hát (Songs)

### 1.1. Tải lên bài hát

Bạn có thể tải lên bài hát với các định dạng được hỗ trợ:

| Định dạng | MIME Type |
|-----------|-----------|
| MP3 | `audio/mpeg` |
| WAV | `audio/wav` |
| FLAC | `audio/flac` |

**Quy trình upload:**

1. Hệ thống tạo một **Presigned URL** (link upload tạm thời, có hiệu lực 1 giờ) trên S3.
2. Trình duyệt upload file trực tiếp lên S3 qua link đó — không đi qua server, giúp tải nhanh hơn.
3. Sau khi upload xong, hệ thống xác nhận file thực sự tồn tại trên S3 và đăng ký bài hát vào cơ sở dữ liệu.

**Giới hạn:**

| Thông số | Giá trị |
|----------|---------|
| Kích thước file tối đa (bài hát) | **200 MB** |
| Kích thước file tối đa (validation phía client) | **100 MB** |

### 1.2. Thông tin bài hát

Mỗi bài hát lưu trữ các thông tin sau:

- **Tiêu đề** (bắt buộc, tối đa 200 ký tự)
- **Định dạng file** (mp3 / wav / flac)
- **Thời lượng** (đo bằng giây)
- **Kích thước file**
- **Trạng thái xử lý** (xem phần 1.3)
- **Có gắn Voice Tag hay không**

### 1.3. Vòng đời trạng thái (Song Status)

Mỗi bài hát đi qua các trạng thái sau:

```
UPLOADED ──→ PROCESSING ──→ PROCESSED
                │
                └──→ FAILED ──→ (Retry) ──→ PROCESSING
```

| Trạng thái | Ý nghĩa |
|------------|---------|
| `UPLOADED` | Bài hát đã tải lên thành công, **không gắn Voice Tag** hoặc chưa bắt đầu xử lý. Bài hát ở trạng thái này phát được ngay. |
| `PROCESSING` | Đang ghép Voice Tag (watermark) vào bài hát. Chưa phát được. |
| `PROCESSED` | Đã ghép Voice Tag thành công. Bài hát phát bản đã ghép. |
| `FAILED` | Quá trình ghép thất bại. Bạn có thể bấm **Thử lại (Retry)** để chạy lại. |

**Lưu ý quan trọng:**
- Bài hát **không gắn Voice Tag** sẽ luôn ở trạng thái `UPLOADED` và phát được ngay.
- Bài hát **đã xử lý xong** (`PROCESSED`) không thể xử lý lại — kết quả là cuối cùng.
- Chỉ bài hát ở trạng thái `FAILED` mới cho phép **Retry**.

### 1.4. Chỉnh sửa & Xoá bài hát

- **Chỉnh sửa**: Bạn có thể thay đổi tiêu đề bài hát bất cứ lúc nào.
- **Xoá**: Xoá bài hát sẽ đồng thời xoá cấu hình Voice Tag đi kèm và dọn sạch file trên S3 (cả file gốc lẫn file đã ghép).

### 1.5. Phát bài hát

Khi bạn phát một bài hát, hệ thống tự động chọn bản phát phù hợp:
- Nếu bài hát **đã ghép Voice Tag** → phát bản đã ghép (processed).
- Nếu bài hát **không ghép Voice Tag** → phát bản gốc (original).

Link phát có thời hạn (Presigned URL), từ **1 phút** đến tối đa **24 giờ** (mặc định 1 giờ).

---

## 2. Voice Tag — Đánh dấu âm thanh thương hiệu

**Voice Tag** là đoạn âm thanh ngắn mang tính nhận diện (ví dụ: "Bản quyền thuộc về DJ Minh", "Live at PWB Radio"...) được lặp lại xuyên suốt bài hát để bảo vệ bản quyền hoặc branding.

### 2.1. Hai cách tạo Voice Tag

#### Cách 1: Text-to-Speech (TTS)

Bạn nhập văn bản → hệ thống dùng **Google Cloud Text-to-Speech** để tổng hợp giọng nói tự động.

**Ngôn ngữ & Giọng đọc được hỗ trợ:**

| Ngôn ngữ | Giọng nữ | Giọng nam |
|----------|----------|-----------|
| 🇻🇳 Tiếng Việt (`vi-VN`) | Wavenet-A, Wavenet-C | Wavenet-B, Wavenet-D |
| 🇺🇸 Tiếng Anh Mỹ (`en-US`) | Neural2-C, Wavenet-F | Neural2-D, Wavenet-B |
| 🇬🇧 Tiếng Anh Anh (`en-GB`) | Neural2-A, Wavenet-A | Neural2-B, Wavenet-B |

**Tính năng Preview:** Trước khi lưu, bạn có thể **nghe thử** bất kỳ tổ hợp văn bản + giọng đọc nào mà không tốn lưu trữ — bản preview không lưu vào hệ thống.

#### Cách 2: Upload file âm thanh

Bạn tự thu âm và tải lên file Voice Tag. Hệ thống sẽ:
1. Kiểm tra định dạng (mp3 / wav / flac).
2. Đo thời lượng thật bằng **ffprobe** (không tin giá trị từ client).
3. Từ chối nếu vượt giới hạn cho phép.

**Giới hạn Voice Tag:**

| Thông số | Giá trị |
|----------|---------|
| Thời lượng tối đa | **10 giây** |
| Kích thước file tối đa | **10 MB** |
| Định dạng | MP3, WAV, FLAC |

### 2.2. Quản lý Voice Tag

- **Tên Voice Tag** phải là duy nhất trong tài khoản của bạn. Trùng tên sẽ bị từ chối.
- **Chỉnh sửa**: Đổi tên Voice Tag.
- **Xoá**: Chỉ xoá được Voice Tag **không đang gắn vào bài hát nào**. Nếu còn bài hát đang dùng, hệ thống sẽ từ chối xoá.
- **Phát thử**: Lấy link phát Voice Tag (Presigned URL, hiệu lực 1 giờ).

---

## 3. Cấu hình Voice Tag trên Bài hát (Song Tag Config)

Khi tải lên bài hát, bạn có thể chọn **gắn kèm một Voice Tag**. Cấu hình này **cố định ngay lúc upload** và không thể thay đổi sau đó.

### 3.1. Các tham số cấu hình

| Tham số | Mô tả | Mặc định | Khoảng giá trị |
|---------|--------|----------|----------------|
| **Voice Tag** | Voice Tag sẽ được ghép vào | *(bắt buộc chọn)* | — |
| **Interval (giây)** | Khoảng cách giữa mỗi lần Voice Tag xuất hiện | 30 giây | 5 – 600 giây |
| **Volume (%)** | Âm lượng của Voice Tag so với bản gốc | 80% | 0 – 100% |
| **Ducking (%)** | Mức giảm âm lượng nhạc nền khi Voice Tag phát | 50% | 0 – 100% |
| **Start Offset (giây)** | Thời điểm bắt đầu phát Voice Tag đầu tiên | 0 giây | ≥ 0 |

### 3.2. Giải thích chi tiết

- **Interval**: Ví dụ interval = 30s → Voice Tag sẽ xuất hiện vào giây thứ 0, 30, 60, 90... (tính từ Start Offset). Interval **phải dài hơn** thời lượng Voice Tag (ví dụ: Voice Tag 5 giây thì interval phải > 5 giây).
- **Volume**: 80% nghĩa là Voice Tag phát ở 80% âm lượng so với nguyên bản.
- **Ducking**: 50% nghĩa là khi Voice Tag đang phát, nhạc nền bị giảm xuống còn 50% âm lượng, giúp Voice Tag nghe rõ hơn. Đặt 100% = không giảm nhạc nền.
- **Start Offset**: 10 giây → Voice Tag đầu tiên xuất hiện ở giây thứ 10 thay vì giây 0.

---

## 4. Xử lý Watermark (Audio Processing)

Khi bạn tải lên bài hát có kèm Voice Tag, hệ thống tự động khởi chạy quy trình ghép:

### 4.1. Quy trình xử lý

```
Upload bài hát + chọn Voice Tag
       │
       ▼
Ghi cấu hình vào DB
       │
       ▼
Gửi event qua Kafka (Outbox Pattern)
       │
       ▼
Kafka Consumer nhận → SongProcessorWorker bắt đầu
       │
       ▼
Tải file bài hát + Voice Tag từ S3
       │
       ▼
FFmpeg ghép watermark theo cấu hình
       │
       ▼
Upload file đã ghép lên S3
       │
       ▼
Cập nhật trạng thái → PROCESSED ✅
```

### 4.2. Cách ghép hoạt động

Hệ thống sử dụng **FFmpeg** (qua thư viện Jaffree) để:

1. **Tạo track Voice Tag**: Voice Tag được lặp lại đúng theo interval đã cấu hình, với volume đã chỉ định.
2. **Ducking nhạc nền**: Nếu ducking < 100%, nhạc nền sẽ tự động giảm âm lượng mỗi khi Voice Tag phát.
3. **Mix hai track**: Trộn track Voice Tag vào nhạc nền, giữ nguyên thời lượng bài hát gốc.
4. **Xuất file MP3**: Kết quả được encode ra MP3 với bitrate cấu hình sẵn.

**Giới hạn xử lý:**

| Thông số | Giá trị |
|----------|---------|
| Timeout xử lý | **15 phút** (mặc định, cấu hình được) |
| Số lần chèn Voice Tag tối đa | **500 lần** mỗi bài |
| Output format | **MP3** |

### 4.3. Xử lý lỗi & Retry

- Nếu FFmpeg gặp lỗi hoặc timeout, bài hát chuyển sang `FAILED` và lưu thông tin lỗi.
- Bạn có thể bấm **Thử lại** từ giao diện hoặc gọi API retry.
- Bài hát đã `PROCESSED` thành công **không thể** xử lý lại.

---

## 5. Tìm kiếm

Module Audio cung cấp hệ thống tìm kiếm mạnh mẽ, hỗ trợ bởi **Elasticsearch**:

### 5.1. Tìm kiếm Bài hát

- **Full-text search** theo tiêu đề bài hát.
- Chịu lỗi chính tả (fuzzy matching) và hỗ trợ tìm **không dấu tiếng Việt** (ví dụ: "ha noi" tìm ra "Hà Nội").
- Lọc theo trạng thái (`UPLOADED`, `PROCESSING`, `PROCESSED`, `FAILED`), định dạng, khoảng thời lượng.
- **Suggest (gợi ý)**: Gõ từ 2 ký tự trở lên → hệ thống gợi ý tiêu đề bài hát phù hợp theo thời gian thực.
- **Fallback**: Nếu Elasticsearch không khả dụng, hệ thống tự động chuyển sang tìm kiếm bằng database (substring matching).

### 5.2. Tìm kiếm Voice Tag

- **Full-text search** theo tên Voice Tag.
- Lọc theo loại (`TTS` hoặc `UPLOADED`) và ngôn ngữ.
- **Suggest**: Gợi ý cho picker Voice Tag trong form upload bài hát — mỗi gợi ý kèm theo thông tin giọng đọc và ngôn ngữ.
- **Fallback** database tương tự bài hát.

---

## 6. Giao diện người dùng (Frontend)

### 6.1. Trang Bài hát (`/dashboard/songs`)

- Danh sách bài hát phân trang, sắp xếp theo ngày tạo mới nhất.
- Lọc nhanh theo trạng thái: **Tất cả**, **Sẵn sàng**, **Đang xử lý**, **Thất bại**.
- Mỗi bài hát hiển thị: tiêu đề, trạng thái (badge màu), thời lượng, có gắn Voice Tag hay không.
- Thao tác: Phát, Sửa tên, Xoá.

### 6.2. Trang Chi tiết Bài hát (`/dashboard/songs/[songId]`)

- Xem đầy đủ thông tin bài hát.
- **Audio Player** tích hợp để nghe bài hát.
- Xem cấu hình Voice Tag (nếu có): Voice Tag nào, interval, volume, ducking...
- Nút **Retry** cho bài hát bị lỗi.

### 6.3. Trang Upload Bài hát (`/dashboard/songs/new`)

- Form upload bài hát với **kéo-thả (drag & drop)** hoặc chọn file.
- Thanh tiến trình upload realtime.
- Tùy chọn gắn Voice Tag:
  - Chọn Voice Tag từ picker (có search gợi ý).
  - Cấu hình interval, volume, ducking, start offset bằng slider/input.
- Sau upload, nếu có Voice Tag → tự động chờ kết quả xử lý (poll mỗi 2 giây, tối đa 5 phút) rồi chuyển tới trang chi tiết bài hát.

### 6.4. Quản lý Voice Tag (`/dashboard/songs` — tab Voice Tags)

- Danh sách Voice Tag phân trang.
- Mỗi Voice Tag hiển thị: tên, loại (TTS / Uploaded), ngôn ngữ (có cờ quốc gia), thời lượng, ngày tạo.
- Thao tác: Phát thử, Sửa tên, Xoá.
- Tạo Voice Tag mới:
  - **Tab TTS**: Nhập tên, chọn ngôn ngữ, chọn giọng đọc, nhập văn bản → Preview → Lưu.
  - **Tab Upload**: Nhập tên, chọn file âm thanh → Lưu.

---

## 7. Bảo mật & Quyền truy cập

- Mọi API đều yêu cầu **xác thực** (authenticated).
- Người dùng chỉ truy cập được **dữ liệu của chính mình**: bài hát, Voice Tag, cấu hình.
- File upload trên S3 được phân vùng theo User ID; hệ thống kiểm tra **storage key** thuộc đúng user trước khi chấp nhận.
- Presigned URL có thời hạn, không thể tái sử dụng sau khi hết hạn.

---

## 8. Tóm tắt kiến trúc kỹ thuật

| Thành phần | Công nghệ |
|------------|-----------|
| Backend | Java 21, Spring Boot 3.x |
| Xử lý âm thanh | FFmpeg (qua Jaffree) |
| Text-to-Speech | Google Cloud TTS (Wavenet / Neural2) |
| Lưu trữ file | Amazon S3 (Presigned URL) |
| Message Queue | Apache Kafka (Outbox Pattern) |
| Tìm kiếm | Elasticsearch |
| Frontend | Next.js, TypeScript, TanStack Query |
| Quản lý form | React Hook Form + Zod validation |
