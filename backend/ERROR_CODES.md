# Error Codes — Single Source of Truth

> **Mục đích:** tài liệu này là nguồn tham chiếu duy nhất cho **frontend, mobile, QA, BA, SRE** khi cần biết:
> - Mã lỗi có nghĩa gì
> - HTTP status trả về
> - Có nên retry không
> - Thông báo i18n đã có sẵn chưa

> **Cập nhật:** mỗi khi thêm ErrorCode mới, **bắt buộc** cập nhật file này + thêm key vào `messages_vi.properties`, `messages_en.properties`, `messages.properties`.

---

## 1. Quy ước

| Mục | Quy ước |
|---|---|
| Format code | `SCREAMING_SNAKE_CASE`, unique toàn hệ thống |
| Vị trí định nghĩa | `com.pwb.backend.<module>.internal.domain.exception.<Module>ErrorCode` (mỗi module tự sở hữu) |
| Code cross-cutting | `com.pwb.backend.shared.exception.ErrorCode` (chỉ dùng cho cross-module) |
| Response format | `{ success: false, message, errors: [{ code, field, message }], traceId, timestamp }` |
| HTTP status | Quy định theo category — xem bảng dưới |
| i18n | Tra từ `code` trong `MessageSource`, fallback `defaultMessage` |

## 2. HTTP Status Map

| Category | HTTP Status | Ý nghĩa |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Bean Validation thất bại |
| `UPLOAD_TOO_LARGE` | 413 | Upload file quá lớn |
| `DATA_CONFLICT` | 409 | DB constraint (unique, FK) |
| `RESOURCE_NOT_FOUND` | 404 | Endpoint không tồn tại |
| `NOT_FOUND` | 404 | Resource không tồn tại |
| `BAD_REQUEST` | 400 | Input sai format / logic |
| `LOCKED` | 423 | Tài khoản bị khóa tạm |
| `UNAUTHORIZED` | 401 | Chưa xác thực |
| `JWT_EXPIRED` | 401 | Token hết hạn |
| `FORBIDDEN` | 403 | Không có quyền |
| `CONFLICT` | 409 | Trạng thái không hợp lệ |
| `TOO_MANY_REQUESTS` | 429 | Rate limit / quota |
| `SERVICE_UNAVAILABLE` | 503 | Hạ tầng ngoài (TTS, S3) |
| `BAD_GATEWAY` | 502 | Upstream trả về lỗi |
| `GONE` | 410 | Link đã hết hạn vĩnh viễn |
| `INTERNAL_SERVER_ERROR` | 500 | Bug — không lộ chi tiết |

---

## 3. Shared / Cross-cutting Codes

Định nghĩa: `com.pwb.backend.shared.exception.ErrorCode`

| Code | HTTP | i18n key | Ý nghĩa |
|---|---|---|---|
| `INTERNAL_SERVER_ERROR` | 500 | `INTERNAL_SERVER_ERROR` | Catch-all 500 — log full stack, không lộ chi tiết |
| `VALIDATION_FAILED` | 400 | `VALIDATION_FAILED` | `@Valid` thất bại |
| `UNAUTHORIZED` | 401 | `UNAUTHORIZED` | Chưa xác thực |
| `FORBIDDEN` | 403 | `FORBIDDEN` | Không có quyền |
| `RESOURCE_NOT_FOUND` | 404 | `RESOURCE_NOT_FOUND` | Không tìm thấy endpoint |
| `RATE_LIMIT_EXCEEDED` | 429 | `RATE_LIMIT_EXCEEDED` | Bị rate-limit |

---

## 4. IAM Module Codes

Định nghĩa: `com.pwb.backend.iam.internal.domain.exception.IamErrorCode`

