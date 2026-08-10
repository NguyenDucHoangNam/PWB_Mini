# Hạ tầng — Lỗi & đa ngôn ngữ

> `shared-kernel`: `ErrorCode`, `ErrorCategory`, `BusinessException` · `shared-web`: `GlobalExceptionHandler`, `WebErrorMapper`, `MessageSourceConfig`, `LocaleFilter`
> Bối cảnh: [01 §2.1](01-architecture-overview.md)

---

## 1. Bài toán

Một lỗi nghiệp vụ phải đi từ tầng domain — nơi **không được biết HTTP là gì** — tới một response JSON có mã trạng thái đúng và câu chữ đúng ngôn ngữ người dùng.

Ba thứ phải tách rời:

| Thứ | Ai quyết | Ở đâu |
|---|---|---|
| **Mã lỗi** `IAM_004` | Domain | `shared-kernel` |
| **Loại lỗi** `UNAUTHORIZED` → HTTP 401 | `shared-web` | `WebErrorMapper` |
| **Câu chữ** "Email hoặc mật khẩu không hợp lệ" | Bundle message | `MessageSource` |

Tách ba thứ này là điều cho phép domain layer thuần Java ném lỗi mà vẫn ra được response HTTP tử tế.

---

## 2. Đường đi của một lỗi

```mermaid
flowchart LR
    A["use case ném<br/>BusinessException(IamErrorCode.LOGIN_BAD_CREDENTIALS)"] --> B["GlobalExceptionHandler"]
    B --> C1["code() → \"IAM_004\""]
    B --> C2["category() → UNAUTHORIZED<br/>→ WebErrorMapper → HTTP 401"]
    B --> C3["MessageSource.getMessage(\"IAM_004\", locale)<br/>→ \"Email hoặc mật khẩu không hợp lệ\""]
    C1 & C2 & C3 --> D["ApiResponse.error"]
```

Bảng `ErrorCategory → HttpStatus`:

| Category | HTTP | | Category | HTTP |
|---|---|---|---|---|
| `VALIDATION` | 400 | | `CONFLICT` | 409 |
| `UNAUTHORIZED` | 401 | | `TOO_MANY_REQUESTS` | 429 |
| `FORBIDDEN` | 403 | | `BUSINESS` | 422 |
| `NOT_FOUND` | 404 | | `INTERNAL` | 500 |
| `METHOD_NOT_ALLOWED` | 405 | | | |

Nhờ bảng này, domain chỉ khai báo *"đây là loại lỗi không có quyền"*; dịch sang 401 là việc của `shared-web`.

---

## 3. Ba dải mã lỗi, một quy ước

| Dải | Module | Ví dụ |
|---|---|---|
| `IAM_xxx` | IAM | `IAM_004` sai thông tin đăng nhập |
| `AUDIO_xxx` | Audio | `AUDIO_022` khoảng chèn ngắn hơn tag |
| `LR_xxx` | Live Room | `LR_075` xung đột điều khiển nhạc |
| `VALIDATION_FAILED`, `ACCESS_DENIED`, `INTERNAL_SERVER_ERROR` | `SysErrorCode` dùng chung | |

Mã là **số**, không phải tên gợi nghĩa. Với Live Room, tài liệu yêu cầu dùng tên kiểu `LIVEROOM_ROOM_FULL`; cài đặt giữ tên đó làm **tên hằng số enum** nhưng mã trả về là `LR_xxx`, cho khớp quy ước của hai module có trước.

Mỗi mã mang bốn thứ:

```java
LOGIN_BAD_CREDENTIALS (ErrorCategory.UNAUTHORIZED, "IAM_004", "Invalid email or password."),
```

Chuỗi cuối là **bản dự phòng tiếng Anh**, dùng khi bundle không có key. Nó là thứ khiến một bundle chưa đăng ký hỏng **lặng lẽ** — xem mục 5.

---

## 4. Hình dạng response

Cùng một lớp `ApiResponse`, hai nửa:

```json
{"success":true,  "data":{…},          "traceId":"…", "timestamp":1786333492431}
{"success":false, "message":"…", "code":"IAM_004", "traceId":"…", "timestamp":…}
```

Response lỗi **không có** `data`; response thành công **không có** `code`. Nửa không dùng biến mất nhờ `default-property-inclusion: non_null`.

Lỗi có chi tiết thì thêm `error`:

```json
{"success":false,"code":"IAM_026","error":{"retryAfterSeconds":59}, …}
{"success":false,"code":"VALIDATION_FAILED","error":{"password":["size must be between 12 and 128"]}, …}
```

Hai hình dạng khác nhau trong cùng một trường: một map tuỳ ý từ `BusinessException.details`, hoặc map `field → danh sách lỗi` từ Bean Validation.

### 4.1. Hai id truy vết, không trùng nhau

`X-Correlation-Id` trên header (do `CorrelationIdFilter` sinh, đẩy vào MDC) và `traceId` trong body (do lớp dựng `ApiResponse` sinh) là **hai giá trị khác nhau** — kiểm chứng được ở mọi response.

