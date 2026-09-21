# Bộ Câu Hỏi Phỏng Vấn: Giai Đoạn 3 — Module Audio (Outbox Pattern & Xử Lý Bất Đồng Bộ)

> Thư mục này tổng hợp các câu hỏi phỏng vấn thực chiến chuyên sâu cấp độ Senior Backend / Tech Lead / Distributed Systems Architect dành cho **Giai đoạn 3: Bất Đồng Bộ & Xử Lý File Nặng (Module Audio)**.  
> Tài liệu gốc đối chiếu: [`docs/interview-notes/03-giai-doan-3-module-audio.md`](../../interview-notes/03-giai-doan-3-module-audio.md) và mã nguồn hạ tầng Outbox tại [`shared-infrastructure`](../../../Backend/shared/shared-infrastructure/).

---

## 📋 Danh Sách Câu Hỏi Trọng Tâm

| STT | Câu Hỏi Trọng Tâm | Trạng Thái | File Chi Tiết |
| :---: | :--- | :---: | :--- |
| **01** | **Toàn diện về Transactional Outbox Pattern & Kafka**: Khi hệ thống vừa phải cập nhật cơ sở dữ liệu vừa phải phát thông điệp sang Message Broker (Kafka), bạn giải quyết bài toán Dual-Write bằng mẫu thiết kế Transactional Outbox như thế nào để bảo đảm tính nguyên tử (Atomicity)? Cơ chế In-Memory Nudge giúp triệt tiêu độ trễ Polling ra sao? Hệ thống xử lý tranh chấp khi Scale-out nhiều node (`FOR UPDATE SKIP LOCKED`), phục hồi khi node đột tử (Distributed Lease 60s), và bảo đảm thứ tự tuần tự tuyệt đối trong Kafka như thế nào? | ✅ Đã soạn | [👉 `cau-01-outbox-pattern-va-kafka.md`](./cau-01-outbox-pattern-va-kafka.md) |
| **02** | **Toàn diện về Kiến Trúc Xử Lý Bất Đồng Bộ & Vận Hành Heavy Worker**: Tại sao các tác vụ xử lý tệp nặng và tính toán CPU cao (như render âm thanh FFmpeg) bắt buộc phải xử lý bất đồng bộ? Bạn đã thiết kế luồng xử lý bất đồng bộ tổng thể từ lúc tiếp nhận request, bộ đệm thông điệp, worker xử lý ngầm cho tới phản hồi kết quả realtime như thế nào? Trong quá trình đó, bạn cấu hình Kafka Consumer ra sao để chống thảm họa Rebalance Storm, quản trị Database Connection Pool như thế nào để không làm sập các API Web khác, và xử lý các tình huống biên (Idempotent, tệp mồ côi, lỗi độc lập) ra sao? | ✅ Đã soạn | [👉 `cau-02-toan-bo-kien-truc-xu-ly-bat-dong-bo.md`](./cau-02-toan-bo-kien-truc-xu-ly-bat-dong-bo.md) |
| **03** | **Toàn diện về Vai Trò & 2 Đường Ống Xử Lý Của Apache Kafka**: Trong dự án PWB_MiNi, bạn sử dụng Apache Kafka cho những mục đích gì? Phân tích 2 đường ống sự kiện (xử lý âm thanh FFmpeg và gửi email/thông báo). Cấu hình Producer và Consumer ra sao để xử lý lỗi với Dead Letter Topic (DLT), Exponential Backoff Retry, và giải quyết triệt để thảm họa Rebalance Storm? | ✅ Đã soạn | [👉 `cau-03-toan-dien-vai-tro-va-ung-dung-kafka.md`](./cau-03-toan-dien-vai-tro-va-ung-dung-kafka.md) |
