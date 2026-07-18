# Auth Endpoint Matrix — IAM Module

> Cập nhật ngày 2026-07-18 theo Sprint I0 (IAM HARDEN).
> Người sở hữu: IAM track. Verify mỗi khi thêm/sửa endpoint.

## Quy ước

| Cột | Ý nghĩa |
|---|---|
| Method | HTTP method |
| Path | Đường dẫn (relative với base `/api/v1/auth`) |
| Auth required | `public` (không cần token) / `authenticated` (bắt buộc Bearer access token) |
| Authz rule | `@PreAuthorize` ở method-level (nếu có) |
| Rate limit | Có áp dụng rate-limit không (số req/IP/giờ) |
| Lockout | Có chịu brute-force lockout không (theo email + IP) |
| i18n success key | Message key trả về khi thành công |
| Error codes | Các `ErrorCode` có thể trả về |

## Matrix — 11 endpoint

| # | Method | Path | Auth required | Authz rule | Rate limit | Lockout | i18n success key | Error codes |
|---|---|---|---|---|---|---|---|---|
| 1 | POST | `/register` | public | — | 3/IP/h | không | `AUTH_REGISTER_SUCCESSFUL` | `EMAIL_ALREADY_REGISTERED_AUTH`, `INVALID_INPUT`, `AUTH_RATE_LIMIT_EXCEEDED` |
| 2 | POST | `/login` | public | — | không | **CÓ** (5 sai → 15 phút) | `AUTH_LOGIN_SUCCESSFUL` | `AUTH_LOGIN_FAILED`, `AUTH_ACCOUNT_LOCKED`, `AUTH_IP_LOCKED`, `AUTH_ACCOUNT_NOT_VERIFIED` |
| 3 | POST | `/google` | public | — | không | không | `AUTH_GOOGLE_LOGIN_SUCCESSFUL` | `AUTH_GOOGLE_TOKEN_INVALID`, `AUTH_GOOGLE_EMAIL_NOT_VERIFIED`, `AUTH_OAUTH_USER_NO_PASSWORD`, `FORBIDDEN` |
| 4 | POST | `/refresh` | public (cần refresh token) | — | không | không | `AUTH_REFRESH_TOKEN_SUCCESSFUL` | `AUTH_TOKEN_INVALID` |
| 5 | POST | `/logout` | **authenticated** | `@PreAuthorize("isAuthenticated()")` | không | không | `AUTH_LOGOUT_SUCCESSFUL` | `USER_NOT_FOUND`, `UNAUTHORIZED` |
| 6 | POST | `/verify-otp` | public | — | không | OTP lock riêng (5 sai) | `AUTH_VERIFY_OTP_SUCCESSFUL` | `AUTH_OTP_INVALID`, `AUTH_OTP_EXPIRED`, `AUTH_OTP_LOCKED`, `USER_NOT_FOUND` |
| 7 | POST | `/resend-otp` | public | — | 3/IP/h | không | `AUTH_OTP_RESENT` | `USER_NOT_FOUND`, `AUTH_OTP_COOLDOWN`, `AUTH_RATE_LIMIT_EXCEEDED` |
| 8 | POST | `/forgot-password` | public | — | 3/IP/h + Redis cooldown 60s/email | không | `AUTH_FORGOT_PASSWORD_SENT` | `PASSWORD_RESET_COOLDOWN`, `AUTH_OAUTH_USER_NO_PASSWORD`, `AUTH_RATE_LIMIT_EXCEEDED` |
| 9 | POST | `/reset-password` | public (cần reset token) | — | 3/IP/h | không | `AUTH_PASSWORD_RESET_SUCCESSFUL` | `AUTH_RESET_TOKEN_INVALID`, `USER_NOT_FOUND`, `AUTH_OAUTH_USER_NO_PASSWORD`, `AUTH_RATE_LIMIT_EXCEEDED` |
| 10 | POST | `/complete-profile` | **authenticated** | `@PreAuthorize("isAuthenticated()")` | không | không | `AUTH_COMPLETE_PROFILE_SUCCESSFUL` | `USER_NOT_FOUND`, `USER_NAME_EXISTS`, `UNAUTHORIZED` |
| 11 | POST | `/change-password` | **authenticated** | `@PreAuthorize("isAuthenticated()")` | không | không | `AUTH_PASSWORD_CHANGED_SUCCESSFUL` | `USER_NOT_FOUND`, `AUTH_OAUTH_USER_NO_PASSWORD`, `AUTH_INVALID_CURRENT_PASSWORD`, `AUTH_PASSWORD_REUSED`, `UNAUTHORIZED` |

## Public paths (filter-chain level)

Định nghĩa trong `bootstrap/src/main/resources/application.yml` → `app.security.public-paths`:

```yaml
public-paths:
  - /api/v1/auth/register
  - /api/v1/auth/login
  - /api/v1/auth/google
  - /api/v1/auth/refresh
  - /api/v1/auth/verify-otp
  - /api/v1/auth/resend-otp
  - /api/v1/auth/forgot-password
  - /api/v1/auth/reset-password
  - /actuator/health
  - /v3/api-docs/**
  - /swagger-ui/**
```

Mọi path không thuộc danh sách này sẽ rơi vào `anyRequest().authenticated()` → filter `JwtAuthenticationFilter` validate Bearer token → nếu thiếu/hết hạn → `AuthEntryPoint` trả 401; nếu không đủ quyền → `AccessDeniedHandlerImpl` trả 403.

## Endpoint ĐẶC BIỆT — yêu cầu authenticated

3 endpoint dưới đây **KHÔNG** nằm trong `public-paths` (sau I0.4):

- `/api/v1/auth/logout` — yêu cầu đăng nhập.
- `/api/v1/auth/complete-profile` — yêu cầu đăng nhập.
- `/api/v1/auth/change-password` — yêu cầu đăng nhập.

Trước I0.4, 3 endpoint này thuộc `permitAll` (do wildcard `/api/v1/auth/**`) — chỉ enforce ở `@PreAuthorize` method-level. Sau I0.4, filter-chain chặn thiếu token ngay từ đầu → 401 nhất quán với các flow auth khác.

## Quy tắc thiết kế

1. **Không thêm endpoint mới vào `public-paths` trừ khi thực sự public.**
2. **Mọi endpoint mới có side-effect** (đổi state) phải có `@PreAuthorize` ở method-level.
3. **Mọi endpoint trả token** phải set refresh cookie qua `RefreshTokenCookieService`.
4. **Mọi ErrorCode mới** phải được thêm vào cả 3 file `messages*.properties` (en/vi/fallback).
5. **Mọi message trả về user** phải qua `MessageSource` (không hardcode).

## Lịch sử thay đổi

| Ngày | Thay đổi |
|---|---|
| 2026-07-18 | Khởi tạo từ Sprint I0.4. Tách `permitAll` thành 11 path riêng biệt. Thêm rate-limit cho 4 endpoint, lockout cho `/login`. |
