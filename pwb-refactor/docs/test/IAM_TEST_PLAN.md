# IAM Module — Test Plan

> **Mục đích**: File này là "đề bài" để AI khác generate code test cho module IAM trong `pwb-refactor/Backend/modules/iam/`.
>
> **Chuẩn áp dụng**: Test Pyramid (Martin Fowler) + Right-BICEP (Brian Marick) + CORRECT (IEEE 829) + FIRST (Tim Ottinger).
>
> **Nguyên tắc**: Mỗi test phải cover **một business behavior**, không test implementation. Đọc code IAM trước khi viết test để hiểu flow.

---

## 1. Test Strategy

Xem chuẩn chi tiết tại [`TEST_GENERATION_RULES.md`](./TEST_GENERATION_RULES.md) §1, §3.6 (Right-BICEP), §3.7 (CORRECT), §15 (Tham chiếu chuẩn).

### 1.1 Tỷ lệ Pyramid cho IAM

| Layer | % count | % effort | Tốc độ chạy | Đếm hiện tại |
|-------|---------|----------|-------------|--------------|
| Unit Test | 70% | 70% | < 100ms/test | 100 test |
| Component Test | 8% | 12% | < 500ms/test | 12 test |
| Integration Test | 10% | 10% | < 5s/test | 15 test |
| Contract Test | 2% | 3% | < 1s/test | 6 test |
| E2E Test | 5% | 5% | < 30s/test | 12 test |
| Non-functional | 5% | 0% (bổ sung) | varies | 8 test |

### 1.2 Coverage Targets

| Layer | Target | Lý do |
|-------|--------|-------|
| `domain/model/` | 95% | Pure logic, dễ test, không có excuse |
| `application/usecase/` | 90% | Business rule quan trọng nhất |
| `application/facade/` | 80% | Chỉ là delegation |
| `api/controller/` | 80% | Verify status code + validation |
| `infrastructure/persistence/adapter/` | 70% | Test qua IT |
| `infrastructure/service/impl/` | 70% | Test qua IT với container |

**Mutation score** (PIT): ≥ 70% — test có khả năng phát hiện bug thật.

### 1.3 Error Code Coverage Matrix

Mỗi error code phải có ≥ 1 test. Mapping:

| Error Code | Coverage test |
|------------|---------------|
| `IAM_001` EMAIL_ALREADY_REGISTERED | `RegisterUseCaseImplTest` |
| `IAM_002` WEAK_PASSWORD | `RegisterUseCaseImplTest`, `ValidatePasswordPolicyUseCaseImplTest` |
| `IAM_003` USER_NOT_FOUND | `VerifyOtpUseCaseImplTest`, `ChangePassword`, `ResetPassword`, `ResendOtp` |
| `IAM_004` LOGIN_BAD_CREDENTIALS | `LoginUseCaseImplTest` (×2) |
| `IAM_005` ACCOUNT_LOCKED | `LoginUseCaseImplTest` |
| `IAM_006` ACCOUNT_INACTIVE | `LoginUseCaseImplTest`, `RefreshTokenUseCase` |
| `IAM_007` ACCOUNT_NOT_VERIFIED | `LoginUseCaseImplTest` |
| `IAM_011` REFRESH_TOKEN_INVALID | `RefreshTokenUseCaseImplTest` |
| `IAM_014` RATE_LIMITED | Login/Refresh/VerifyOtp/ChangePassword |
| `IAM_018` ROLE_NOT_FOUND | `RegisterUseCaseImplTest` |
| `IAM_021` AUTH_OTP_INVALID | `VerifyOtpUseCaseImplTest` |
| `IAM_022` AUTH_OTP_EXPIRED | `VerifyOtpUseCaseImplTest` |
| `IAM_026` AUTH_COOLDOWN_ACTIVE | `RegisterUseCaseImplTest`, `ResendOtpUseCase` |
| `IAM_027` AUTH_OTP_DAILY_LIMIT_EXCEEDED | `ResendOtpUseCaseImplTest` |
| `IAM_030` AUTH_PASSWORD_RECENTLY_USED | `ResetPasswordUseCaseImplTest`, `ChangePassword` |
| `IAM_031` AUTH_RESET_TOKEN_INVALID | `ResetPasswordUseCaseImplTest` |
| `IAM_033` AUTH_OAUTH_USER_NO_PASSWORD | `ForgotPassword`, `ResetPassword`, `ChangePassword` |
| `IAM_034` SERVICE_UNAVAILABLE | `ThrottlingServiceAdapterIT` |
| `IAM_GOOGLE_001/002` | `GoogleLoginUseCaseImplTest` |

---

## 2. Cấu trúc thư mục Test

```
modules/iam/src/test/java/com/pwb/iam/
├── domain/
│   └── model/
│       ├── EmailAddressTest.java          ← Unit (Right-BICEP: B, E)
│       ├── PasswordTest.java              ← Unit (B, I)
│       ├── OtpCodeTest.java               ← Unit (B, E, T)
│       ├── PasswordHistoryTest.java       ← Unit (B, C)
│       ├── PasswordResetTokenTest.java    ← Unit (B, E, T)
│       └── UserTest.java                  ← Unit (B, E, O, T)
├── application/
│   └── usecase/
│       ├── impl/
│       │   ├── RegisterUseCaseImplTest.java
│       │   ├── VerifyOtpUseCaseImplTest.java
│       │   ├── ResendOtpUseCaseImplTest.java
│       │   ├── LoginUseCaseImplTest.java
│       │   ├── RefreshTokenUseCaseImplTest.java
│       │   ├── LogoutUseCaseImplTest.java
│       │   ├── ForgotPasswordUseCaseImplTest.java
│       │   ├── ResetPasswordUseCaseImplTest.java
│       │   ├── ChangePasswordUseCaseImplTest.java
│       │   ├── GoogleLoginUseCaseImplTest.java
│       │   └── ValidatePasswordPolicyUseCaseImplTest.java
│   └── facade/
│       └── IamFacadeImplTest.java             ← Unit, verify delegation
├── api/
│   └── controller/
│       └── AuthControllerTest.java        ← Component (@WebMvcTest)
├── infrastructure/
│   ├── service/
│   │   └── impl/
│   │       ├── ThrottlingServiceAdapterIT.java      ← Integration (Redis)
│   │       ├── TokenManagerServiceAdapterIT.java     ← Integration (Redis + JWT)
│   │       ├── RedisLoginAttemptCheckerIT.java       ← Integration (Redis)
│   │       ├── SecureOtpGeneratorTest.java           ← Unit
│   │       ├── SpringPasswordHasherTest.java         ← Unit
│   │       ├── PasswordPolicyAdapterTest.java        ← Unit
│   │       ├── PasswordResetTokenServiceImplTest.java ← Unit
│   │       └── GoogleTokenVerifierAdapterIT.java     ← Integration (WireMock) [source: security/jwt/]
│   ├── persistence/
│   │   ├── adapter/
│   │   │   ├── UserRepositoryImplIT.java             ← Component (@DataJpaTest)
│   │   │   ├── OtpCodeRepositoryImplIT.java
│   │   │   ├── PasswordHistoryRepositoryImplIT.java
│   │   │   ├── PasswordResetTokenRepositoryImplIT.java
│   │   │   └── RoleRepositoryImplIT.java
│   │   └── mapper/
│   │       ├── UserMapperTest.java                   ← Unit
│   │       ├── OtpCodeMapperTest.java                ← Unit
│   │       ├── PasswordHistoryMapperTest.java        ← Unit
│   │       ├── PasswordResetTokenMapperTest.java     ← Unit
│   │       └── RoleMapperTest.java                   ← Unit
│   ├── mail/
│   │   ├── OutboxEmailDeliveryAdapterTest.java       ← Unit (mock Kafka)
│   │   └── ThymeleafEmailRendererTest.java           ← Unit
│   └── audit/
│       ├── AuditPersistListenerTest.java             ← Unit
│       └── LoggingAuthEventPublisherTest.java        ← Unit
├── contract/
│   ├── AuthEndpointContractTest.java                 ← Contract (Pact/OpenAPI)
│   └── schema/
│       └── api-v1-auth.json                          ← Generated contract
└── e2e/
    ├── AuthFlowIT.java                               ← E2E (happy path)
    ├── PasswordResetFlowIT.java                      ← E2E (recovery flow)
    ├── AccountLockoutFlowIT.java                     ← E2E (sad path)
    ├── OtpResendLimitFlowIT.java                     ← E2E (boundary)
    ├── GoogleLoginFlowIT.java                        ← E2E (happy path)
    ├── RefreshTokenRotationIT.java                   ← E2E (security)
    ├── ReRegistrationRecoveryFlowIT.java             ← E2E (UX recovery)
    └── AccountLockoutRecoveryIT.java                 ← E2E (UX recovery)
```