Cái đầu ghép được với dòng log; cái sau chỉ có trong body. Khi người dùng gửi ảnh chụp lỗi, họ đưa `traceId` — mà tra log lại phải dùng `X-Correlation-Id`. Đây là một chỗ gây nhầm lẫn thật.

---

## 5. Bẫy lớn nhất: bundle không đăng ký

`MessageSourceConfig` liệt kê basename của **năm** bundle:

```java
source.setBasenames(SHARED_WEB_BUNDLE, IAM_BUNDLE, AUDIO_BUNDLE, LIVEROOM_BUNDLE, SHARED_INFRA_BUNDLE);
source.setDefaultEncoding(StandardCharsets.UTF_8.name());
source.setFallbackToSystemLocale(false);
source.setUseCodeAsDefaultMessage(false);
source.setDefaultLocale(DEFAULT_LOCALE);
```

Bundle của Live Room **từng bị quên** trong danh sách này. Hậu quả:

- Mọi lỗi `LR_xxx` rơi về chuỗi tiếng Anh hard-code trong enum
- Mọi message thành công trả về **nguyên key thô**: `"LIVEROOM_ROOM_CREATED"`
- HTTP vẫn **200**, log vẫn sạch, không exception nào

Không có gì báo. Đây là mẫu mực của một lỗi hỏng-lặng-lẽ, và là lý do bước "đăng ký basename" nằm trong [checklist thêm module mới](01-architecture-overview.md).

Ba dòng cấu hình còn lại cũng đáng đọc:

- `setFallbackToSystemLocale(false)` — không rơi về locale của **máy chủ**. Không có nó, một server đặt locale Nhật sẽ trả tiếng Nhật cho người không gửi `Accept-Language`.
- `setUseCodeAsDefaultMessage(false)` — thiếu key thì ném thay vì trả về chính key đó. Kết hợp với `getMessage(code, args, defaultMessage, locale)` ở nơi gọi, kết quả là rơi về chuỗi tiếng Anh của enum — hành vi có kiểm soát.
- `setDefaultLocale(...)` — locale mặc định rõ ràng thay vì tuỳ môi trường.

---

## 6. Locale: đặt ở filter đầu tiên, dùng ở tận cuối

`LocaleFilter` mang `@Order(Ordered.HIGHEST_PRECEDENCE)` — **filter chạy sớm nhất trong cả chain**. Nó đọc `Accept-Language` và đặt vào `LocaleContextHolder`.

Vì sao phải sớm nhất: việc dịch xảy ra ở **cuối** vòng đời request, trong `GlobalExceptionHandler`. Giữa hai đầu là mọi filter khác, và bất kỳ cái nào cũng có thể ném lỗi cần dịch. Filter rate limit chẳng hạn — nó tự dựng response 429 với message đã dịch, và nó chỉ làm được vì `LocaleFilter` đã chạy trước.

Một số endpoint còn nhận `Accept-Language` **lần thứ hai** dưới dạng tham số phương thức:

```java
@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
…
RequestLocale.from(acceptLanguage)
```

Lý do: locale đó phải **đi theo vào payload email** ([iam-01 §7](iam-01-dang-ky-va-otp.md)). Nội dung mail được kết xuất lúc xếp hàng, nhưng consumer chạy trên luồng khác — nơi `LocaleContextHolder` không còn giá trị nào. Nên locale phải được truyền tay như dữ liệu.

---

## 7. STOMP có đường xử lý lỗi riêng

`GlobalExceptionHandler` gắn với servlet dispatch và **không bao giờ nhìn thấy một STOMP frame**. Live Room phải có `LiveroomStompExceptionHandler` riêng — chi tiết ở [13 §7](13-realtime-stomp.md).

Hai đường, cùng một bảng mã lỗi và cùng một `MessageSource`, khác nhau ở chỗ trả về:

| | REST | STOMP |
|---|---|---|
| Bắt bởi | `GlobalExceptionHandler` | `LiveroomStompExceptionHandler` |
| Trả về | Response HTTP | `/user/queue/liveroom/errors` |
| Mã trạng thái | Có | Không có khái niệm |
| Lỗi khoá lạc quan | Bắt được trong use case | **Chỉ bắt được ở đây** (nổi lên lúc commit) |

---

## 8. Phía client: dịch theo mã, không dùng câu từ server

`Frontend/src/lib/error-code-to-i18n.ts` có bảng `EXACT_ERROR_CODE_TO_I18N_KEY` ánh xạ mã lỗi sang key i18n của frontend, và **chỉ dùng `message` của server làm dự phòng**.

Vì sao dịch hai lần: frontend cần chèn biến, đổi câu chữ theo ngữ cảnh màn hình, và đôi khi hiển thị hoàn toàn khác với câu server gửi.