| Code | HTTP | i18n key | Ý nghĩa | Retry? |
|---|---|---|---|---|
| `USER_NOT_EXISTED` | 404 | `USER_NOT_EXISTED` | Người dùng không tồn tại | No |
| `USERNAME_EXISTED` | 400 | `USERNAME_EXISTED` | Username đã được dùng | No |
| `EMAIL_EXISTED` | 400 | `EMAIL_EXISTED` | Email đã được dùng | No |
| `OTP_EXPIRED` | 400 | `OTP_EXPIRED` | OTP hết hạn | Request new OTP |
| `INVALID_OTP` | 400 | `INVALID_OTP` | OTP sai | No (max attempts) |
| `OTP_COOLDOWN` | 429 | `OTP_COOLDOWN` | Đang trong cooldown | Wait then retry |
| `OTP_ATTEMPTS_EXCEEDED` | 423 | `OTP_ATTEMPTS_EXCEEDED` | Vượt số lần thử OTP | Wait lockout |
| `DISPOSABLE_EMAIL_NOT_ALLOWED` | 400 | `DISPOSABLE_EMAIL_NOT_ALLOWED` | Email rác bị chặn | No |
| `REGISTRATION_IN_PROGRESS` | 400 | `REGISTRATION_IN_PROGRESS` | Đã có yêu cầu đăng ký | No |
| `ACCOUNT_ALREADY_ACTIVE` | 400 | `ACCOUNT_ALREADY_ACTIVE` | Tài khoản đã kích hoạt | No |
| `JWT_EXPIRED` | 401 | `JWT_EXPIRED` | JWT hết hạn | Refresh token |
| `INVALID_REFRESH_TOKEN` | 401 | `INVALID_REFRESH_TOKEN` | Refresh token sai/hết hạn | Re-login |
| `TOKEN_THEFT_DETECTED` | 401 | `TOKEN_THEFT_DETECTED` | Phát hiện tái sử dụng token | Re-login |
| `BAD_CREDENTIALS` | 400 | `BAD_CREDENTIALS` | Sai username/password | No |
| `ACCOUNT_BANNED` | 400 | `ACCOUNT_BANNED` | Tài khoản bị cấm | No |
| `ACCOUNT_TEMPORARILY_LOCKED` | 423 | `ACCOUNT_TEMPORARILY_LOCKED` | Tạm khóa | Wait |
| `INVALID_OAUTH_TOKEN` | 400 | `INVALID_OAUTH_TOKEN` | OAuth token sai | No |
| `INVALID_RESET_TOKEN` | 400 | `INVALID_RESET_TOKEN` | Reset token sai/hết hạn | Request new |
| `OAUTH_ONLY_ACCOUNT` | 400 | `OAUTH_ONLY_ACCOUNT` | Không đổi được mk cho OAuth-only | No |
| `INVALID_OLD_PASSWORD` | 400 | `INVALID_OLD_PASSWORD` | Sai mật khẩu cũ | No |
| `PASSWORD_REUSE_BLOCKED` | 400 | `PASSWORD_REUSE_BLOCKED` | Mật khẩu mới trùng cũ | No |
| `INVALID_PASSWORD` | 400 | `INVALID_PASSWORD` | Xác nhận mk không khớp | No |
| `DELETION_ALREADY_REQUESTED` | 400 | `DELETION_ALREADY_REQUESTED` | Đã yêu cầu xóa rồi | No |
| `SESSION_NOT_FOUND` | 404 | `SESSION_NOT_FOUND` | Phiên không tồn tại | No |
| `CANNOT_REVOKE_CURRENT_SESSION` | 400 | `CANNOT_REVOKE_CURRENT_SESSION` | Không hủy phiên hiện tại qua API này | Use logout |
| `OAUTH_LINK_PASSWORD_REQUIRED` | 409 | `OAUTH_LINK_PASSWORD_REQUIRED` | Cần mk để liên kết Google | Provide password |

---

## 5. Audio Module Codes

Định nghĩa: `com.pwb.backend.audio.internal.domain.exception.AudioErrorCode`

### 5.1 Upload & Processing

| Code | HTTP | i18n key | Ý nghĩa | Retry? |
|---|---|---|---|---|
| `UNSUPPORTED_AUDIO_FORMAT` | 400 | `UNSUPPORTED_AUDIO_FORMAT` | Format không hỗ trợ (wav/flac/mp3) | No |
| `FILE_NOT_FOUND_ON_S3` | 400 | `FILE_NOT_FOUND_ON_S3` | File không có trên S3 | Re-upload |
| `FILE_SIZE_MISMATCH` | 400 | `FILE_SIZE_MISMATCH` | Size không khớp claim | Re-upload |
| `INVALID_S3_KEY_OWNER` | 403 | `INVALID_S3_KEY_OWNER` | Không sở hữu S3 key | No |
| `DEMO_QUOTA_EXCEEDED` | 403 | `DEMO_QUOTA_EXCEEDED` | Vượt quota demo (20) | Delete old demo |
| `AUDIO_QUOTA_EXCEEDED` | 403 | `AUDIO_QUOTA_EXCEEDED` | Vượt quota audio (5 GB) | Delete |
| `INVALID_AUDIO_CONTENT` | 500 | `INVALID_AUDIO_CONTENT` | Audio content sai | Re-upload |
| `UPLOAD_CLAIM_EXPIRED` | 400 | `UPLOAD_CLAIM_EXPIRED` | Upload claim hết hạn | Re-request |
| `FFMPEG_PROCESS_FAILED` | 500 | `FFMPEG_PROCESS_FAILED` | FFmpeg xử lý lỗi | Contact support |
| `FFPROBE_VALIDATION_FAILED` | 400 | `FFPROBE_VALIDATION_FAILED` | ffprobe check fail | Re-upload valid file |
| `HLS_SEGMENTATION_FAILED` | 500 | `HLS_SEGMENTATION_FAILED` | HLS segmentation fail | Contact support |
| `AES_KEY_GENERATION_FAILED` | 500 | `AES_KEY_GENERATION_FAILED` | AES key gen fail | Contact support |