---

## 3. Unit Test (70% — pure logic, mock boundary)

### 3.1 Domain Model Unit Test

Áp dụng chuẩn **Right-BICEP** — đặc biệt chú trọng **Boundary** (B), **Error** (E), **Time** (T).

#### 3.1.1 `EmailAddressTest` (domain/model)

**Right-BICEP mapping**: B (boundary), E (error)

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_accept_valid_email_format` | B | `"user@example.com"` → OK |
| `should_accept_email_with_subdomain` | B | `"user@mail.example.com"` → OK |
| `should_accept_email_with_plus_tag` | B | `"user+tag@example.com"` → OK |
| `should_normalize_to_lowercase` | I | Inverse — input uppercase → output lowercase |
| `should_trim_whitespace` | I | `"  user@example.com  "` → `"user@example.com"` |
| `should_reject_null` | E | null → `IllegalArgumentException` |
| `should_reject_empty` | E | `""` → throw |
| `should_reject_invalid_format_no_at` | E | `"userexample.com"` → throw |
| `should_reject_invalid_format_no_domain` | E | `"user@"` → throw |
| `should_reject_invalid_format_no_tld` | E | `"user@example"` → throw |
| `should_reject_consecutive_dots` | E | `"user..name@example.com"` → throw |
| `should_reject_leading_dot` | E | `".user@example.com"` → throw |
| `should_reject_trailing_dot` | E | `"user.@example.com"` → throw |

#### 3.1.2 `PasswordTest` (domain/model)

**Right-BICEP mapping**: B (boundary), I (inverse)

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_accept_valid_hash` | B | `"$2a$10$abc..."` (BCrypt format) → OK |
| `should_reject_null_hash` | E | null → throw |
| `should_reject_blank_hash` | E | `""` → throw |
| `fromHash_should_create_hashed_password` | I | `isHashed() == true` |
| `empty_should_create_empty_password` | I | `Password.empty().isHashed() == false` |

#### 3.1.3 `UserTest` (domain/model)

**Right-BICEP mapping**: B, E, O (ordering), T (time)

**`createLocal`**:
| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_create_user_with_pending_verification_status` | O | status = PENDING, role = USER |
| `should_throw_when_email_null` | E | null → throw |
| `should_throw_when_password_not_hashed` | E | Plain text → throw |
| `should_default_role_to_user_when_role_null` | B | null role → USER |
| `should_throw_when_full_name_blank` | E | `""` → throw |
| `should_throw_when_full_name_too_long` | E | > 128 chars → throw |
| `should_trim_full_name_whitespace` | I | Raw "  John  " → "John" |

**`createGoogle`**:
| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_create_oauth_user_with_pending_verification` | O | provider = GOOGLE, status = PENDING |
| `should_throw_when_oauth_id_blank` | E | Blank → throw |
| `should_allow_null_full_name` | B | Google có thể không trả name |
| `should_use_default_avatar_when_null` | B | Null avatar → default |

**State transitions** (O - ordering, T - time):
| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `verifyOtp_should_set_status_to_active_from_pending` | O | PENDING → ACTIVE |
| `verifyOtp_should_throw_when_account_banned` | E | Banned → throw |
| `verifyOtp_should_throw_when_account_deleted` | E | Deleted → throw |
| `verifyOtp_should_throw_when_already_active` | E | Already active → throw |
| `linkOAuth_should_update_provider_and_id` | O | OAuth provider update |
| `changePassword_should_update_hash_and_touch` | T | updatedAt thay đổi |
| `changePassword_should_throw_when_password_not_hashed` | E | Plain → throw |
| `isOAuthUser_should_return_true_when_provider_not_local` | I | Inverse check |
| `isOAuthUser_should_return_false_when_local` | I | Inverse check |
| `updateProfile_should_trim_full_name` | I | Trim whitespace |

#### 3.1.4 `OtpCodeTest` (domain/model)

**Right-BICEP mapping**: B (boundary), E (error), T (time)

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `create_should_initialize_with_pending_status_and_zero_attempts` | I | initial state check |
| `verify_should_succeed_with_correct_code` | O | correct → status = VERIFIED |
| `verify_should_throw_when_code_mismatch` | E | Wrong code → registerFailedAttempt |
| `verify_should_lock_after_max_attempts` | B | 5 lần sai → `OtpStatus.LOCKED` |
| `verify_should_throw_when_already_locked` | E | Locked → throw |
| `verify_should_throw_when_expired` | T | After expiresAt → throw |
| `isExpired_should_return_true_when_past_expires_at` | T | Boundary: now = expiresAt - 1ms vs + 1ms |
| `markVerified_should_set_status_and_timestamp` | T | verifiedAt = now |
| `isLocked_should_return_true_when_status_locked_or_max_attempts` | B | 2 trường hợp locked |

> **Mock cho OtpGenerator**: tạo `StubOtpGenerator implements OtpGenerator` với map `raw → hash` cố định, dùng lại trong mọi test cần `verify(rawCode, codeHash)`.

#### 3.1.5 `PasswordHistoryTest` (domain/model)