Cái bẫy đi kèm: **map này phải được cập nhật mỗi khi thêm mã lỗi mới.** Nó từng chỉ liệt kê **9 trong khoảng 40** mã `LR_xxx`, dù `liveroom.errors.*` đã có bản dịch cho gần hết — kết quả là phần lớn lỗi âm thầm hiển thị tiếng Anh trong một giao diện tiếng Việt.

Quy tắc rút ra: thêm một mã lỗi mới cần **ba** chỗ, không phải một — enum, bundle của module, và map phía client.

---

## 9. Quyết định & đánh đổi

| Quyết định | Thay vì | Vì sao | Cái giá |
|---|---|---|---|
| Tách mã / loại / câu chữ | Ném exception mang sẵn HTTP status | Domain không biết HTTP | Ba chỗ phải giữ khớp |
| Mã dạng số theo module | Tên gợi nghĩa | Ngắn, ổn định, dễ tra | Đọc `LR_075` không biết là gì nếu không tra |
| Chuỗi tiếng Anh dự phòng trong enum | Không có dự phòng | Thiếu bản dịch vẫn ra câu đọc được | **Bundle chưa đăng ký hỏng lặng lẽ** |
| `useCodeAsDefaultMessage(false)` | `true` | Không trả về key thô cho người dùng | Phải luôn truyền `defaultMessage` khi gọi |
| `fallbackToSystemLocale(false)` | Mặc định của Spring | Không phụ thuộc locale máy chủ | — |
| Locale đặt ở filter sớm nhất | Đọc trong controller | Filter ném lỗi cũng dịch được | Thêm một filter |
| Locale truyền tay vào payload email | Đọc lại trong consumer | `LocaleContextHolder` không tồn tại ở luồng khác | Tham số lặp ở nhiều endpoint |
| Client dịch theo mã | Hiển thị `message` của server | Chèn biến được, đổi câu theo màn hình | Map phải cập nhật, quên là ra tiếng Anh |
| `non_null` loại trường rỗng | Trả về `null` tường minh | Payload gọn | Trường vắng mặt gây nhầm với "không có giá trị" |
| Hai id truy vết | Một | — | Tra log dễ nhầm — mục 4.1 |

---

## 10. Tự kiểm chứng

**Thấy cùng một mã lỗi, hai ngôn ngữ:**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -H "Accept-Language: vi" -d '{"email":"user1@gmail.com","password":"sai"}'
```

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -H "Accept-Language: en" -d '{"email":"user1@gmail.com","password":"sai"}'
```

`code` giữ nguyên `IAM_004`, `message` đổi. Bỏ hẳn header thì nhận locale mặc định.

**Thấy `ErrorCategory` quyết định mã HTTP:**

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"user1@gmail.com","password":"sai"}'
```

`401` — vì `LOGIN_BAD_CREDENTIALS` là `UNAUTHORIZED`. So với `IAM_026` (cooldown, `TOO_MANY_REQUESTS`) trả `429`.

**Thấy hai hình dạng `error`:**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register -H "Content-Type: application/json" -d '{"email":"x@example.com","password":"abc","fullName":"X"}'
```

Cho ra `VALIDATION_FAILED` với `error` dạng `field → danh sách`. Còn đăng ký hai lần liên tiếp cho ra `IAM_026` với `error: {"retryAfterSeconds": …}`.

**Thấy hai id truy vết khác nhau:**

```bash
curl -s -D - -o /tmp/body.json -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"user1@gmail.com","password":"sai"}' | grep -i correlation; cat /tmp/body.json
```

`X-Correlation-Id` trên header và `traceId` trong body không trùng.

**Thấy bundle đã đăng ký đủ** — gọi một endpoint Live Room bằng `Accept-Language: vi` và kiểm `message` là tiếng Việt, **không phải** một key thô kiểu `LIVEROOM_...`. Nếu thấy key thô, basename đã bị gỡ khỏi `MessageSourceConfig`.

**Xem các bundle:**

```bash
ls Backend/modules/*/src/main/resources/*/messages*.properties Backend/shared/*/src/main/resources/*/messages*.properties 2>/dev/null
```

---

## 11. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| **Bundle chưa đăng ký hỏng lặng lẽ** | Không exception, không log, HTTP vẫn 200 — mục 5 |
| Thêm mã lỗi cần sửa ba chỗ | Enum, bundle, map phía client — mục 8 |
| Không có kiểm tra bản dịch thiếu | Không test nào đối chiếu enum với bundle |
| Hai id truy vết | Mục 4.1 |
| STOMP có đường xử lý lỗi song song | Thêm mã lỗi phải nhớ cả hai đường |
| `WEAK_PASSWORD` không nói thiếu gì | Danh sách vi phạm được tính rồi bỏ đi ([iam-01 §4](iam-01-dang-ky-va-otp.md)) |
| Chỉ hai ngôn ngữ | Tiếng Việt và tiếng Anh; thêm ngôn ngữ là thêm một bộ bundle cho mỗi module |
| Không có mã lỗi cho tầng hạ tầng | Outbox, Kafka, ES hỏng thì rơi vào `INTERNAL_SERVER_ERROR` chung |
