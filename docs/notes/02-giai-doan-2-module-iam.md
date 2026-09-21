# Giai đoạn 2: Lát Cắt Dọc Chuẩn — Module IAM (Xác Thực & User)

> 🧭 **Điều hướng**: [⬅️ Quay lại Mục Lục Tổng Quan](../notes.md) | [⬅️ Giai đoạn 1: Nền Tảng Dùng Chung](01-giai-doan-1-shared-foundation.md) | [Giai đoạn 3: Module Audio ➡️](03-giai-doan-3-module-audio.md)

---

- **Domain**:
  - `User.java` (`com.pwb.iam.domain.model`): Rich Domain Model (chứa nghiệp vụ `createLocal`, `createGoogle`, `markEmailVerified`).
  - `UserRepository.java` (`com.pwb.iam.domain.repository`): Port interface định nghĩa thao tác với user.
- **Application**:
  - `LoginUseCaseImpl.java` (`com.pwb.iam.application.usecase.impl`): Luồng đăng nhập kiểm tra brute-force, password hashing, cấp cặp token và publish event.
  - `RegisterUseCaseImpl.java` & `VerifyOtpUseCaseImpl.java` (`com.pwb.iam.application.usecase.impl`): Tạo user chưa kích hoạt và kích hoạt tài khoản qua OTP.
- **Infrastructure**:
  - `UserRepositoryImpl.java` (`com.pwb.iam.infrastructure.persistence.adapter`): Adapter hiện thực port bằng Spring Data JPA.
  - `TokenManagerServiceImpl.java` (`com.pwb.iam.infrastructure.service`): Quản lý JWT và cơ chế Refresh Token Rotation trong Redis (`iam:refresh:token:*`).
- **API & Frontend**:
  - `AuthController.java` (`com.pwb.iam.api.controller`): 10 REST endpoint xác thực.
  - `login.ts` (`Frontend/src/features/auth/api`): 3 thành phần chuẩn (Type → Fetcher → TanStack Query Hook).
  - `use-auth-store.ts` (`Frontend/src/features/auth/stores`) & `login-form.tsx` (`Frontend/src/features/auth/components`): Zustand store lưu user/token và UI form đăng nhập.

---

---

> 🧭 **Điều hướng**: [⬅️ Quay lại Mục Lục Tổng Quan](../notes.md) | [⬅️ Giai đoạn 1: Nền Tảng Dùng Chung](01-giai-doan-1-shared-foundation.md) | [Giai đoạn 3: Module Audio ➡️](03-giai-doan-3-module-audio.md)