**Right-BICEP mapping**: B (boundary), C (cardinality)

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_create_with_userId_and_passwordHash` | B | happy path |
| `should_throw_when_userId_null` | E | null → throw |
| `should_throw_when_passwordHash_null` | E | null → throw |
| `should_throw_when_passwordHash_blank` | E | blank → throw |
| `should_have_max_history_size_constant_of_5` | C | `MAX_HISTORY_SIZE == 5` |

#### 3.1.6 `PasswordResetTokenTest` (domain/model)

**Right-BICEP mapping**: B (boundary), E (error), T (time)

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_create_with_userId_tokenHash_expiresAt` | B | happy path |
| `should_throw_when_userId_null` | E | null → throw |
| `should_throw_when_tokenHash_null_or_blank` | E | null/blank → throw |
| `should_throw_when_expiresAt_null` | E | null → throw |
| `should_be_expired_when_past_expiresAt` | T | now > expiresAt → true |
| `should_not_be_expired_when_before_expiresAt` | T | now < expiresAt → false |
| `should_be_usable_when_not_used_and_not_expired` | B | `isUsable() == true` |
| `should_not_be_usable_when_used` | B | `markUsed()` → `isUsable() == false` |
| `should_set_usedAt_when_marked_used` | T | `markUsed(now)` → `usedAt == now` |

---

### 3.2 Application Use Case Unit Test

Áp dụng chuẩn **Right-BICEP** + **CORRECT** — đặc biệt **O** (ordering), **R** (reference), **T** (time).

#### 3.2.1 `RegisterUseCaseImplTest` (application/usecase)

> **Setup**: Mock tất cả dependency bằng Mockito. Dùng `StubOtpGenerator` cho OTP generation, `StubPasswordHasher` cho password (trả về hash cố định).

**CORRECT mapping**: O (ordering), R (reference), E (error), T (time)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_register_local_user_when_email_valid_and_password_strong` | O, R | user save với `status = PENDING_VERIFICATION`, role = `USER`, oauthProvider = `LOCAL`; OTP tạo với `purpose = REGISTER`; email enqueue template `OTP_REGISTER`; publish `publishOtpIssued()` |
| `should_throw_when_email_already_registered` | E | `existsByEmail()` return true → `BusinessException(IAM_001)` — không save, không gửi OTP |
| `should_throw_when_weak_password` | E | `passwordPolicyService.validate()` invalid → `BusinessException(IAM_002)` với metadata `violations` |
| `should_throw_when_throttled_by_register_cooldown` | E | `enforceCooldown(REGISTER)` > 0 → `BusinessException(IAM_026)` với `cooldownSeconds` |
| `should_throw_when_default_role_not_found` | E | `roleRepository.findByName(USER)` empty → `BusinessException(IAM_018)` |
| `should_normalize_email_to_lowercase_and_trim` | I | `"  User@A.COM  "` → `"user@a.com"` |
| `should_invalidate_old_otp_before_issuing_new_one` | O | `deleteAllByUserAndPurpose()` gọi trước khi save OTP mới |
| `should_set_otp_expiry_from_properties` | T | `expiresAt = now + Duration.ofMinutes(otpProperties.ttlMinutes)` |

#### 3.2.2 `VerifyOtpUseCaseImplTest` (application/usecase)

**Right-BICEP + CORRECT**: E, T, O

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_activate_user_and_issue_tokens_when_otp_correct` | O | `user.verifyOtp()` → ACTIVE; issue access + refresh token; publish `publishAuthSuccess()` + `publishOtpVerified()` |
| `should_throw_user_not_found` | E | `findById()` empty → `BusinessException(IAM_003)` |
| `should_throw_otp_expired_when_no_active_otp` | T | `findActiveByUserAndPurpose()` empty → `BusinessException(IAM_022)` |
| `should_throw_otp_invalid_when_code_mismatch` | E | `OtpCode.verify()` throw → rethrow `OtpVerificationException(IAM_021)` |
| `should_throw_otp_locked_after_5_wrong_attempts` | B | 5 lần → `OtpStatus.LOCKED` |
| `should_throw_rate_limited_when_per_user_limit_exceeded` | E | throttling user key deny → `BusinessException(IAM_014)` với `retryAfterSeconds` |
| `should_throw_rate_limited_when_per_ip_limit_exceeded` | E | throttling IP key deny → `IAM_014` |
| `should_set_next_step_complete_profile_when_onboarding_incomplete` | O | `user.isOnboardingIncomplete()` → result.nextStep = `COMPLETE_PROFILE` |

#### 3.2.3 `LoginUseCaseImplTest` (application/usecase)

**Use case có nhiều rule nhất — phải cover kỹ**

**Right-BICEP + CORRECT**: E, O, R, T

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_login_success_when_credentials_valid_and_user_active` | O, R | rate limit (2 key: ip + email) consumed; issue access + refresh; `attemptChecker.reset(email)` + `resetIpLock(email, ip)`; publish `publishAuthSuccess()` |
| `should_throw_bad_credentials_when_user_not_found` | E | `findByEmail()` empty → `recordFailure()`; publish `publishLoginFailed(reason=USER_NOT_FOUND)`; `BusinessException(IAM_004)` |
| `should_throw_bad_credentials_when_password_mismatch` | E | `passwordHasher.matches()` false → `recordFailure()`; publish `publishLoginFailed(reason=BAD_CREDENTIALS)` |
| `should_throw_account_locked_when_attempts_exceeded` | E | `isLocked()` true → KHÔNG gọi `findByEmail()`; publish `reason=ACCOUNT_LOCKED`; `BusinessException(IAM_005)` với `retryAfterSeconds` |
| `should_throw_account_inactive_when_status_banned` | E | status BANNED → `IAM_006` |
| `should_throw_account_inactive_when_status_deleted` | E | status DELETED → `IAM_006` |
| `should_throw_account_not_verified_when_status_pending` | E | status PENDING → `IAM_007` |
| `should_throw_rate_limited_when_per_ip_login_exceeded` | E | `consume("login:ip:*")` deny → `IAM_014` |
| `should_throw_rate_limited_when_per_email_login_exceeded` | E | `consume("login:email:*")` deny → `IAM_014` |
| `should_handle_null_client_ip_as_unknown` | B | null IP → "unknown" key |
| `should_handle_null_password_gracefully` | B | OAuth user với `password = null` → `IAM_004` |
| `should_reset_attempts_after_successful_login` | O | Login success → attempt reset |
| `should_return_complete_profile_next_step_when_onboarding_incomplete` | O | result.nextStep = `COMPLETE_PROFILE` |

#### 3.2.4 `ResendOtpUseCaseImplTest` (application/usecase)

**CORRECT**: O, T, E

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_resend_otp_when_cooldown_passed_and_under_daily_limit` | O, T | old OTP xóa, OTP mới save, email enqueue |
| `should_throw_user_not_found` | E | `findById()` empty → `IAM_003` |
| `should_throw_rate_limit_when_resend_cooldown_active` | T | `enforceCooldown(RESEND_OTP)` > 0 → `IAM_026` với `cooldownSeconds` |
| `should_throw_daily_limit_when_exceeded` | C | `countIssuedToday()` >= `dailyLimit` → `IAM_027` |

#### 3.2.5 `RefreshTokenUseCaseImplTest`

