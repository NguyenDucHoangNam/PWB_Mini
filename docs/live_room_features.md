# Module 2: Live Room — Mô tả Chức năng Nghiệp vụ

Tài liệu tổng hợp mô tả các chức năng nghiệp vụ của phân hệ **Phòng Nghe nhạc Trực tuyến (Live Room)**. Module này cho phép Producer tạo phòng ảo để nghe nhạc demo đồng bộ cùng khách hàng, đàm thoại video/audio trực tiếp, và tương tác thời gian thực — toàn bộ trong một phiên trải nghiệm bảo mật tối đa 4 giờ.

---

## Mục lục

1. [Khởi tạo & Cấu hình Phòng](#1-khởi-tạo--cấu-hình-phòng)
2. [Tham gia Phòng & Phòng chờ](#2-tham-gia-phòng--phòng-chờ)
3. [Chọn Nguồn phát Nhạc](#3-chọn-nguồn-phát-nhạc)
4. [Trình phát Nhạc Đồng bộ](#4-trình-phát-nhạc-đồng-bộ)
5. [Phân quyền Điều khiển Trình phát](#5-phân-quyền-điều-khiển-trình-phát)
6. [Đàm thoại Trực tiếp WebRTC](#6-đàm-thoại-trực-tiếp-webrtc)
7. [Chat & Biểu cảm Cảm xúc](#7-chat--biểu-cảm-cảm-xúc)
8. [Vòng đời Phòng & Dọn dẹp Tự động](#8-vòng-đời-phòng--dọn-dẹp-tự-động)

---

## 1. Khởi tạo & Cấu hình Phòng

### Đối tượng sử dụng
Producer (Host) có tài khoản PRO.

### Mô tả chức năng
Cho phép Producer tạo phòng nghe nhạc ảo với mã phòng 6 ký tự duy nhất, thiết lập chế độ vào phòng, và tự động mở kênh liên lạc thời gian thực.

### Quy trình nghiệp vụ
1. Producer truy cập bảng điều khiển, nhấn "Khởi tạo phòng Live Room".
2. Chọn chế độ vào phòng:
   - **OPEN (Vào tự do)**: Khách có mã phòng tham gia thẳng.
   - **MODERATED (Kiểm duyệt)**: Khách phải chờ Host phê duyệt mới được vào.
3. Hệ thống sinh mã phòng ngẫu nhiên 6 ký tự viết hoa (ví dụ: `A8B9D1`), loại bỏ các ký tự dễ nhầm (`0`, `O`, `1`, `I`).
4. Lưu thông tin phòng vào cơ sở dữ liệu và đưa trạng thái hoạt động lên cache.
5. Frontend tự động thiết lập kết nối WebSocket thời gian thực.
6. Hiển thị mã phòng ở kích thước lớn kèm nút "Copy mã" để Host chia sẻ cho khách hàng.

### Ràng buộc nghiệp vụ

| Quy tắc | Chi tiết |
|:---|:---|
| Quyền tạo phòng | Chỉ tài khoản PRO mới được tạo phòng |
| Giới hạn phòng | Mỗi Producer chỉ được có tối đa **1 phòng hoạt động** tại bất kỳ thời điểm |
| Số lượng người tham gia | Tối đa **7 người** đồng thời (1 Host + 6 Listener) |
| Sinh mã phòng | Tối đa 3 lần thử sinh mã (chống trùng lặp), thất bại thì báo lỗi |
| Bảo mật kết nối | Kết nối WebSocket yêu cầu Token JWT, tự động ngắt nếu không xác thực trong 10 giây |
| Giới hạn tạo phòng | Tối đa 3 lần tạo phòng / phút / IP |

### Cơ chế chống Phòng ma (Two-Phase TTL)

| Pha | Mô tả |
|:---|:---|
| **Pha 1 — Chờ kết nối (30 giây)** | Sau khi tạo phòng, cache chỉ sống 30 giây |
| **Pha 2 — Hoạt động (4 giờ)** | Khi Host kết nối WebSocket thành công, cache được gia hạn lên 4 giờ |
| **Phòng ma** | Nếu Host không kết nối trong 30 giây → phòng tự động đóng, giải phóng quyền tạo phòng |
| **Tuyến phòng thủ** | Hệ thống quét nền định kỳ 1-2 phút tìm phòng mồ côi để dọn dẹp |

---

## 2. Tham gia Phòng & Phòng chờ

### Đối tượng sử dụng
- **Listener**: Khách hàng muốn vào phòng nghe nhạc.
- **Host**: Người xem danh sách chờ và quyết định phê duyệt.

### Mô tả chức năng
Cho phép khách hàng nhập mã phòng để tham gia. Tùy chế độ phòng, khách vào thẳng hoặc được xếp vào hàng chờ để Host phê duyệt. Hỗ trợ khách vãng lai (không cần đăng ký tài khoản).

### Quy trình nghiệp vụ

#### Chế độ OPEN (Vào tự do)
1. Listener nhập mã phòng 6 ký tự và tên hiển thị.
2. Hệ thống kiểm tra phòng tồn tại, còn chỗ trống (< 7 người).
3. Cấp Token tạm thời (4 giờ) cho Listener và cho vào phòng ngay.
4. Frontend kết nối WebSocket, đăng ký nhận thông tin thành viên và nhạc.

#### Chế độ MODERATED (Kiểm duyệt)
1. Listener nhập mã phòng và tên hiển thị.
2. Hệ thống xếp Listener vào **hàng chờ (Waiting List)**.
3. Host nhận thông báo real-time: *"Có yêu cầu tham gia mới từ {tên}"*.
4. Listener chờ ở màn hình chờ duyệt với spinner xoay.
5. Host xem danh sách chờ, nhấn **Duyệt** hoặc **Từ chối**:
   - **Duyệt**: Listener được chuyển vào phòng, giao diện tự động cập nhật sang phòng Live.
   - **Từ chối**: Listener nhận thông báo và quay về trang nhập mã.

### Các thao tác của Host

| Thao tác | Mô tả |
|:---|:---|
| **Duyệt (Approve)** | Chấp nhận Listener vào phòng, kiểm tra tự động còn chỗ trống |
| **Từ chối (Reject)** | Xóa Listener khỏi hàng chờ, chuyển hướng về trang nhập mã |
| **Kick (Mời ra)** | Đá thành viên ra ngoài, Listener nhận thông báo và bị ngắt kết nối |

### Ràng buộc nghiệp vụ

| Quy tắc | Chi tiết |
|:---|:---|
| Giới hạn cứng | 7 người / phòng (1 Host + 6 Listener) |
| Thời gian chờ | Mỗi yêu cầu chờ duyệt chỉ hiệu lực **5 phút**, quá hạn tự xóa |
| Dọn dẹp tự động | Yêu cầu quá hạn tự động bị loại khi Host tải danh sách chờ |
| Khách vãng lai | Không cần tài khoản, được cấp Token tạm thời với quyền hạn chế (sandbox) |
| Bảo mật Token tạm | Token tạm chỉ được phép truy cập WebSocket và API join/leave, bị chặn mọi API khác |
| Dọn dẹp khi mất kết nối | Listener ngắt kết nối đột ngột → tự động giảm số lượng người, xóa khỏi hàng chờ |
| Giới hạn tham gia | Tối đa 5 lần yêu cầu / phút / IP |

---

## 3. Chọn Nguồn phát Nhạc

### Đối tượng sử dụng
Host (chủ phòng).

### Mô tả chức năng
Cho phép Host chọn bài hát từ thư viện nhạc demo cá nhân làm nguồn phát nhạc chính cho toàn phòng. Khi đổi bài, trình phát của tất cả thành viên đồng loạt reset và tải lại.

### Quy trình nghiệp vụ
1. Host nhấn "Chọn nguồn nhạc" tại phòng Live.
2. Hệ thống mở danh mục hiển thị các tệp nhạc demo đã tải lên (từ Module Secure Audio Streaming).
3. Host chọn bài hát và xác nhận.
4. Hệ thống kiểm tra quyền sở hữu file nhạc và trạng thái sẵn sàng.
5. Reset trạng thái trình phát toàn phòng: dừng nhạc, đặt vị trí về 0:00.
6. Gửi thông báo WebSocket tới toàn bộ thành viên.
7. Trình duyệt của mọi người: dừng nhạc cũ (fade-out mượt), vẽ lại biểu đồ hình sóng mới, nạp luồng HLS mới.

### Ràng buộc nghiệp vụ

| Quy tắc | Chi tiết |
|:---|:---|
| Quyền chọn nhạc | Chỉ Host mới được đổi bài, Listener bị từ chối |
| Sở hữu file | File nhạc phải thuộc quyền sở hữu của Host |
| Trạng thái file | File phải ở trạng thái sẵn sàng (đã xử lý xong HLS & waveform) |
| Reset playback | Khi đổi bài → trình phát tự động dừng, vị trí về 0:00 |
| Chống nổ âm | Fade-out 100-200ms trước khi hủy luồng cũ (chống tiếng click/pop) |
| Giới hạn | Tối đa 10 lần đổi bài / phút / IP |

---

## 4. Trình phát Nhạc Đồng bộ

### Đối tượng sử dụng
Host (mặc định) hoặc Listener được ủy quyền điều khiển.

### Mô tả chức năng
Cho phép người điều khiển Play/Pause/Seek bài hát, hệ thống đồng bộ trạng thái phát nhạc tới toàn bộ phòng qua WebSocket với thuật toán bù trừ độ trễ mạng, đảm bảo mọi người nghe nhạc trùng khớp đến từng mili giây.

### Quy trình nghiệp vụ
1. Người điều khiển nhấn Play/Pause hoặc kéo tua thanh trượt.
2. Frontend gửi hành động kèm vị trí thời gian và mốc giờ gửi lên WebSocket.
3. Backend xác thực quyền, cập nhật cache và broadcast tới toàn phòng.
4. Trình duyệt của mọi thành viên nhận tin, tính toán bù trừ độ trễ mạng và điều chỉnh trình phát cục bộ.

### Đặc điểm nổi bật

#### Chất lượng âm thanh phòng thu
- Luồng nhạc demo chạy HLS trên thẻ HTML5 Audio cục bộ, **tách biệt hoàn toàn** khỏi luồng đàm thoại WebRTC.
- Nhạc giữ nguyên chất lượng gốc (AAC 320kbps), không bị suy giảm bởi nén tiếng nói.

#### Thuật toán bù trừ độ trễ mạng
- Sử dụng `clientSendTime` (mốc giờ phía Client người gửi) thay vì đồng hồ server → **tránh hoàn toàn lỗi lệch múi giờ giữa Client và Server**.
- Tính toán: `targetPosition = currentTime + (thời gian truyền mạng)`.
- Giới hạn cận trên `duration - 0.1 giây` để tránh lỗi treo trình phát trên thiết bị di động.

#### Tự động hiệu chỉnh lệch pha vi mô (Micro-Drift Management)
- Khi nhạc đang phát, Frontend của người điều khiển định kỳ mỗi **5-10 giây** gửi gói tin đồng bộ.
- Listener so khớp vị trí: nếu lệch > 250ms → tự động vi chỉnh ngầm (Silent Seek) không hiện loading.
- **Bộ lọc nhiễu mạng (Jitter Filter)**: Nếu độ trễ đột ngột vọt cao > 50% so với trung bình 3-5 frame trước → bỏ qua frame đó.

#### Đồng bộ khi mới vào phòng / Reconnect
- Thành viên mới hoặc reconnect → gọi API lấy trạng thái phát nhạc hiện tại kèm đầy đủ thông tin bài hát, tự động đồng bộ ngay.

### Ràng buộc nghiệp vụ

| Quy tắc | Chi tiết |
|:---|:---|
| Quyền điều khiển | Chỉ Host hoặc người được ủy quyền |
| Kiểm duyệt biên độ | Server kiểm tra giá trị seek: từ chối nếu < 0 hoặc > thời lượng bài hát |
| Chống dội lệnh | Nếu nhận broadcast từ chính mình → bỏ qua không tự phát lại |
| Debounce kéo tua | Chỉ gửi lệnh khi thả chuột khỏi thanh Seek |
| Mất kết nối | Listener tự động dừng nhạc, kết nối lại thì đồng bộ ngay |
| Giới hạn | Tối đa 15 lệnh / 10 giây / kết nối |

---

## 5. Phân quyền Điều khiển Trình phát

### Đối tượng sử dụng
Host (chủ phòng).

### Mô tả chức năng
Cho phép Host ủy quyền điều khiển Play/Pause/Seek nhạc cho một hoặc tất cả Listener trong phòng. Thay đổi quyền được phản ánh tức thì trên giao diện: mở khóa hoặc khóa nút bấm trình phát.

### Hai chế độ phân quyền

| Chế độ | Mô tả |
|:---|:---|
| **Cá nhân** | Host chọn Listener cụ thể, cấp hoặc thu hồi quyền riêng lẻ |
| **Toàn phòng** | Host bật/tắt công tắc cho phép tất cả Listener điều khiển nhạc |

### Quy trình nghiệp vụ
1. Host mở danh sách thành viên online.
2. Click icon "Cấp quyền" cạnh tên Listener cần ủy quyền (hoặc bật Switch toàn phòng).
3. Hệ thống ghi nhận và gửi thông báo WebSocket broadcast.
4. Giao diện của Listener được ủy quyền: các nút Play/Pause/Seek chuyển từ mờ/khóa sang hoạt động.
5. Khi thu hồi quyền: nút bấm bị khóa trở lại.

### Ràng buộc nghiệp vụ

| Quy tắc | Chi tiết |
|:---|:---|
| Phân quyền chỉ Host | Listener được ủy quyền chỉ điều khiển nhạc, không được phân quyền cho người khác |
| Bảo toàn khi reconnect | Quyền điều khiển được giữ lại khi Listener mất mạng tạm thời rồi reconnect |
| Dọn dẹp khi rời phòng | Quyền bị xóa khi Listener chủ động rời phòng hoặc bị Host kick |
| Dọn dẹp khi đóng phòng | Toàn bộ quyền ủy quyền bị xóa sạch khi phòng đóng |
| Đồng bộ đa máy chủ | Thay đổi quyền được đồng bộ tức thì giữa tất cả máy chủ Backend trong cụm |
| Giới hạn | Tối đa 10 lần thay đổi quyền / phút / IP |

---

## 6. Đàm thoại Trực tiếp WebRTC

### Đối tượng sử dụng
Toàn bộ thành viên trong phòng (Host + Listener đã được duyệt).

### Mô tả chức năng
Cho phép các thành viên đàm thoại giọng nói và chia sẻ camera trực tiếp qua kết nối ngang hàng (Peer-to-Peer). Backend chỉ đóng vai trò trung chuyển tín hiệu báo hiệu, không xử lý dữ liệu audio/video.

### Quy trình nghiệp vụ
1. Khi vào phòng, Frontend xin quyền truy cập Micro/Camera và lấy cấu hình kết nối từ Backend.
2. Frontend thiết lập kết nối ngang hàng tới từng thành viên khác (mô hình Mesh P2P).
3. Các bên trao đổi tín hiệu SDP và ICE Candidates qua WebSocket.
4. Kết nối P2P được thiết lập → âm thanh và hình ảnh truyền trực tiếp giữa các trình duyệt.

### Đặc điểm nổi bật

| Đặc điểm | Mô tả |
|:---|:---|
| **Kiến trúc Mesh P2P** | Mỗi client kết nối trực tiếp đến tối đa 6 client khác. Tối đa 21 đường truyền toàn phòng |
| **Backend không xử lý media** | Backend chỉ chuyển tiếp văn bản báo hiệu, không tốn băng thông/CPU xử lý audio/video |
| **TURN Server bảo mật** | Mật khẩu kết nối TURN được sinh động mỗi 24 giờ, chống kẻ xấu truyền dữ liệu lậu |
| **Nhận diện người đang nói** | Viền sáng quanh avatar khi phát hiện giọng nói > ngưỡng dB liên tục 200ms |
| **Bật/Tắt Micro & Camera** | Tắt track cục bộ để bảo vệ riêng tư, gửi trạng thái cập nhật cho các bên hiển thị icon tắt |
| **Chống xung đột báo hiệu** | Thuật toán Perfect Negotiation: so sánh ID để phân vai, tránh hai bên gửi Offer cùng lúc treo kết nối |
| **Phát hiện Peer offline** | Server kiểm tra trước khi chuyển tín hiệu; nếu đối phương offline → thông báo ngay để hủy kết nối sớm |

### Ràng buộc nghiệp vụ

| Quy tắc | Chi tiết |
|:---|:---|
| Giới hạn 7 người | Do kiến trúc Mesh, giới hạn 7 người đảm bảo chất lượng đàm thoại |
| Giới hạn video | Mỗi luồng video tối đa 300kbps, 360-480p, 15-20fps để bảo vệ băng thông Host |
| Ẩn Tab tiết kiệm | Khi tab bị ẩn, tạm dừng render video tiết kiệm 70% GPU |
| Tự dọn dẹp | Khi ICE connection state = failed/disconnected → tự đóng PeerConnection và xóa khung hình |
| Từ chối quyền camera | Người dùng từ chối → vẫn vào phòng nhưng không có tiếng/hình |
| Giới hạn tín hiệu | Tối đa 100 frame báo hiệu / phút / kết nối |

---

## 7. Chat & Biểu cảm Cảm xúc

### Đối tượng sử dụng
Toàn bộ thành viên online trong phòng.

### Mô tả chức năng
Cho phép thành viên gửi tin nhắn chat văn bản tạm thời và biểu cảm cảm xúc nhanh (Emoji Reactions) với hiệu ứng hoạt hình bay lên màn hình.

### Hai loại tương tác

#### Chat văn bản (Ephemeral Chat)
- Thành viên nhập tin nhắn và gửi qua WebSocket → broadcast tức thời tới toàn phòng.
- **Tuyệt đối không lưu lịch sử**: Tin nhắn chỉ tồn tại trên bộ nhớ trình duyệt.
- Khi tải lại trang hoặc đóng phòng → lịch sử chat biến mất vĩnh viễn.
- Tối đa **100 tin nhắn gần nhất** trên giao diện (tin cũ nhất bị xóa khi vượt quá).

#### Emoji Reactions (Biểu cảm bay lên)
- Hỗ trợ 4 biểu tượng nhanh: 🔥 (Lửa), 👍 (Thích), 👏 (Vỗ tay), 💯 (Tuyệt vời).
- Khi nhấn emoji → broadcast → tất cả màn hình hiển thị emoji bay lên từ góc dưới, mờ dần trong 3 giây.
- Emoji tự động bị xóa khỏi giao diện ngay khi animation kết thúc (chống rò rỉ bộ nhớ).

### Ràng buộc nghiệp vụ

| Quy tắc | Chi tiết |
|:---|:---|
| Không lưu DB | Tin nhắn không được lưu vào bất kỳ cơ sở dữ liệu hay cache nào |
| Bảo mật XSS | Nội dung tin nhắn tự động escape HTML, nghiêm cấm render thô |
| Giới hạn chat | Tối đa 200 ký tự / tin nhắn |
| Giới hạn gửi TEXT | 30 tin nhắn / phút / kết nối |
| Giới hạn gửi REACTION | 120 lần / phút / kết nối |
| Xử lý vượt quota | Server lặng lẽ bỏ qua tin nhắn vi phạm và gửi cảnh báo riêng, **không ngắt kết nối** |
| Throttle phía Client | Tối đa 5 click emoji / giây, click thêm chỉ hiện cục bộ không gửi đi |
| Mất kết nối | Khung chat bị khóa, lịch sử cũ giữ nguyên, tin gửi offline bị bỏ qua |

---

## 8. Vòng đời Phòng & Dọn dẹp Tự động

### Đối tượng sử dụng
- **Host**: Đóng phòng chủ động.
- **Hệ thống**: Tự động phát hiện phòng bỏ hoang để dọn dẹp.

### Mô tả chức năng
Quản lý toàn bộ vòng đời phòng ảo: Host đóng phòng chủ động, ân hạn khi Host mất mạng, tự động dọn dẹp phòng trống, và giải phóng toàn bộ tài nguyên.

### Ba kịch bản đóng phòng

#### A. Host chủ động đóng phòng
1. Host nhấn "Đóng phòng" → xác nhận.
2. Hệ thống:
   - Cập nhật trạng thái phòng thành `CLOSED` trong cơ sở dữ liệu.
   - Ghi nhận sự kiện kết thúc phòng để phân tích thống kê.
   - Gửi tin nhắn `ROOM_CLOSED` qua WebSocket tới toàn bộ thành viên.
   - Đợi 500ms rồi ép ngắt toàn bộ kết nối WebSocket trên mọi máy chủ trong cụm.
   - Xóa sạch cache: trạng thái phòng, nhạc, thành viên, hàng chờ, phân quyền.
3. Giao diện Listener hiển thị Modal cảnh báo: *"Buổi nghe thử đã kết thúc"*. Sau 5 giây tự động chuyển về trang nhập mã.

#### B. Host mất kết nối — Ân hạn 5 phút (Grace Period)
1. Khi Host bị ngắt kết nối đột ngột → phòng chuyển sang trạng thái `INACTIVE_HOST`.
2. **Đóng băng luồng nhạc**: Hệ thống ép dừng nhạc toàn phòng ngay lập tức (tránh Listener nghe phần buffer cũ).
3. Bắt đầu đếm ngược **5 phút** ân hạn.
4. Nếu Host kết nối lại trong 5 phút → phòng trở về `ACTIVE`, hủy đếm ngược.
5. Nếu quá 5 phút → phòng tự động bị đóng vĩnh viễn.

#### C. Phòng trống quá lâu — AFK 15 phút
1. Khi thành viên cuối cùng rời phòng (số người = 0) → bắt đầu đếm ngược **15 phút**.
2. Nếu có người mới vào trước 15 phút → hủy đếm ngược.
3. Nếu quá 15 phút không có ai → phòng tự động đóng.

### Bộ quét dọn dẹp nền
- Chạy định kỳ **mỗi 1 phút** để phát hiện phòng quá hạn.
- Kiểm tra kép trạng thái trước khi đóng: nếu Host vừa reconnect → hủy bỏ dọn dẹp.
- Chỉ 1 máy chủ trong cụm thực hiện quét tại một thời điểm (tránh trùng lặp).

### Ràng buộc nghiệp vụ

| Quy tắc | Chi tiết |
|:---|:---|
| Quyền đóng phòng | Chỉ Host mới được đóng phòng |
| Ép thoát Listener | Khi đóng phòng, dừng camera/micro, ngắt WebRTC, chuyển hướng trang |
| Sự kiện Analytics | Mỗi lần đóng phòng đều ghi nhận: mã phòng, Host, thời gian bắt đầu/kết thúc, số khách tối đa |
| Thời lượng tối đa | Phòng tự động hết hạn sau **4 giờ** (TTL cache) |
| Giới hạn đóng phòng | Tối đa 5 lần / phút / IP |

### Trạng thái vòng đời Phòng

```
ACTIVE → INACTIVE_HOST (Host mất kết nối, ân hạn 5 phút)
   ↓            ↓
CLOSED      CLOSED (Quá 5 phút / Phòng trống 15 phút / Hết 4 giờ)
```

---

## Tổng quan Luồng nghiệp vụ End-to-End

```
Host (Producer PRO) tạo phòng Live Room (1)
         ↓
Chia sẻ mã phòng 6 ký tự cho khách hàng
         ↓
Khách nhập mã → vào phòng hoặc chờ duyệt (2)
         ↓
Host chọn bài nhạc demo từ thư viện (3)
         ↓
Cùng nghe nhạc đồng bộ chất lượng phòng thu (4)
         ↓
Host ủy quyền điều khiển cho khách nếu cần (5)
         ↓
Đàm thoại trao đổi qua video/audio P2P (6)
         ↓
Tương tác chat và biểu cảm cảm xúc (7)
         ↓
Host đóng phòng hoặc hệ thống tự dọn dẹp (8)
```

---

## Tổng hợp Giới hạn & Quota

| Hạng mục | Giới hạn |
|:---|:---|
| Phòng hoạt động / Producer | 1 phòng |
| Người tham gia / phòng | 7 người (1 Host + 6 Listener) |
| Thời lượng phòng tối đa | 4 giờ |
| Ân hạn Host mất kết nối | 5 phút |
| Phòng trống tự đóng | 15 phút |
| Chờ kết nối ban đầu (Two-Phase) | 30 giây |
| Thời gian chờ duyệt | 5 phút / yêu cầu |
| Token tạm thời Listener | 4 giờ |
| Video bandwidth / luồng | 300 kbps, 360-480p, 15-20 fps |
| Tin nhắn chat | 200 ký tự, 30 tin / phút |
| Emoji Reactions | 120 lần / phút |
| Tin nhắn hiển thị | 100 tin gần nhất |