### 5.2 Voice Tag (TTS)

| Code | HTTP | i18n key | Ý nghĩa | Retry? |
|---|---|---|---|---|
| `TTS_TEXT_TOO_LONG` | 400 | `TTS_TEXT_TOO_LONG` | Text > 100 chars | Shorten |
| `INVALID_LANGUAGE_CODE` | 400 | `INVALID_LANGUAGE_CODE` | BCP-47 sai | Fix format |
| `INVALID_VOICE_NAME` | 400 | `INVALID_VOICE_NAME` | Voice không trong allowlist | Pick from list |
| `INVALID_SSML_TAG` | 400 | `INVALID_SSML_TAG` | SSML tag ngoài whitelist | Remove |
| `VOICE_TAG_LIMIT_EXCEEDED` | 400 | `VOICE_TAG_LIMIT_EXCEEDED` | Vượt 50 tags | Delete old |
| `VOICE_TAG_STORAGE_EXCEEDED` | 400 | `VOICE_TAG_STORAGE_EXCEEDED` | Vượt 200 MB | Delete old |
| `TTS_SERVICE_FAILED` | 502 | `TTS_SERVICE_FAILED` | GCP TTS lỗi | Yes (transient) |
| `TTS_SERVICE_FAILED_INVALID` | 502 | `TTS_SERVICE_FAILED_INVALID` | GCP TTS integrity fail | Yes |
| `VOICE_TAG_NOT_FOUND` | 404 | `VOICE_TAG_NOT_FOUND` | Voice tag không tồn tại | No |
| `VOICE_TAG_IN_USE` | 409 | `VOICE_TAG_IN_USE` | Đang được demo ACTIVE dùng | Remove from demo |
| `VOICE_TAG_ALREADY_DEFAULT` | 409 | `VOICE_TAG_ALREADY_DEFAULT` | Đã có default khác | Unset first |

### 5.3 Demo & Distribution

| Code | HTTP | i18n key | Ý nghĩa | Retry? |
|---|---|---|---|---|
| `DEMO_NOT_FOUND` | 404 | `DEMO_NOT_FOUND` | Demo không tồn tại / đã xóa | No |
| `DEMO_NOT_ACTIVE` | 409 | `DEMO_NOT_ACTIVE` | Demo không ACTIVE | No |
| `INVALID_RECIPIENT_EMAIL` | 400 | `INVALID_RECIPIENT_EMAIL` | Email rác / sai format | Fix |
| `SHARE_QUOTA_EXCEEDED` | 429 | `SHARE_QUOTA_EXCEEDED` | Vượt quota share | Wait |
| `DISTRIBUTION_NOT_FOUND` | 404 | `DISTRIBUTION_NOT_FOUND` | Distribution không tồn tại | No |

### 5.4 Shared Link / Streaming / Download

| Code | HTTP | i18n key | Ý nghĩa | Retry? |
|---|---|---|---|---|
| `LINK_REVOKED` | 403 | `LINK_REVOKED` | Producer thu hồi link | No |
| `IP_MISMATCH` | 403 | `IP_MISMATCH` | IP không khớp subnet / jti revoked | No |
| `LINK_NOT_FOUND` | 404 | `LINK_NOT_FOUND` | Link không tồn tại | No |
| `LINK_EXPIRED` | 410 | `LINK_EXPIRED` | Link hết hạn vĩnh viễn | No |
| `STREAM_SESSION_INVALID` | 403 | `STREAM_SESSION_INVALID` | Session cookie sai/hết hạn | Re-auth |
| `DOWNLOAD_PROHIBITED` | 403 | `DOWNLOAD_PROHIBITED` | Producer tắt download | No |
| `ORIGINAL_FILE_MISSING` | 404 | `ORIGINAL_FILE_MISSING` | File gốc mất trên S3 | No |
| `DOWNLOAD_QUOTA_EXCEEDED` | 429 | `DOWNLOAD_QUOTA_EXCEEDED` | Vượt quota download | Wait |
| `S3_PRESIGN_FAILED` | 503 | `S3_PRESIGN_FAILED` | Không tạo được presigned URL | Yes |
| `FORBIDDEN_ACCESS` | 403 | `FORBIDDEN_ACCESS` | Không sở hữu resource | No |
| `WS_TOKEN_INVALID` | 403 | `WS_TOKEN_INVALID` | WS token sai/hết hạn | Re-auth |
| `WS_SUBSCRIBE_DENIED` | 403 | `WS_SUBSCRIBE_DENIED` | Không là participant | No |