**CORRECT**: O, R, E

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_issue_new_tokens_when_refresh_token_valid` | O, R | `rotateRefreshToken()` → `findById(rotated.userId())` → `issueAccessToken()` → publish `publishAuthSuccess()` |
| `should_throw_rate_limited_when_per_ip_refresh_exceeded` | E | `consume("refresh:ip:*")` deny → `IAM_014` |
| `should_throw_refresh_token_invalid_when_redis_missing` | E | `rotateRefreshToken()` throw IllegalArgumentException → `RefreshTokenInvalidException(IAM_011)` |
| `should_throw_user_not_found_after_token_rotation` | E | `findById()` empty → `IAM_003` |
| `should_throw_account_inactive_when_user_banned` | E | status BANNED → `IAM_006` |
| `should_throw_account_inactive_when_user_deleted` | E | status DELETED → `IAM_006` |
| `should_handle_null_client_ip_as_unknown` | B | null IP → "unknown" |

#### 3.2.6 `LogoutUseCaseImplTest`

**Right-BICEP**: B, E, I (inverse)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_revoke_refresh_token_when_provided` | O | `revokeRefreshToken(rawRefreshToken)` gọi |
| `should_blacklist_access_token_when_jti_and_ttl_provided` | O | `blacklistAccessToken(jti, ttl)` gọi |
| `should_publish_logout_event` | O | `publishLogout()` gọi |
| `should_handle_null_refresh_token_gracefully` | B | `rawRefreshToken == null` → KHÔNG gọi `revokeRefreshToken` |
| `should_handle_blank_refresh_token_gracefully` | B | `""` → graceful |
| `should_skip_blacklist_when_ttl_zero` | B | `accessExpiresInSeconds == 0` → KHÔNG gọi `blacklistAccessToken` |
| `should_skip_blacklist_when_jti_blank` | B | jti blank → skip |

#### 3.2.7 `ForgotPasswordUseCaseImplTest`

**Corner**: silent failure (information disclosure prevention)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_send_reset_email_when_user_exists_and_active` | O, T | reset token save với hash + expiresAt; email enqueue `PASSWORD_RESET`; `invalidateAllForUser()` gọi; `publishPasswordResetRequested()` |
| `should_return_silent_result_when_user_not_found` | I | `findByEmail()` empty → KHÔNG enqueue, KHÔNG publish → `Result.sent(null, cooldown)` |
| `should_return_silent_result_when_user_not_active` | I | status != ACTIVE → silent |
| `should_throw_when_oauth_user` | E | oauthProvider = GOOGLE → `BusinessException(IAM_033)` |
| `should_throw_rate_limit_when_cooldown_active` | E | `enforceCooldownForPasswordReset()` > 0 → `IAM_014` |
| `should_build_reset_link_with_locale` | O | link chứa locale |

#### 3.2.8 `ResetPasswordUseCaseImplTest`

**Use case phức tạp nhất — nhiều guard clause. CORRECT**: O, R, E, C (cardinality)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_reset_password_when_token_valid_and_new_password_strong` | O, R, C | old password save vào `PasswordHistory`; new password không match history; `validatePasswordPolicyUseCase.validate()` pass; `user.changePassword()`; reset token mark used; `revokeAllRefreshTokensForUser()`; `publishPasswordChanged()` |
| `should_throw_invalid_token_when_signature_invalid` | E | `verifySignature()` false → `IAM_031` |
| `should_throw_invalid_token_when_raw_token_blank` | E | blank → `IAM_031` |
| `should_throw_invalid_token_when_no_active_token_in_db` | E | DB empty → `IAM_031` |
| `should_throw_user_not_found` | E | `findById()` empty → `IAM_003` |
| `should_throw_oauth_user_no_password` | E | OAuth user → `IAM_033` |
| `should_throw_password_recently_used` | C | history có entry match → `IAM_030` |
| `should_throw_weak_password` | E | `policy.validate()` invalid → `IAM_002` |
| `should_trim_history_to_max_size_when_exceeded` | C | count > 5 → `deleteOldestByUserId()` với `(count - 5)` |

#### 3.2.9 `ChangePasswordUseCaseImplTest`

**CORRECT**: O, R, C, E

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_change_password_when_current_correct_and_new_strong` | O | success path |
| `should_throw_user_not_found` | E | `IAM_003` |
| `should_throw_oauth_user_no_password` | E | OAuth → `IAM_033` |
| `should_throw_invalid_current_password_when_null` | E | null current → `IAM_015` |
| `should_throw_invalid_current_password_when_mismatch` | E | mismatch → `IAM_015` |
| `should_throw_password_recently_used` | C | history match → `IAM_030` |
| `should_throw_password_reused` | C | newPassword == currentPassword → `IAM_016` |
| `should_throw_weak_password` | E | weak → `IAM_002` |
| `should_throw_rate_limited_per_user` | E | per-user limit → `IAM_014` |
| `should_throw_rate_limited_per_ip` | E | per-IP limit → `IAM_014` |
| `should_revoke_all_refresh_tokens_after_change` | O | `revokeAllRefreshTokensForUser()` gọi |
| `should_save_old_password_to_history_before_changing` | O, C | old password → history |
| `should_trim_history_to_max_size` | C | count > 5 → trim |

#### 3.2.10 `GoogleLoginUseCaseImplTest`

> **Mock `GoogleTokenVerifierPort`**: trả `GoogleUserInfo` cố định với email + sub + name + picture.

**CORRECT**: O, R, E, C

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_login_new_google_user_when_email_not_registered` | O, R | `User.createGoogle()` → markActive; email enqueue `WELCOME_GOOGLE`; publish `publishUserRegisteredGoogle()` |
| `should_link_existing_user_when_email_match_but_no_oauth` | O | `user.linkOAuth(GOOGLE, payload.sub())`; status PENDING → ACTIVE; avatar/name update nếu trống; publish `publishUserLinkedGoogle()` |
| `should_update_existing_google_user_when_login_again` | O | status PENDING → ACTIVE; avatar update nếu trống; KHÔNG enqueue welcome email |
| `should_throw_invalid_token_when_verifier_fails` | E | `verify()` throw → publish `publishGoogleLoginFailed()`; rethrow |
| `should_throw_account_inactive_when_existing_user_banned` | E | existing BANNED → `IAM_006` |
| `should_throw_account_inactive_when_existing_user_deleted` | E | existing DELETED → `IAM_006` |
| `should_throw_account_inactive_when_new_link_banned` | E | new link BANNED → `IAM_006` |
| `should_throw_rate_limited_per_ip` | E | per-IP → `IAM_014` |
| `should_throw_rate_limited_per_email` | E | per-email → `IAM_014` |
| `should_handle_null_client_ip_as_unknown` | B | null IP → "unknown" |
| `should_use_locale_for_welcome_email` | O | vi/en locale |

#### 3.2.11 `ValidatePasswordPolicyUseCaseImplTest`

