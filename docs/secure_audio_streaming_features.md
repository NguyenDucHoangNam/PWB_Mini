# Module 3: Secure Audio Streaming — Mô tả Chức năng Nghiệp vụ

Tài liệu tổng hợp mô tả các chức năng nghiệp vụ của phân hệ **Truyền phát Nhạc Bảo mật** (Secure Audio Streaming). Module này phục vụ toàn bộ vòng đời của một bản nhạc demo: từ lúc Producer tải lên → đóng dấu bản quyền → chia sẻ cho khách hàng → khách nghe thử bảo mật → tải file gốc → thu hồi liên kết.

---

## Mục lục

1. [Tải lên & Xử lý Nhạc gốc](#1-tải-lên--xử-lý-nhạc-gốc)
2. [Tạo Voice Tag (Thẻ giọng nói bản quyền)](#2-tạo-voice-tag-thẻ-giọng-nói-bản-quyền)
3. [Phân phối & Chia sẻ Demo](#3-phân-phối--chia-sẻ-demo)
4. [Truyền phát Nhạc bảo mật HLS](#4-truyền-phát-nhạc-bảo-mật-hls)
5. [Tải file gốc chất lượng cao](#5-tải-file-gốc-chất-lượng-cao)
6. [Thu hồi quyền truy cập liên kết](#6-thu-hồi-quyền-truy-cập-liên-kết)

---

## 1. Tải lên & Xử lý Nhạc gốc

### Đối tượng sử dụng
Producer (nhà sản xuất nhạc) có tài khoản nâng cấp PRO.

### Mô tả chức năng
Cho phép Producer tải lên tệp nhạc gốc chất lượng cao và hệ thống tự động xử lý ngầm để tạo ra bản nhạc nghe thử có đóng dấu bản quyền, sẵn sàng chia sẻ.

### Quy trình nghiệp vụ
1. Producer kéo thả file âm thanh gốc (.wav, .flac, .mp3) vào vùng tải lên, nhập tiêu đề bài hát và chọn cấu hình Voice Tag đóng dấu.
2. Hệ thống sinh một liên kết tải lên trực tiếp lên kho lưu trữ đám mây (không đi qua máy chủ Backend) có thời hạn 60 giây.
3. Ứng dụng đẩy file thẳng lên kho lưu trữ riêng tư. Sau khi thành công, gửi yêu cầu xác nhận lên Backend.
4. Backend phản hồi ngay trạng thái "Đang xử lý" và đưa tác vụ vào hàng đợi xử lý ngầm.
5. Hệ thống xử lý ngầm thực hiện:
   - **Xác thực tệp tin**: Kiểm tra file thực sự là âm thanh hợp lệ (chống giả mạo định dạng).
   - **Đóng dấu bản quyền (Sidechain Ducking)**: Ghép Voice Tag đè lên nhạc nền. Khi Voice Tag phát, âm lượng nhạc nền tự động giảm xuống rồi phục hồi ngay khi Voice Tag kết thúc.
   - **Mã hóa & phân đoạn HLS**: Cắt file thành các đoạn nhỏ 6 giây, mã hóa từng đoạn bằng khóa bảo mật riêng.
   - **Trích xuất Waveform**: Phân tích biên độ âm thanh, tạo dữ liệu hình sóng 200 điểm để hiển thị trên giao diện.
6. Khi hoàn tất, hệ thống cập nhật trạng thái bản nhạc thành "Hoạt động" và thông báo real-time cho Producer qua WebSocket.

### Ràng buộc nghiệp vụ
| Quy tắc | Chi tiết |
|:---|:---|
| Định dạng chấp nhận | `.wav`, `.flac`, `.mp3` (MP3 yêu cầu bitrate ≥ 320kbps) |
| Giới hạn dung lượng | Tối đa 200 MB / file |
| Giới hạn số demo hoạt động | Tối đa 20 demo ở trạng thái hoạt động cùng lúc / user |
| Giới hạn tổng dung lượng | Tối đa 5 GB tổng dung lượng demo hoạt động / user |
| Dọn dẹp file rác | File tải lên dở dang (chưa xác nhận) tự động bị xóa sau 24 giờ |
| Xử lý lỗi | File lỗi sau 3 lần thử xử lý sẽ được chuyển sang hàng đợi lỗi để xem xét |

### Trạng thái vòng đời Demo

```
PROCESSING → ACTIVE → DELETED
     ↓
   FAILED
```

- **PROCESSING**: Đang trong tiến trình xử lý ngầm (đóng dấu, mã hóa, phân đoạn).
- **ACTIVE**: Sẵn sàng chia sẻ và phát trực tuyến.
- **FAILED**: Xử lý thất bại (file lỗi, không hợp lệ).
- **DELETED**: Đã bị Producer xóa (soft-delete).

---

## 2. Tạo Voice Tag (Thẻ giọng nói bản quyền)

### Đối tượng sử dụng
Producer có tài khoản PRO.

### Mô tả chức năng
Cho phép Producer tạo các thẻ giọng nói thương hiệu (Voice Tag) từ văn bản bằng công nghệ chuyển đổi văn bản thành giọng nói (Text-to-Speech). Voice Tag dùng để đóng dấu bản quyền lên các bản nhạc demo nghe thử.

### Quy trình nghiệp vụ
1. Producer nhập văn bản thương hiệu muốn đọc (ví dụ: *"PWB Preview"* hoặc *"Bản nghe thử của Hoàng Nam"*).
2. Chọn ngôn ngữ (tiếng Việt, tiếng Anh,...) và loại giọng đọc (Nam/Nữ, chuẩn hoặc nâng cao).
3. Hệ thống gửi yêu cầu sang dịch vụ chuyển đổi giọng nói, nhận về tệp âm thanh giọng đọc.
4. Tệp âm thanh được lưu trữ an toàn trên kho lưu trữ đám mây riêng tư.
5. Producer có thể nghe thử (Preview) trực tiếp hoặc đặt Voice Tag này làm mặc định cho tất cả bản nhạc tải lên sau.

### Các thao tác chính

| Thao tác | Mô tả |
|:---|:---|
| **Tạo mới** | Nhập văn bản, chọn ngôn ngữ & giọng đọc → Hệ thống sinh file giọng nói |
| **Nghe thử** | Nghe lại Voice Tag đã tạo qua trình phát nhỏ trong ứng dụng |
| **Đặt mặc định** | Chọn 1 Voice Tag làm mặc định cho mọi lần upload demo mới (mỗi user chỉ có 1 mặc định) |
| **Xem danh sách** | Liệt kê tất cả Voice Tag đã tạo, sắp xếp mặc định lên đầu |
| **Xóa** | Xóa mềm (soft-delete) — từ chối nếu Voice Tag đang được sử dụng bởi demo hoạt động |

### Ràng buộc nghiệp vụ
| Quy tắc | Chi tiết |
|:---|:---|
| Giới hạn ký tự | Tối đa 100 ký tự văn bản thô (không tính cú pháp SSML) |
| Chỉ 1 mặc định | Mỗi Producer chỉ có 1 Voice Tag mặc định tại bất kỳ thời điểm nào |
| Giới hạn tổng số | Tối đa 50 Voice Tag chưa xóa / user |
| Giới hạn dung lượng | Tổng dung lượng Voice Tag tối đa 200 MB / user |
| Giới hạn tạo mới | Tối đa 30 Voice Tag / ngày / user (kiểm soát chi phí dịch vụ TTS) |
| Hỗ trợ phiên âm | Hỗ trợ SSML để kiểm soát cách phát âm nghệ danh, từ viết tắt |
| Cắt khoảng lặng | Tự động loại bỏ khoảng lặng đầu/cuối của giọng đọc để tránh lỗi khi ghép nhạc |
| Khôi phục | Trong vòng 7 ngày sau khi xóa, user có thể khôi phục Voice Tag |

---

## 3. Phân phối & Chia sẻ Demo

### Đối tượng sử dụng
Producer có tài khoản PRO.

### Mô tả chức năng
Cho phép Producer chia sẻ bản nhạc demo cho khách hàng qua email. Hệ thống tổ chức lịch sử chia sẻ dưới dạng luồng hội thoại (Shared Thread) giữa Producer và từng khách hàng, giống như một chuỗi tin nhắn.

### Quy trình nghiệp vụ
1. Producer chọn bản nhạc demo đã ở trạng thái hoạt động, nhấn "Chia sẻ".
2. Nhập email khách hàng nhận nhạc (có gợi ý tự động danh sách đối tác cũ).
3. Cấu hình quyền: Bật/Tắt cho phép tải xuống tệp gốc.
4. Hệ thống tạo hoặc tái sử dụng luồng chia sẻ chung giữa Producer và email khách.
5. Sinh mã Token bảo mật độc quyền (UUID ngẫu nhiên, không thể đoán trước) cho liên kết.
6. Ghi nhận sự kiện gửi email vào hàng đợi. Tiến trình ngầm sẽ gửi email chứa liên kết nghe thử: `https://pwbmini.com/shared/{shareToken}`.

### Các tính năng nổi bật

#### Luồng chia sẻ (Shared Thread)
- Mỗi cặp (Producer, Email khách hàng) chỉ có duy nhất 1 luồng chia sẻ.
- Mỗi lần Producer gửi bài mới cho email đó → thêm bản ghi vào cùng luồng.
- Khách hàng truy cập có thể xem toàn bộ lịch sử các bản demo từng nhận trên 1 giao diện.

#### Gợi ý tự động (Autocomplete)
- Khi Producer nhập email, hệ thống gợi ý tối đa 10 email đối tác cũ đã từng tương tác, sắp xếp theo thời gian gần nhất.

#### Gửi email qua hàng đợi (Outbox Pattern)
- Email không gửi trực tiếp trong API call → tránh API bị treo khi SMTP chậm.
- Đóng gói sự kiện email vào bảng outbox cùng transaction với lưu phân phối.
- Tiến trình ngầm quét và gửi, chống trùng lặp email giữa nhiều máy chủ.
- Giới hạn tối đa 5 lần thử gửi lại, sau đó chuyển vào hàng đợi lỗi.

#### Bảo vệ liên kết chống chuyển tiếp (Phase 2 — OTP)
- Khi khách truy cập link chia sẻ, hệ thống gửi mã OTP 6 số về email khách.
- Khách phải nhập đúng OTP mới được nghe nhạc.
- Nhập sai 3 lần → khóa link 15 phút.

### Ràng buộc nghiệp vụ
| Quy tắc | Chi tiết |
|:---|:---|
| Token độc lập | Mỗi lượt phân phối có shareToken riêng biệt, thu hồi độc lập |
| Chỉ chia sẻ demo ACTIVE | Không thể chia sẻ demo đang xử lý hoặc đã lỗi |
| Giới hạn email / ngày | Tối đa 100 email khác nhau trong 24 giờ / user |
| Giới hạn phân phối / ngày | Tối đa 500 lượt chia sẻ trong 24 giờ / user |
| Chặn email rác | Từ chối email thuộc domain blacklist (mailinator, tempmail,...) |
| Bảo mật email trong DB | Email được chuẩn hóa lowercase trước khi lưu, hash SHA-256 để chống trùng lặp |

---

## 4. Truyền phát Nhạc bảo mật HLS

### Đối tượng sử dụng
Khách hàng (Listener) nhận được liên kết nghe thử độc quyền qua email.

### Mô tả chức năng
Cho phép khách hàng nghe thử bản nhạc demo trực tuyến với cơ chế bảo mật nhiều lớp: mã hóa AES-128, phân phối qua CDN, kiểm soát khóa giải mã và chống tải lậu.

### Quy trình nghiệp vụ
1. Khách hàng click vào liên kết `/shared/{shareToken}` nhận từ email.
2. Hệ thống kiểm tra tính hợp lệ của liên kết (chưa thu hồi, demo còn hoạt động).
3. Cấp phiên bảo mật (Secure Session Cookie) gắn với IP khách.
4. Trình phát nhạc tải danh sách phát (.m3u8) → tải các đoạn nhạc mã hóa từ CDN → lấy khóa giải mã từ Backend.
5. Giải mã và phát nhạc trực tiếp trên bộ nhớ RAM trình duyệt (không lưu file xuống ổ cứng).
6. Khi khách nghe qua 30% bài hát và liên tục ít nhất 15 giây → ghi nhận 1 lượt nghe.

### Cơ chế bảo mật phát nhạc

| Lớp bảo vệ | Mô tả |
|:---|:---|
| **Mã hóa AES-128** | Từng đoạn nhạc .ts được mã hóa bằng khóa riêng, CDN chỉ phục vụ file đã mã hóa |
| **Khóa giải mã qua Backend** | Khóa giải mã chỉ được cấp qua API Backend sau khi xác thực phiên và IP |
| **Phiên bảo mật (Session Cookie)** | Cookie HttpOnly, Secure, SameSite=Strict, gắn IP subnet, TTL 30 phút |
| **Kiểm tra IP CIDR** | So sánh IP theo dải mạng /24 (IPv4) hoặc /48 (IPv6) để hỗ trợ di chuyển mạng di động |
| **Giải mã trên RAM** | Nhạc chỉ tồn tại tạm thời trên bộ nhớ, không lưu ổ cứng |
| **CDN tối ưu** | Đoạn nhạc tải trực tiếp từ CDN, giảm tải Backend |

### Ghi nhận lượt nghe (Anti-fraud Play Count)

| Quy tắc | Chi tiết |
|:---|:---|
| Ngưỡng tính lượt nghe | Nghe ≥ 30% bài hát VÀ liên tục ≥ 15 giây sau mốc 30% |
| Chống spam | Mỗi thiết bị/IP chỉ tính 1 lượt / 24 giờ (dựa trên fingerprint server-side) |
| Chống bot | Kết hợp kiểm tra heartbeat WebSocket + số lần request key giải mã thực tế |
| Body rỗng | API ghi lượt nghe không nhận tham số từ client, tự trích xuất từ header request |

### Ràng buộc nghiệp vụ
| Quy tắc | Chi tiết |
|:---|:---|
| Chỉ phát demo ACTIVE | Demo ở trạng thái PROCESSING/FAILED/DELETED bị từ chối |
| Link thu hồi | Link đã thu hồi bị chặn tức thời, kể cả khi Cookie còn hạn |
| Rate limit key | Tối đa 600 yêu cầu khóa giải mã / phút / token |
| Rate limit lượt nghe | Tối đa 5 / phút / IP, 10 / ngày / session |

---

## 5. Tải file gốc chất lượng cao

### Đối tượng sử dụng
Khách hàng (Listener) được Producer cấp quyền tải xuống.

### Mô tả chức năng
Cho phép khách hàng tải xuống tệp nhạc gốc chất lượng cao (.wav, .flac) không bị đóng dấu bản quyền. Chức năng này do Producer quyết định bật/tắt cho từng lượt chia sẻ.

### Quy trình nghiệp vụ
1. Tại giao diện nghe thử, nếu Producer cho phép tải gốc → nút "Tải xuống file gốc" hiển thị.
2. Khách hàng nhấn nút tải xuống.
3. Hệ thống kiểm tra quyền hạn: liên kết chưa thu hồi, quyền tải được bật, demo còn hoạt động, Cookie phiên hợp lệ.
4. Sinh liên kết tải xuống trực tiếp từ kho lưu trữ với thời hạn 5 phút, ép trình duyệt mở hộp thoại "Save As" với tên file tiếng Việt có dấu hiển thị đúng.
5. Trả về liên kết qua JSON để Frontend tự kích hoạt tải ngầm (bảo toàn giao diện SPA, không bị sập trang khi lỗi).

### Ràng buộc nghiệp vụ
| Quy tắc | Chi tiết |
|:---|:---|
| Quyền tải | Chỉ hoạt động khi `allowDownload = true` và link chưa thu hồi |
| Xác thực phiên | Yêu cầu Secure Session Cookie hợp lệ + IP khớp |
| Thời hạn link | Link tải hết hạn sau 5 phút |
| Giới hạn lượt tải/ngày | Tối đa 10 lượt / ngày / session |
| Giới hạn lượt tải/tuần | Tối đa 100 lượt / tuần / token chia sẻ |
| Kiểm tra file | Xác thực extension và kích thước file trước khi sinh link tải |
| Tên file tiếng Việt | Hỗ trợ hiển thị tên file có dấu đúng chuẩn RFC 5987 |
| Ghi nhận tải xuống | Mỗi lượt tải được ghi nhận vào bảng audit để thống kê và đối soát |
| Tự động xóa audit | Bản ghi audit tự xóa sau 90 ngày |

### Trường hợp từ chối
- Producer tắt quyền tải → nút ẩn đi, API trả 403.
- Link đã thu hồi → từ chối.
- Demo không ở trạng thái ACTIVE → từ chối.
- Cookie hết hạn hoặc IP không khớp → từ chối.
- Vượt quota tải → từ chối.

---

## 6. Thu hồi quyền truy cập liên kết

### Đối tượng sử dụng
Producer có tài khoản PRO.

### Mô tả chức năng
Cho phép Producer hủy bỏ hiệu lực của liên kết nghe thử đã gửi cho khách hàng. Sau khi thu hồi, khách hàng không thể tiếp tục nghe nhạc, tải file gốc hay truy cập bất kỳ nội dung nào qua liên kết đó.

### Quy trình nghiệp vụ
1. Producer mở danh mục lịch sử chia sẻ, nhấn nút "Thu hồi" cạnh tên đối tác muốn khóa.
2. Hệ thống hiển thị Modal xác nhận: *"Khách hàng sẽ không thể tiếp tục nghe hoặc tải nhạc."*
3. Sau khi xác nhận:
   - Đánh dấu liên kết là đã thu hồi trong cơ sở dữ liệu.
   - Xóa cache phân phối và ghi nhận blacklist tạm thời trên Redis.
   - Vô hiệu hóa tất cả cookie phiên đang hoạt động của liên kết đó.
   - Phát sự kiện WebSocket tới giao diện khách hàng (nếu đang mở) để cập nhật real-time.
4. Từ thời điểm đó, mọi yêu cầu nghe nhạc, lấy khóa giải mã, hoặc tải file gốc qua token này đều bị chặn HTTP 403.

### Đặc điểm nghiệp vụ

| Đặc điểm | Mô tả |
|:---|:---|
| **Tức thời** | Thu hồi có hiệu lực ngay lập tức, kể cả khi khách đang nghe giữa chừng (cắt ngang phát nhạc) |
| **Độc lập** | Chỉ ảnh hưởng token cụ thể, các liên kết chia sẻ khác của cùng demo cho người khác không bị ảnh hưởng |
| **Idempotent** | Gọi thu hồi nhiều lần trả kết quả thành công, không lỗi trùng lặp |
| **Đồng bộ UI real-time** | Giao diện khách hàng tự động cập nhật: mờ bài hát + dòng chữ "Đã thu hồi" |
| **Cascade khi xóa Demo** | Khi Producer xóa demo gốc, tất cả liên kết chia sẻ của demo đó tự động bị thu hồi |
| **Xoay khóa mã hóa** | Sau khi thu hồi, nếu không còn ai khác đang nghe cùng demo → xoay khóa mã hóa mới |
| **Ghi nhận kiểm toán** | Mọi thao tác thu hồi đều được ghi vào bảng audit (giữ 2 năm) |

### Ràng buộc nghiệp vụ
| Quy tắc | Chi tiết |
|:---|:---|
| Quyền thu hồi | Chỉ Producer sở hữu bản nhạc mới được thu hồi |
| Rate limit | Tối đa 20 / phút / IP + 30 / phút / user |
| Xác thực WebSocket | Khách hàng phải có Temporary Access Token hợp lệ mới subscribe được kênh thông báo |

---

## Tổng quan luồng nghiệp vụ End-to-End

```
Producer tạo Voice Tag (2)
         ↓
Producer tải lên nhạc gốc (1)
         ↓
Hệ thống xử lý: đóng dấu + mã hóa + phân đoạn HLS (1)
         ↓
Producer chia sẻ demo cho khách qua email (3)
         ↓
Khách mở link → nghe thử bảo mật (4) → tải file gốc (5) (nếu được phép)
         ↓
Producer thu hồi liên kết khi cần (6)
```

---

## Tổng hợp giới hạn & Quota theo User

| Hạng mục | Giới hạn |
|:---|:---|
| Số demo ACTIVE đồng thời | 20 / user |
| Tổng dung lượng demo ACTIVE | 5 GB / user |
| Dung lượng file đơn lẻ | 200 MB / file |
| Số Voice Tag active | 50 / user |
| Tổng dung lượng Voice Tag | 200 MB / user |
| Tạo Voice Tag / ngày | 30 / user |
| Email chia sẻ / ngày | 100 email khác nhau / user |
| Lượt phân phối / ngày | 500 / user |
| Lượt tải file gốc / ngày | 10 / session |
| Lượt tải file gốc / tuần | 100 / token |
