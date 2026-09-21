# Tự code tay lại PWB — cách dùng bộ hướng dẫn này

Bộ này **không phải kế hoạch refactor**. Dự án gốc giữ nguyên, không đụng vào. Bạn mở một project Spring Boot trống bên cạnh và gõ lại từng chức năng bằng tay, dùng dự án gốc làm đáp án.

## Vòng lặp cho mỗi chức năng

1. **Đọc mục chức năng trong file hướng dẫn** — chỉ đọc phần "Luồng thật" và "Bẫy". Chưa mở code gốc.
2. **Chạy bản gốc, gọi thử endpoint bằng curl** để thấy request/response thật trông như thế nào.
3. **Tự code** theo các chặng A → B → C. Mỗi chặng phải chạy được bằng curl trước khi sang chặng sau.
4. **Chỉ sau khi chạy được** mới mở file gốc ra so sánh. Ghi lại chỗ mình nghĩ khác.
5. Sang chức năng tiếp theo.

Quy tắc duy nhất: **không copy-paste**. Gõ tay. Chỗ nào không nhớ thì đọc lại mục "Luồng thật" chứ không mở code gốc.

## Chạy bản gốc để đối chiếu

```bash
cd /d/Learning/Project/PWB_MiNi && docker compose up -d postgres redis kafka
```

```bash
cd /d/Learning/Project/PWB_MiNi && mvn -o -f Backend/pom.xml install -Dmaven.test.skip=true
```

```bash
cd /d/Learning/Project/PWB_MiNi && mvn -o -f Backend/pom.xml -pl bootstrap spring-boot:run -Dspring-boot.run.workingDirectory="D:/Learning/Project/PWB_MiNi" -Dspring-boot.run.jvmArguments="-Dserver.port=8098 -Djdk.net.unixdomain.tmpdir= -Dpwb.iam.jwt.secret=$(openssl rand -base64 48) -Dspring.data.redis.password=$(grep '^REDIS_PASSWORD=' .env | cut -d= -f2-)"
```

Bốn cờ đó đều bắt buộc; thiếu cái nào cũng ra lỗi trông như bug ứng dụng. Tài khoản test: `pro1@gmail.com` / `@NamHoang511`, token nằm ở `data.accessToken`.

## Khung project của bạn

Phẳng theo tầng, một module Maven duy nhất:

```
pwb-mini/
  src/main/java/com/pwb/mini/
    MiniApplication.java
    config/        SecurityConfig, JwtAuthFilter, WebConfig, JpaAuditConfig
    common/        ApiResponse, ApiException, ErrorCode, ErrorCategory, GlobalExceptionHandler, BaseEntity, CurrentUser
    enums/
    entity/
    repository/
    service/
    controller/
    dto/request/   dto/response/
  src/main/resources/
    application.yml
    db/migration/
```

Đặt tên DTO theo `<NghiệpVụ><HànhĐộng>Request` (`AuthLoginRequest`, `SongCreateRequest`) — package phẳng không nói được nghiệp vụ nên tên phải nói.

## Chặng 0 — nền chung (làm một lần, trước F1)

Bốn thứ này mọi chức năng sau đều dùng. Code trước, không có chúng thì mọi endpoint sau đều lệch.

**`common/ErrorCode` + `ErrorCategory`** — bản gốc: `Backend/shared/shared-kernel/.../exception/`.
`ErrorCategory` là enum 9 giá trị: `VALIDATION, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, METHOD_NOT_ALLOWED, CONFLICT, TOO_MANY_REQUESTS, BUSINESS, INTERNAL`. `ErrorCode` là interface 3 hàm: `code()`, `defaultMessage()`, `category()`. Mỗi module gốc có một enum implement nó (`IamErrorCode`, `AudioErrorCode`, `LiveroomErrorCode`) — bạn làm **một** enum `AppErrorCode` duy nhất.

Ánh xạ category → HTTP (bản gốc `WebErrorMapper`):

| category | HTTP |
|---|---|
| VALIDATION | 400 |
| UNAUTHORIZED | 401 |
| FORBIDDEN | 403 |
| NOT_FOUND | 404 |
| METHOD_NOT_ALLOWED | 405 |
| CONFLICT | 409 |
| TOO_MANY_REQUESTS | 429 |
| **BUSINESS** | **422** |
| INTERNAL | 500 |

`BUSINESS → 422` là điểm hay bị bỏ sót: nghiệp vụ từ chối khác với dữ liệu sai.

**`common/ApiResponse<T>`** — 7 field: `success, data, message, traceId, code, error, timestamp`. Static factory `success(data)`, `success(message, data)`, `error(errorCode)`, `error(code, message, errorDetails)`. Mọi controller trả `ApiResponse`, không bao giờ trả entity trần.

**`common/GlobalExceptionHandler`** — `@RestControllerAdvice`, bắt `ApiException` (đọc category ra HTTP), `MethodArgumentNotValidException` (gom field error vào `error`), và `Exception` (500, không lộ stack trace ra response).

**`common/BaseEntity`** — `@MappedSuperclass`: `createdAt`, `updatedAt`, `createdBy`, `updatedBy`, `deleted`, `version`. Bật `@EnableJpaAuditing`. Mọi bảng trong dự án gốc đều có đủ 6 cột này.

**Kiểm chứng chặng 0**: một endpoint `GET /api/v1/ping` trả `ApiResponse.success("pong")`, và một endpoint cố tình ném `ApiException(NOT_FOUND)` trả đúng 404 với `code` trong body.

## Thứ tự chức năng

| File | Chức năng | Ghi chú |
|---|---|---|
| [01-iam.md](01-iam.md) | F1–F12 | Làm hết trước khi sang audio |
| [02-audio.md](02-audio.md) | F13–F19 | Cần F1–F5 để có token |
| [03-liveroom.md](03-liveroom.md) | F20–F28 | Cần audio để có bài hát phát trong phòng |

Ba mốc dừng được: sau **F9** (đã có toàn bộ auth), sau **F16** (đã có upload + phát nhạc), sau **F26** (đã có phòng live không realtime).

## Hạ tầng: thêm dần, không thêm trước

Dự án gốc dùng Postgres + Redis + Kafka + S3 + ffmpeg. Đừng dựng hết từ đầu — mỗi cái chỉ kéo vào đúng lúc chức năng cần, để thấy nó giải quyết vấn đề gì:

| Khi nào | Thêm gì | Vì chức năng |
|---|---|---|
| Từ F1 | Postgres + Flyway | mọi thứ |
| Từ F2 | SMTP (Mailhog) gửi thẳng | F2 gửi OTP |
| Từ F4 | Redis | F4 đếm lần đăng nhập sai, F5 lưu refresh token |
| Từ F13 | MinIO (S3 API) | F13 presigned URL |
| Từ F19 | ffmpeg | F19 ghép voice tag |
| Cuối cùng | Kafka + outbox | thay SMTP đồng bộ ở F2 |