**Right-BICEP**: B, E

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_pass_when_policy_valid` | B | valid password → OK |
| `should_throw_weak_password_when_policy_invalid` | E | `validate()` invalid với messages → `BusinessException(IAM_002)` với metadata `violations` |

> **Không test** `PasswordPolicyAdapter` ở unit level — chỉ wrap `PasswordPolicyProperties` thành rules. Test integration qua các use case trên.

#### 3.2.12 `IamFacadeImplTest`

**Mục đích**: Verify `IamFacadeImpl` chỉ delegate đúng đến use case, không thêm logic.

| Test case | Verify |
|-----------|--------|
| `should_delegate_register_to_RegisterUseCase` | `facade.register(cmd)` → `useCase.execute(cmd)` |
| `should_delegate_verifyOtp_to_VerifyOtpUseCase` | `facade.verifyOtp(cmd)` → `useCase.execute(cmd)` |
| `should_delegate_login_to_LoginUseCase` | `facade.login(cmd)` → `useCase.execute(cmd)` |
| `should_delegate_refresh_to_RefreshTokenUseCase` | `facade.refresh(cmd)` → `useCase.execute(cmd)` |
| `should_delegate_logout_to_LogoutUseCase` | `facade.logout(cmd)` → `useCase.execute(cmd)` |
| `should_delegate_forgotPassword_to_ForgotPasswordUseCase` | delegation |
| `should_delegate_resetPassword_to_ResetPasswordUseCase` | delegation |
| `should_delegate_changePassword_to_ChangePasswordUseCase` | delegation |
| `should_delegate_googleLogin_to_GoogleLoginUseCase` | delegation |
| `should_delegate_resendOtp_to_ResendOtpUseCase` | delegation |

---

### 3.3 Infrastructure Unit Test (không cần container)

#### 3.3.1 `SecureOtpGeneratorTest` (unit, no Redis)

**Right-BICEP**: I (inverse), C (cross-check)

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_generate_code_with_configured_length` | B | length = configured |
| `should_generate_different_codes_each_call` | I | 2 call → khác nhau |
| `should_hash_code_deterministically` | C | cùng input → cùng hash |
| `should_match_when_codes_equal` | O | verify thành công |
| `should_not_match_when_codes_different` | E | verify fail |

#### 3.3.2 `SpringPasswordHasherTest` (unit)

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_hash_password_with_bcrypt` | C | dùng BCrypt format |
| `should_match_when_password_correct` | O | match |
| `should_not_match_when_password_wrong` | E | mismatch |
| `should_produce_different_hash_each_call` | I | salt random |

#### 3.3.3 `PasswordResetTokenServiceImplTest` (unit)

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_generate_signed_token_with_valid_format` | C | HMAC-SHA256 format |
| `should_verify_signature_when_valid` | O | match |
| `should_fail_signature_verification_when_tampered` | E | tamper → fail |
| `should_extract_raw_token` | O | extract được raw |
| `should_hash_for_storage_deterministically` | C | SHA-256 hash |
| `should_build_reset_link_with_frontend_url` | O | URL đúng |

#### 3.3.4 `PasswordPolicyAdapterTest` (unit)

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_validate_when_password_meets_all_rules` | B | đủ length, có uppercase, có digit, có special |
| `should_return_violations_when_password_too_short` | E | < 12 chars → violations |
| `should_return_violations_when_password_missing_uppercase` | E | violations |
| `should_return_violations_when_password_missing_digit` | E | violations |
| `should_return_violations_when_password_missing_special` | E | violations |

#### 3.3.5 Mapper Tests (unit)

Áp dụng cho **6 mapper**: `UserMapperTest`, `OtpCodeMapperTest`, `PasswordHistoryMapperTest`, `PasswordResetTokenMapperTest`, `RoleMapperTest`, `AuditLogMapperTest`.

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_map_entity_to_domain` | I | Entity → Domain |
| `should_map_domain_to_entity` | I | Domain → Entity |
| `should_preserve_all_fields` | C | round-trip giữ nguyên value |
| `should_handle_null_optional_fields` | B | null → null |

#### 3.3.6 `OutboxEmailDeliveryAdapterTest` (unit, mock Kafka)

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_enqueue_to_outbox_when_email_sent` | O | outbox row inserted |
| `should_publish_to_kafka_after_outbox_inserted` | R | Kafka send sau khi DB commit |
| `should_fail_gracefully_when_kafka_unavailable` | B | outbox vẫn insert, retry Kafka |

#### 3.3.7 `ThymeleafEmailRendererTest` (unit)

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_render_template_with_variables` | O | template + vars → HTML |
| `should_throw_when_template_missing` | E | template not found |
| `should_escape_html_in_user_input` | C | XSS prevention |

#### 3.3.8 `AuditPersistListenerTest`

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_capture_pre_persist_event` | O | JPA event handler |
| `should_set_createdAt_on_new_entity` | T | timestamp |

#### 3.3.9 `LoggingAuthEventPublisherTest`

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_log_auth_success_with_trace_id` | O | log format |
| `should_log_login_failed_with_reason` | O | log reason |
| `should_not_log_sensitive_data` | E | password/token KHÔNG bao giờ log |

---

## 4. Component Test (15% — slice test)

### 4.1 `AuthControllerTest` (`@WebMvcTest`)

> **Setup**: `@WebMvcTest(AuthController.class)` + `@MockBean IamFacade` + import `GlobalExceptionHandler` + `IamExceptionHandler` + `MessageSource` mock.

**Right-BICEP**: B, E, I

#### Register endpoint

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_201_with_userId_when_register_valid` | O | status 201, body có `success=true`, `data.userId` |
| `should_return_400_when_email_blank` | E | validation |
| `should_return_400_when_email_invalid_format` | E | validation |
| `should_return_400_when_password_too_short` | B | < 12 chars |
| `should_return_400_when_password_too_long` | B | > 128 chars |
| `should_return_400_when_fullName_blank` | E | validation |
| `should_return_400_when_fullName_too_long` | B | > 128 chars |
| `should_return_409_when_email_already_registered` | E | facade throw `IAM_001` → 409 |
| `should_return_400_when_password_weak` | E | facade throw `IAM_002` → 400 |

#### Verify OTP endpoint

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_200_with_tokens_when_otp_correct` | O | body có `accessToken`, `refreshToken`, `expiresInSeconds`, `nextStep` |
| `should_return_400_when_code_too_short` | B | < 6 chars |
| `should_return_400_when_userId_null` | E | null ID |
| `should_return_404_when_user_not_found` | E | facade throw `IAM_003` |

#### Login endpoint

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_200_with_tokens_when_login_success` | O | tokens |
| `should_return_401_when_bad_credentials` | E | `IAM_004` |
| `should_return_429_when_account_locked` | E | `IAM_005`, body có `retryAfterSeconds` |
| `should_return_429_when_rate_limited` | E | `IAM_014` |
| `should_return_403_when_account_inactive` | E | `IAM_006` |
| `should_return_403_when_account_not_verified` | E | `IAM_007` |
| `should_pass_client_ip_and_user_agent_to_command` | O | command có IP/UA từ header |

#### Refresh endpoint

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_200_with_new_tokens_when_refresh_valid` | O | new tokens |
| `should_return_401_when_refresh_invalid` | E | `IAM_011` |
| `should_return_429_when_rate_limited` | E | `IAM_014` |