### 5.5 Audio-module OTP (recipient / share-link flow)

| Code | HTTP | i18n key | Ý nghĩa | Retry? |
|---|---|---|---|---|
| `OTP_COOLDOWN` | 429 | `OTP_COOLDOWN` | Đang cooldown | Wait |
| `OTP_ATTEMPTS_EXCEEDED` | 423 | `OTP_ATTEMPTS_EXCEEDED` | Vượt attempts | Wait lockout |
| `OTP_EXPIRED` | 400 | `OTP_EXPIRED` | OTP hết hạn | Request new |
| `INVALID_OTP` | 400 | `INVALID_OTP` | OTP sai | No |

---

## 6. Thêm ErrorCode mới — Checklist

Khi thêm code mới, làm theo đúng 5 bước:

- [ ] **1. Thêm enum entry** trong `IamErrorCode` / `AudioErrorCode` / `ErrorCode`
- [ ] **2. Thêm translation key** trong cả 3 file `messages*.properties`
- [ ] **3. Throw `BusinessException`** ở service layer với code tương ứng
- [ ] **4. Cập nhật file này** (thêm row vào bảng của module tương ứng)
- [ ] **5. Cập nhật FE** — nếu cần thông báo riêng (fallback là dùng `message` từ response)

## 7. Response mẫu

### 7.1 Success
```json
{
  "success": true,
  "message": "auth.login.success",
  "data": { "accessToken": "...", "refreshToken": "..." },
  "errors": null,
  "timestamp": "2026-07-11T15:30:00Z",
  "traceId": "0HMQ8V9F5P3N5:00000003"
}
```

### 7.2 Validation (multi-field)
```json
{
  "success": false,
  "message": "Validation failed",
  "data": null,
  "errors": [
    { "code": "VALIDATION_FAILED", "field": "email", "message": "must be a well-formed email" },
    { "code": "VALIDATION_FAILED", "field": "password", "message": "must be at least 8 chars" }
  ],
  "timestamp": "2026-07-11T15:30:00Z",
  "traceId": "0HMQ8V9F5P3N5:00000003"
}
```

### 7.3 Business error
```json
{
  "success": false,
  "message": "Tên đăng nhập đã được sử dụng",
  "data": null,
  "errors": [
    { "code": "USERNAME_EXISTED", "field": null, "message": "Tên đăng nhập đã được sử dụng" }
  ],
  "timestamp": "2026-07-11T15:30:00Z",
  "traceId": "0HMQ8V9F5P3N5:00000003"
}
```

### 7.4 Internal Server Error
```json
{
  "success": false,
  "message": "Đã xảy ra lỗi không mong đợi",
  "data": null,
  "errors": [
    { "code": "INTERNAL_SERVER_ERROR", "field": null, "message": "Đã xảy ra lỗi không mong đợi" }
  ],
  "timestamp": "2026-07-11T15:30:00Z",
  "traceId": "0HMQ8V9F5P3N5:00000003"
}
```
> Frontend: **luôn truyền `traceId` khi escalate** cho support. Log phía server có full stack trace tương ứng với `traceId` này.

---

## 8. Frontend / Mobile checklist

- [ ] Đọc `errors[0].code` để xử lý logic (VD: `JWT_EXPIRED` → redirect refresh)
- [ ] Hiển thị `errors[0].message` cho user (đã là localized)
- [ ] Nếu là `VALIDATION_FAILED`, có thể có nhiều error → map `field` vào form
- [ ] Nếu là `INTERNAL_SERVER_ERROR`, hiển thị generic + log `traceId` để support tra
- [ ] Caching layer: cache toàn bộ bảng này để map `code → i18n message` cho logging/analytics