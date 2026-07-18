# PWB Backend (Modular Monolith)

Producer Workbench Backend — re-architecture dưới dạng **Modular Monolith** theo chuẩn Hexagonal Architecture (api / core / infrastructure).

## Modules

| Module | Vai trò | Status |
|---|---|:---:|
| `shared-kernel` | Common types: exception, i18n, security contract, utilities | ✅ |
| `shared-web` | Web layer chung: `ApiResponse`, `MessageResolver`, `GlobalExceptionHandler` | ✅ |
| `bootstrap` | Spring Boot main class, application config | ✅ |
| `modules/iam` | Identity & Access (Auth, User, Role, OAuth, JWT, OTP) | ✅ |
| `modules/outbox` | Transactional outbox + Kafka relay (module dùng chung) | [ ] |
| `modules/notification` | Email consumer (Thymeleaf + SMTP, listen Kafka) | [ ] |
| `modules/voice` | Voice Tag (TTS, quota, storage) | [-] **REVOKED — redesign từ đầu** |

> **Cập nhật 2026-07-18:** Module `voice` bị revoke. Sếp muốn thiết kế lại logic business lẫn luồng code. Sprint tương ứng (`S3`) đã xóa khỏi `SPRINTS.md`. Module folder được giữ skeleton, không đụng đến khi chưa có sprint mới.
>
> **Module mới `outbox`:** tách riêng khỏi `notification` để outbox là cơ chế dùng chung (IAM, Voice tương lai, Billing tương lai đều có thể dùng). Notification giờ chỉ là **consumer** của Kafka topic — single-responsibility.

## Tech Stack

- Java 21
- Spring Boot 4.1.x
- Maven multi-module
- PostgreSQL (multi-schema)
- Redis (OTP rate-limit, brute-force lockout, rate-limit token-bucket)
- Kafka KRaft mode (outbox relay từ I1.3)
- MailHog local (SMTP :1025 + UI :8025, từ I1.8)

## Quy tắc dependency

- `shared-kernel` KHÔNG depend vào bất kỳ business module nào
- `shared-web` depend `shared-kernel` + Spring Web
- Module nghiệp vụ (`iam`, `outbox`, `notification`) chỉ depend `shared-kernel` + `shared-web` + `api/` module khác (qua facade/writer interface)
- `outbox` là module generic, KHÔNG depend `notification` — consumer tự listen Kafka topic
- `bootstrap` depend tất cả module

## Status

Sprint S2 — IAM foundation đã xong. Đang chuyển sang track mới: **I0 (IAM Harden) → I1 (Notification + Outbox + Kafka) → I3 (Cleanup)**. Chi tiết xem `../SPRINTS.md` ở root.