#### Logout endpoint

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_200_when_logout_success` | O | success |
| `should_return_401_when_userId_null` | E | null → KHÔNG gọi `iamFacade.logout()` |
| `should_return_200_when_logout_without_refresh_token` | B | optional refresh |
| `should_call_blacklist_when_jti_and_ttl_provided` | O | blacklist called |

#### Resend OTP endpoint

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_200_when_resend_success` | O | success |
| `should_return_429_when_cooldown_active` | E | `IAM_026` |
| `should_return_429_when_daily_limit_exceeded` | E | `IAM_027` |
| `should_return_404_when_user_not_found` | E | `IAM_003` |

#### Google Login endpoint

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_200_when_google_token_valid` | O | success |
| `should_return_200_when_google_token_with_default_locale` | B | no header → "vi" |
| `should_return_200_when_google_token_with_en_locale` | O | "en" header |
| `should_return_400_when_idToken_blank` | B | blank |
| `should_return_401_when_google_token_invalid` | E | `IAM_GOOGLE_001` |

#### Forgot Password endpoint

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_200_when_email_exists_and_active` | O | success |
| `should_return_200_silent_when_email_not_found` | I | silent (security) |
| `should_return_429_when_cooldown_active` | E | `IAM_014` |

#### Reset Password endpoint

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_200_when_reset_success` | O | success |
| `should_return_400_when_token_blank` | B | blank |
| `should_return_400_when_password_too_short` | B | < 12 |
| `should_return_400_when_token_invalid_or_expired` | E | `IAM_031` |

#### Change Password endpoint

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_200_when_change_success` | O | success |
| `should_return_401_when_userId_null` | E | null |
| `should_return_400_when_current_password_mismatch` | E | `IAM_015` |
| `should_return_400_when_new_password_too_short` | B | < 12 |
| `should_return_429_when_rate_limited` | E | `IAM_014` |

#### Locale resolution (private method, test qua endpoint)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_default_to_vi_when_accept_language_blank` | B | blank → "vi" |
| `should_default_to_vi_when_accept_language_unsupported` | B | "fr" → "vi" |
| `should_use_en_when_accept_language_en` | O | "en" |
| `should_use_vi_when_accept_language_vi` | O | "vi" |

---

### 4.2 `IamExceptionHandlerTest` (`@WebMvcTest` + DataIntegrityViolationException)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_409_with_field_email_when_email_conflict` | E | constraint "email" → 409 |
| `should_return_409_with_unknown_field_when_no_constraint_match` | E | unknown → 409 |
| `should_return_i18n_message_when_locale_vi` | O | vi message |
| `should_fallback_to_key_when_message_missing` | B | fallback |

---

## 5. Integration Test (10% — adapter + container)

### 5.1 Repository IT (`@DataJpaTest` + Testcontainers PostgreSQL)

> **Container**: Testcontainers Postgres. **Dùng H2** cho đơn giản, hoặc Testcontainers Postgres nếu cần test Flyway migration.

Cho mỗi `*RepositoryImpl`:

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_save_and_find_by_id` | I | round-trip |
| `should_return_empty_when_not_found` | E | empty result |
| `should_update_existing_entity` | O | update field |
| `should_delete_entity` | O | delete OK |
| `should_findByEmail` | O | query method |
| `should_findActiveByUserAndPurpose` | O | query method với filter active |
| `should_count_issued_today` | T | count by date range |
| `should_invalidate_all_for_user` | O | bulk update |

**Files**: `UserRepositoryImplIT`, `OtpCodeRepositoryImplIT`, `PasswordHistoryRepositoryImplIT`, `PasswordResetTokenRepositoryImplIT`, `RoleRepositoryImplIT`.

### 5.2 `TokenManagerServiceAdapterIT` (Testcontainers Redis)

> **Container**: `GenericContainer("redis:7-alpine").withExposedPorts(6379)`

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_issue_access_token_with_valid_jwt_structure` | C | 3 phần header.payload.signature; subject = userId; claim email/role/status |
| `should_reject_access_token_with_wrong_secret` | E | `JwtError.INVALID_SIGNATURE` |
| `should_detect_expired_access_token` | T | `JwtError.EXPIRED` |
| `should_issue_refresh_token_and_persist_in_redis` | O | `redis.opsForValue().get(tokenKey(hash))` = userId; `redis.opsForSet().isMember(userSetKey, hash)` true |
| `should_rotate_refresh_token_and_invalidate_old` | O | old key xóa, new key tồn tại, user set update |
| `should_throw_when_rotate_invalid_refresh_token` | E | throw |
| `should_blacklist_access_token` | O | blacklist key set |
| `should_check_access_token_blacklisted` | I | check OK |
| `should_revoke_refresh_token` | O | revoke single |
| `should_revoke_all_refresh_tokens_for_user` | C | issue 3 → revoke all 3 |
| `should_redis_down_gracefully` | B | fail-open/fail-closed policy |

### 5.3 `ThrottlingServiceAdapterIT` (Testcontainers Redis)

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_allow_under_limit` | B | < limit → allow |
| `should_deny_at_limit_boundary` | B | = limit → deny |
| `should_set_ttl_on_first_consume` | T | TTL set |
| `should_increment_counter_on_each_consume` | C | increment |
| `should_reset_after_window_expires` | T | FLUSHDB hoặc đợi |
| `should_set_register_cooldown_key` | O | cooldown key |
| `should_set_resend_otp_cooldown_key` | O | cooldown key |
| `should_set_password_reset_cooldown_key` | O | cooldown key |
| `should_return_remaining_when_cooldown_active` | I | remaining time |
| `should_clear_cooldown_after_ttl` | T | TTL expiry |
| `should_fail_open_when_redis_unavailable_and_not_critical` | E | `consume()` return `allow(limit)` |
| `should_fail_closed_when_critical_and_redis_unavailable` | E | `consume()` throw `BusinessException(SERVICE_UNAVAILABLE)` |

### 5.4 `RedisLoginAttemptCheckerIT` (Testcontainers Redis)

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_record_failure_and_lock_after_threshold` | B | 5 lần → lock |
| `should_not_be_locked_initially` | I | initial state |
| `should_reset_after_success` | O | reset OK |
| `should_reset_ip_lock_after_success` | O | IP reset |
| `should_track_failures_per_ip` | C | 3 fail IP A + 3 fail IP B → cả 2 locked |
| `should_unlock_after_ttl` | T | TTL expiry |

### 5.5 `GoogleTokenVerifierAdapterIT` (WireMock)

