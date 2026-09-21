# Bộ Câu Hỏi Phỏng Vấn: Giai Đoạn 1 — Nền Tảng Dùng Chung (Shared Foundation)

> Thư mục này tổng hợp các câu hỏi phỏng vấn thực chiến chuyên sâu cấp độ Senior Backend / Tech Lead dành cho **Giai đoạn 1: Nền Tảng Dùng Chung**.  
> Tài liệu gốc đối chiếu: [`docs/interview-notes/01-giai-doan-1-shared-foundation.md`](../../interview-notes/01-giai-doan-1-shared-foundation.md)

---

## 📋 Danh Sách Câu Hỏi Ôn Luyện

| STT | Câu Hỏi Trọng Tâm | Trạng Thái | File Chi Tiết |
| :---: | :--- | :---: | :--- |
| **01** | Tại sao chọn Clean Architecture / Hexagonal thay vì mô hình 3 lớp truyền thống? Giải quyết bài toán gì khi mở rộng? | ✅ Đã soạn | [👉 `cau-01-tai-sao-chon-clean-architecture.md`](./cau-01-tai-sao-chon-clean-architecture.md) |
| **02** | Tại sao cùng một đối tượng User phải tách thành 4 loại Model khác nhau (`UserRequest/Response`, `LoginCommand`, `User`, `UserJpaEntity`)? Trade-offs là gì? | ✅ Đã soạn | [👉 `cau-02-tach-4-loai-object-model.md`](./cau-02-tach-4-loai-object-model.md) |
| **03** | Modular Monolith là gì? Khác gì so với Monolith truyền thống và Microservices? Vì sao dự án chọn Modular Monolith? | ✅ Đã soạn | [👉 `cau-03-modular-monolith.md`](./cau-03-modular-monolith.md) |
| **04** | Phân tích cấu trúc 4 tầng (Domain, Application, Infrastructure, API)? Trách nhiệm cốt lõi và Dependency Rule? | ✅ Đã soạn | [👉 `cau-04-cau-truc-4-tang.md`](./cau-04-cau-truc-4-tang.md) |
| **05** | Mô hình Ports & Adapters hoạt động thế nào? Phân biệt Inbound vs Outbound Ports/Adapters qua ví dụ PWB_MiNi? | ✅ Đã soạn | [👉 `cau-05-mo-hinh-ports-va-adapters.md`](./cau-05-mo-hinh-ports-va-adapters.md) |
| **06** | Tại sao dùng Flyway Migration thay vì Hibernate `ddl-auto: update`? Cơ chế `out-of-order: true` xử lý xung đột ra sao? | ✅ Đã soạn | [👉 `cau-06-flyway-vs-hibernate-ddl.md`](./cau-06-flyway-vs-hibernate-ddl.md) |
| **07** | Hạ tầng AWS S3, Presigned URL & Toàn bộ kiến trúc xử lý lỗi, bảo mật tệp nặng (Dung lượng, Path Traversal, Stored XSS, Magic Bytes, Retryable, StorageCleaner)? | ✅ Đã soạn | [👉 `cau-07-ha-tang-s3-presigned-url-va-toi-uu-bang-thong.md`](./cau-07-ha-tang-s3-presigned-url-va-toi-uu-bang-thong.md) |
| **08** | Triển khai Rate Limiting trong dự án thế nào? Những thách thức kỹ thuật lớn nhất và cách xử lý (NAT, Filter Order, Lua Script, Fail-Open, Scope, STOMP)? | ✅ Đã soạn | [👉 `cau-08-trien-khai-rate-limiting-va-thach-thuc.md`](./cau-08-trien-khai-rate-limiting-va-thach-thuc.md) |
| **10** | Toàn diện kiến trúc & 4 trụ cột ứng dụng của Redis trong dự án (Token Blacklist, RTR & Theft Detection, Brute-force lock, Rate Limit Lua script, Cache TTS)? | ✅ Đã soạn | [👉 `cau-10-toan-dien-kien-truc-va-ung-dung-redis.md`](./cau-10-toan-dien-kien-truc-va-ung-dung-redis.md) |
| **11** | Rich Domain Model khác gì Anemic Domain Model? Bảo vệ Invariants thế nào (private constructor, `rehydrate`)? | ⏳ Chờ soạn | `cau-11-rich-domain-model-va-invariants.md` |
| **12** | Value Object là gì? Tại sao tạo riêng class `EmailAddress`, `Password` thay vì dùng `String`? Đảm bảo Immutability thế nào? | ⏳ Chờ soạn | `cau-12-value-object-va-immutability.md` |
| **13** | Tại sao thiết kế hệ thống lỗi Protocol-Agnostic (`ErrorCode`, `BusinessException`) không chứa mã HTTP? | ⏳ Chờ soạn | `cau-13-protocol-agnostic-error-handling.md` |
| **14** | Kỹ thuật IP Spoofing là gì? Tại sao `ClientIpResolver` không tin `X-Forwarded-For` mà phải qua `trustedProxies`? | ⏳ Chờ soạn | `cau-14-ip-spoofing-va-trusted-proxies.md` |
| **15** | Tại sao dùng Bearer Token thay vì Session Cookie? Khi nào cần CSRF và tại sao ở đây tắt CSRF an toàn? | ⏳ Chờ soạn | `cau-15-bearer-token-va-csrf-protection.md` |
| **16** | Tại sao trong `CorrelationIdFilter` bắt buộc phải có `finally { MDC.clear(); }`? Hậu quả gì với Tomcat Thread Pool? | ⏳ Chờ soạn | `cau-16-correlation-id-va-mdc-cleanup.md` |



