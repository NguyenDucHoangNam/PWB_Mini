# shared-infrastructure

Adapter module chia sẻ: **Redis**, **Kafka**, **Mail**, **Storage**, **Outbox**.

Mỗi concern có `config/` riêng, đăng ký bean qua `@AutoConfiguration` — không scan package ở root `bootstrap`.

| Concern | Package | Mục đích |
|---|---|---|
| Redis | `com.pwb.infra.redis` | Cache, distributed lock, rate-limit |
| Kafka | `com.pwb.infra.kafka` | Producer / Consumer, topic config |
| Mail | `com.pwb.infra.mail` | SMTP outbound |
| Storage | `com.pwb.infra.storage` | Object storage (S3-compatible) |
| Outbox | `com.pwb.infra.outbox` | Transactional outbox + relay |

---

## Flyway version convention

Spring Boot Flyway mặc định scan `classpath:db/migration` từ **mọi jar**. Nếu 2 module cùng dùng `V1__...` → Flyway throw lỗi khi boot.

**Quy ước**: mỗi module pick một range số riêng.

| Module | Range |
|---|---|
| `shared-infrastructure` | `V100__` → `V199__` |
| `modules/iam` | `V1__` → `V99__` |
| `modules/order` (tương lai) | `V200__` → `V299__` |
| `modules/payment` (tương lai) | `V300__` → `V399__` |

Mỗi range có 100 slot. Hết range → bump range, cập nhật bảng này.

**Naming**: `V<version>__<short_snake_case>.sql`. Ví dụ: `V100__create_outbox_events.sql`.

**Thêm module mới**: pick range chưa dùng → đặt file vào `modules/<new>/src/main/resources/db/migration/` → cập nhật bảng.

**Lưu ý**: không tự ý đổi version sau khi merge — sẽ vỡ Flyway history. Cần hỏi trước.

---

## Outbox

Schema trong `V100__create_outbox_events.sql`. Status: `PENDING` / `PROCESSING` / `SENT` / `FAILED`.

Relay config: `pwb.outbox.relay.{poll-interval-ms, batch-size}`. Retry: `pwb.outbox.retry.{max-attempts, backoff-seconds}`.

---

## Build

```bash
cd pwb-refactor/Backend
mvn -pl shared/shared-infrastructure -am clean compile
```