> **Mock Google API** bằng cách inject custom `GoogleIdTokenVerifier` hoặc dùng `WireMock`.

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_return_user_info_when_token_valid` | O | WireMock trả valid JWT |
| `should_throw_when_token_malformed` | E | malformed |
| `should_throw_when_token_expired` | T | expired |
| `should_throw_when_audience_mismatch` | E | wrong audience |
| `should_throw_when_issuer_mismatch` | E | wrong issuer |
| `should_throw_when_email_not_verified` | E | email_verified = false |

### 5.6 `SecurityConfigIT` (`@SpringBootTest` + MockMvc)

> **Mục đích**: Verify security rules — endpoint nào public, endpoint nào cần authentication.

| Test case | Chuẩn | Mô tả |
|-----------|-------|--------|
| `should_allow_register_without_auth` | B | POST `/register` → không 401 |
| `should_allow_login_without_auth` | B | POST `/login` → không 401 |
| `should_allow_verify_otp_without_auth` | B | POST `/verify-otp` → không 401 |
| `should_allow_resend_otp_without_auth` | B | POST `/resend-otp` → không 401 |
| `should_allow_google_login_without_auth` | B | POST `/google-login` → không 401 |
| `should_allow_forgot_password_without_auth` | B | POST `/forgot-password` → không 401 |
| `should_allow_reset_password_without_auth` | B | POST `/reset-password` → không 401 |
| `should_allow_refresh_without_auth` | B | POST `/refresh` → không 401 |
| `should_reject_logout_without_auth` | E | POST `/logout` → 401 |
| `should_reject_change_password_without_auth` | E | POST `/change-password` → 401 |
| `should_allow_logout_with_valid_token` | O | Valid JWT → 200 |
| `should_allow_change_password_with_valid_token` | O | Valid JWT → 200 |
| `should_disable_csrf` | B | CSRF disabled cho API |

---

## 6. Contract Test (3% — OpenAPI/Pact)

### 6.1 `AuthEndpointContractTest`

Mục đích: Verify HTTP contract khớp với OpenAPI spec (status code, header, body schema).

| Endpoint | Test case | Chuẩn |
|----------|-----------|-------|
| POST /api/v1/auth/register | `should_match_openapi_register_response` | C |
| POST /api/v1/auth/verify-otp | `should_match_openapi_verify_response` | C |
| POST /api/v1/auth/login | `should_match_openapi_login_response` | C |
| POST /api/v1/auth/refresh | `should_match_openapi_refresh_response` | C |
| POST /api/v1/auth/logout | `should_match_openapi_logout_response` | C |
| POST /api/v1/auth/google-login | `should_match_openapi_google_response` | C |

---

## 7. E2E Test (5% — full flow người dùng)

**Right-BICEP + CORRECT**: O (ordering), R (reference), T (time), E (error)

### 7.1 `AuthFlowIT` (`@SpringBootTest` + Testcontainers Redis + Postgres)

> **Setup**: Full Spring context, Postgres container, Redis container, Flyway migration chạy, UserSeeder disabled.

| Test case | Chuẩn | Flow |
|-----------|-------|------|
| `should_register_then_verify_then_login_then_logout` | O, R, T | 1. Register → nhận userId; 2. Lấy OTP từ email mock; 3. Verify OTP → nhận tokens; 4. Login → tokens mới; 5. Logout; 6. Verify: accessToken blacklist (gọi API khác → 401); 7. Verify: refreshToken cũ không rotate được |
| `should_register_then_login_after_verification` | O | Register → Verify → Login |
| `should_fail_login_when_not_verified` | E, O | Register → skip verify → Login → 403 `IAM_007` |

### 7.2 `PasswordResetFlowIT`

| Test case | Chuẩn | Flow |
|-----------|-------|------|
| `should_forgot_then_reset_then_login_with_new_password` | O, R, T | Register + verify → Login → Forgot → extract token → Reset → Login old password fail → Login new password success |
| `should_reject_reset_when_token_expired` | T, E | reset token expired → 400 |

### 7.3 `AccountLockoutFlowIT`

| Test case | Chuẩn | Flow |
|-----------|-------|------|
| `should_lock_account_after_5_failed_logins` | B, T | Register + verify → Login fail 5 lần → Login lần 6 → 429 `IAM_005` → Sau TTL → Login success |
| `should_reset_attempts_after_successful_login` | R, O | Login fail 4 lần → Login success → Login fail 4 lần tiếp → Login lần 5 success |

### 7.4 `OtpResendLimitFlowIT`

| Test case | Chuẩn | Flow |
|-----------|-------|------|
| `should_throttle_resend_otp_within_cooldown` | T | Register → Resend → success → Resend → 429 `IAM_026` |
| `should_throw_daily_limit_when_exceeded` | C | Resend 10 lần (clear cooldown) → Lần 11 → 429 `IAM_027` |

### 7.5 `GoogleLoginFlowIT`

| Test case | Chuẩn | Flow |
|-----------|-------|------|
| `should_login_with_new_google_account` | O | Mock Google → Google login → success, ACTIVE |
| `should_link_existing_local_account_to_google` | O, R | Register + verify local → Google login cùng email → link success, ACTIVE |
| `should_login_existing_google_user` | O | Google login → existing user → success |

### 7.6 `RefreshTokenRotationIT`

| Test case | Chuẩn | Flow |
|-----------|-------|------|
| `should_rotate_refresh_token_and_invalidate_old` | O, R | Login → refreshToken → Refresh → new token → Refresh LẠI với token cũ → 401 `IAM_011`/`IAM_013` |

### 7.7 `ReRegistrationRecoveryFlowIT` (UX Recovery)

> **Mục đích**: Cover các flow người dùng thật hay gặp sự cố — đăng ký → không verify → đăng ký lại.

| Test case | Chuẩn | Flow |
|-----------|-------|------|
| `should_auto_resend_otp_when_pending_user_tries_to_register_again` | O, R | Register lần 1 → không verify → Register lần 2 (qua cooldown) → expect 201 + OTP mới enqueue (KHÔNG throw `IAM_001`) |
| `should_reject_re_registration_when_first_user_active` | E | Register + verify → ACTIVE → Register lần 2 → 409 `IAM_001` |
| `should_reject_re_registration_when_within_cooldown` | T | Register lần 1 → Register lần 2 ngay → 429 `IAM_026` |
| `should_clear_old_otp_when_re_registering_pending_user` | O | Register lần 1 → OTP cũ tồn tại → Register lần 2 → OTP cũ verify fail → OTP mới verify success |
| `should_silently_re_register_when_user_banned` | E | User BANNED tồn tại → Register cùng email → UI flow xử lý (silent hoặc 403) |

### 7.8 `AccountLockoutRecoveryIT` (UX Recovery)

| Test case | Chuẩn | Flow |
|-----------|-------|------|
| `should_allow_login_after_lockout_window_expires` | T | Login fail 5 lần → wait TTL → Login success |
| `should_show_remaining_lockout_time_in_response` | T | Login fail 5 lần → Login → response có `retryAfterSeconds` |
| `should_not_count_successful_login_as_failure` | R | Login fail 4 + Login success + Login fail 4 → không bao giờ locked |

---

## 8. Non-functional Test

### 8.1 Performance Test

| Test case | Tool | SLA |
|-----------|------|-----|
| `should_handle_1000_RPS_login` | Gatling / JMeter | p99 < 200ms |
| `should_handle_500_RPS_register` | Gatling | p99 < 300ms |
| `should_redis_pool_not_exhausted_under_load` | Gatling | no pool timeout |

### 8.2 Security Test

| Test case | Tool | Scope |
|-----------|------|-------|
| `should_prevent_sql_injection_in_email_field` | OWASP ZAP | All endpoints |
| `should_prevent_xss_in_fullName_field` | OWASP ZAP | Register endpoint |
| `should_not_leak_user_existence_in_forgot_password` | Custom | Forgot endpoint |
| `should_not_allow_jwt_tampering` | Custom | All authenticated endpoints |
| `should_not_allow_refresh_token_reuse` | Custom | Refresh endpoint |
| `should_rate_limit_by_ip_global` | Custom | All endpoints |
| `should_not_log_sensitive_data` | Logback test | All loggers |

### 8.3 i18n Test

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_vi_message_for_vi_locale` | O | vi message |
| `should_return_en_message_for_en_locale` | O | en message |
| `should_fallback_to_key_when_translation_missing` | B | fallback OK |
| `should_not_throw_when_message_source_unavailable` | E | graceful |

