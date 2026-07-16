# PWB Backend (Modular Monolith)

Producer Workbench Backend — re-architecture dưới dạng **Modular Monolith** theo chuẩn Hexagonal Architecture (api / core / infrastructure).

## Modules

| Module | Vai trò |
|---|---|
| `shared-kernel` | Common types: exception, i18n, security contract, utilities |
| `bootstrap` | Spring Boot main class, application config |
| `modules/iam` | Identity & Access (Auth, User, Role, OAuth, JWT) |
| `modules/voice` | Voice Tag (TTS, quota, storage) |
| `modules/notification` | Outbox + Kafka + Email |

## Tech Stack

- Java 21
- Spring Boot 4.1.x
- Maven multi-module
- PostgreSQL (multi-schema)

## Quy tắc dependency

- `shared-kernel` KHÔNG depend vào bất kỳ business module nào
- Business module chỉ depend vào `shared-kernel` + `api/` của module khác
- `bootstrap` depend tất cả module

## Status

Sprint S0 — project skeleton rỗng.