### 8.4 Idempotency Test

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_same_userId_when_register_called_twice_with_same_email` | O | idempotent |
| `should_not_send_duplicate_otp_email_idempotently` | C | de-dup |

### 8.5 Time & Concurrency Test

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_handle_clock_skew_in_otp_expiry` | T | clock skew tolerance |
| `should_handle_concurrent_registration_of_same_email` | C | unique constraint + race condition |
| `should_handle_concurrent_otp_verify` | C | atomic verify |
| `should_handle_jwt_replay_attack` | E | blacklist check |

---

## 9. Test Configuration & Helpers

### 9.1 Testcontainers base class

```java
@Testcontainers
public abstract class AbstractIntegrationTest {
    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("pwb_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("pwb.iam.seeder.enabled", () -> "false");
    }
}
```

### 9.2 Stub classes cần tạo

```java
public class StubOtpGenerator implements OtpGenerator {
    private final Map<String, String> hashes = new ConcurrentHashMap<>();

    public String presetHash(String rawCode, String hash) {
        hashes.put(rawCode, hash);
        return hash;
    }

    @Override
    public String generate() { return "123456"; }

    @Override
    public String hash(String raw) { return "hash:" + raw; }

    @Override
    public boolean matches(String raw, String hash) {
        return hash.equals("hash:" + raw) || hashes.getOrDefault(raw, "").equals(hash);
    }
}

public class StubPasswordHasher implements PasswordHasher {
    @Override
    public String hash(String raw) { return "hashed:" + raw; }

    @Override
    public boolean matches(String raw, String hash) { return hash.equals("hashed:" + raw); }
}

public class InMemoryEmailDeliveryAdapter implements EmailDeliveryPort {
    public final List<EmailEnqueueCommand> sent = new ArrayList<>();

    @Override
    public void enqueue(EmailEnqueueCommand cmd) { sent.add(cmd); }
}
```

### 9.3 Test data builders

```java
public class TestUserBuilder {
    public static User localActive() {
        return User.rehydrate(
            UUID.randomUUID(),
            "test@example.com",
            "hashed:Pass1234!",
            "Test User",
            null, null,
            UserStatus.ACTIVE,
            "USER",
            OAuthProvider.LOCAL,
            null
        );
    }

    public static User localPending() { /* status = PENDING_VERIFICATION */ }
    public static User googleActive() { /* provider = GOOGLE */ }
    public static User banned() { /* status = BANNED */ }
}
```

---

## 10. Dependencies cần thêm vào `modules/iam/pom.xml`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-junit5-plugin</artifactId>
    <scope>test</scope>
</dependency>
```

> **Application properties cho test** (`src/test/resources/application-test.yml`):
> ```yaml
> spring:
>   jpa:
>     hibernate:
>       ddl-auto: validate
>   flyway:
>     enabled: true
> pwb:
>   iam:
>     seeder:
>       enabled: false
> ```

---

## 11. Quy tắc khi viết test (đọc kỹ)

1. **Test behavior, không test implementation**: Đừng verify `Mockito.verify(mock).method()` cho mọi call. Chỉ verify call quan trọng.
2. **Arrange-Act-Assert rõ ràng**: Tách 3 phần bằng blank line.
3. **Tên test mô tả kịch bản**: `should_X_when_Y` — tiếng Anh, dùng `_` thay space.
4. **Một assertion per test** (lý tưởng): Có thể có 2-3 nếu check cùng 1 behavior.
5. **Mock boundary**: Mock `repository`, `port`, `external API`. KHÔNG mock value object (`EmailAddress`, `Password`, `OtpCode`).
6. **Không assert log message** — log có thể thay đổi.
7. **Dùng AssertJ** (`assertThat`) thay JUnit assertion cho dễ đọc.
8. **Không dùng `Thread.sleep`** trong test — dùng `Awaitility` hoặc mock clock.
9. **Không catch generic Exception** trong test — để test fail rõ ràng.
10. **Mỗi test độc lập**: `@BeforeEach` reset state, không phụ thuộc test khác.
11. **Tag test theo chuẩn**: Mỗi test phải được tag với 1 chuẩn (Right-BICEP letter hoặc CORRECT letter) trong comment của test plan.
12. **Test 1 behavior = 1 test**: Nếu 1 test verify 2 behavior khác nhau → tách thành 2 test.

---

## 12. Definition of Done

Module IAM được coi là "test chuẩn Product" khi:

- [ ] Tất cả test case trong file này được implement
- [ ] `mvn test` pass 100% trên module IAM
- [ ] `mvn verify` pass (bao gồm integration test)
- [ ] Coverage domain + use case ≥ 90%
- [ ] Mutation score ≥ 70% (PIT)
- [ ] Không có `printStackTrace`, `System.out.println` trong test
- [ ] Mỗi use case có ít nhất 1 IT end-to-end
- [ ] Tất cả rate limit / cooldown được test cả 2 chiều (allow + deny)
- [ ] Tất cả error code trong `IamErrorCode` được cover ít nhất 1 test
- [ ] Recovery flow có test E2E riêng (Section 7.7, 7.8)
- [ ] Test fail rõ ràng khi business rule thay đổi (không test implementation detail)
- [ ] Mỗi test có tag chuẩn (Right-BICEP / CORRECT) trong test plan

---

## 13. Tham chiếu nhanh

| Thông tin | Vị trí |
|-----------|--------|
| Error codes | `domain/exception/IamErrorCode.java` |
| Business exceptions | `domain/exception/*.java` |
| Domain services (ports) | `domain/service/*.java` |
| Use case implementations | `application/usecase/impl/*.java` |
| Adapter implementations | `infrastructure/service/impl/*.java` |
| Controller | `api/controller/AuthController.java` |
| DTOs | `api/dto/request/*.java` |
| i18n messages | `src/main/resources/messages/*.properties` |
| Rate limit rules | `docs/RATE_LIMITING.md` |
| Test generation rules | `TEST_GENERATION_RULES.md` |
| Test methodology | First + Right-BICEP + CORRECT |
