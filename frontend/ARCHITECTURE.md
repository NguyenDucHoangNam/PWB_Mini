# 🏛️ Kiến Trúc Frontend — Bulletproof React

Tài liệu này định nghĩa cấu trúc thư mục, luồng phụ thuộc và các nguyên tắc phát triển frontend của dự án **PWB_Mini** dựa trên chuẩn **Bulletproof React**.

---

## 🗄️ Cấu trúc thư mục của dự án

Các phần mã nguồn chính nằm trong thư mục `src/`:

```sh
src/
|
+-- app               # App Router của Next.js (chứa pages, layouts, routes)
|
+-- components        # Shared Components sử dụng chung trên toàn bộ dự án
|   +-- ui            # Các component UI cơ bản (nút, input, dialog từ shadcn/ui)
|   +-- layout        # Các component layout dùng chung (Header, Footer, Sidebar)
|
+-- config            # Cấu hình toàn cục, quản lý biến môi trường
|
+-- features          # Nơi chứa logic ứng dụng phân rã theo Tính Năng (Xem chi tiết bên dưới)
|
+-- hooks             # Custom React Hooks dùng chung cho toàn bộ dự án
|
+-- lib               # Cấu hình thư viện dùng chung (api-client, react-query)
|
+-- stores            # Global State Stores (Zustand stores dùng chung)
|
+-- types             # TypeScript Types dùng chung cho toàn dự án
|
+-- utils             # Các hàm tiện ích dùng chung (format date, validation)
```

---

## 🚀 Cấu trúc của một Tính Năng (Feature Directory)

Để dự án dễ dàng mở rộng và bảo trì, phần lớn mã nguồn của luồng nghiệp vụ sẽ được gom gọn theo từng thư mục tính năng độc lập trong `src/features/`.

Một tính năng (ví dụ: `src/features/auth`, `src/features/liveroom`) sẽ được tổ chức như sau:

```sh
src/features/awesome-feature/
|
+-- api         # Khai báo các API call (fetchers) và React Query hooks
|
+-- components  # Các components UI chỉ sử dụng riêng cho tính năng này
|
+-- hooks       # Custom hooks chỉ sử dụng riêng cho tính năng này
|
+-- stores      # Zustand stores chỉ sử dụng riêng cho tính năng này
|
+-- types       # TypeScript types chỉ sử dụng trong tính năng này
|
+-- utils       # Các hàm tiện ích chỉ sử dụng trong tính năng này
```

*Lưu ý: Không bắt buộc phải có đủ tất cả các folder trên, chỉ tạo khi thực sự cần thiết.*

---

## 📡 Nguyên tắc phát triển API Layer (3 thành phần)

Mỗi khi khai báo một endpoint API trong thư mục `api/` của một feature, bắt buộc phải chia làm **3 thành phần rõ ràng** để đảm bảo khả năng bảo trì, kiểm thử và đồng bộ:

### 1. Types & Schemas
Định nghĩa kiểu dữ liệu TS cho Input (Request) và Output (Response):
```typescript
// src/features/auth/types/index.ts
export interface RegisterRequest {
  email: string;
  password: string;
}
export interface RegisterResponse {
  userId: string;
}
```

### 2. Fetcher Function (Hàm gọi API thuần)
Hàm async thuần sử dụng instance `apiClient` chung. Hàm này không được chứa hooks, để có thể sử dụng được ở Server Components hoặc kiểm thử độc lập:
```typescript
// src/features/auth/api/register.ts
import { apiClient } from "@/lib/api-client";
import type { ApiResponse } from "@/types/api";
import type { RegisterRequest, RegisterResponse } from "../types";

export const register = ({
  data,
}: {
  data: RegisterRequest;
}): Promise<ApiResponse<RegisterResponse>> => {
  return apiClient.post("/auth/register", data).then((res) => res.data);
};
```

### 3. React Query Hook (Bọc hook)
Bọc fetcher function bằng `@tanstack/react-query` (`useMutation` hoặc `useQuery`) để tích hợp caching, invalidation và quản lý loading state:
```typescript
// src/features/auth/api/register.ts (tiếp tục)
import { useMutation } from "@tanstack/react-query";
import type { MutationConfig } from "@/lib/react-query";

type UseRegisterOptions = {
  mutationConfig?: MutationConfig<typeof register>;
};

export const useRegister = ({ mutationConfig }: UseRegisterOptions = {}) => {
  return useMutation({
    ...mutationConfig,
    mutationFn: register,
  });
};
```

---

## 🔒 Quy tắc Nhất quán và Ràng buộc Phụ thuộc (Unidirectional dependencies)

Để giữ cho dự án không bị liên kết chéo chồng chéo và dễ vỡ khi thay đổi mã nguồn, chúng ta phải tuân thủ nghiêm ngặt hai quy tắc:

### 1. Dòng phụ thuộc một chiều (Unidirectional Dependency Flow)
Mã nguồn chỉ được phụ thuộc theo hướng đi từ lớp cơ sở dùng chung lên lớp ứng dụng:
- **Shared Code** (`src/components`, `src/hooks`, `src/lib`, `src/types`) **KHÔNG ĐƯỢC PHÉP** import từ `src/features/*` hay `src/app/*`.
- **Features** (`src/features/*`) chỉ được import từ **Shared Code**, **KHÔNG ĐƯỢC PHÉP** import từ các page/route của `src/app/*`.
- **App Layer** (`src/app/*`) có thể import từ cả **Features** và **Shared Code**.

### 2. Cấm import chéo giữa các Features (No Cross-Feature Imports)
- Mỗi tính năng trong `src/features/*` là độc lập và tự chứa.
- Ví dụ: `src/features/liveroom` **không được phép** import component trực tiếp từ `src/features/auth`.
- Nếu có component hoặc logic cần chia sẻ giữa nhiều features, bắt buộc phải di chuyển nó ra thư mục **Shared Code** dùng chung (ví dụ: `src/components/ui/`, `src/components/layout/`, hoặc `src/hooks/`).

---

## ⚡ Quy tắc Ngăn Chặn Gửi Yêu Cầu Trùng Lặp (Double Submit Prevention)

Để đảm bảo hiệu năng và tránh tạo tài nguyên trùng lặp ở Backend, mọi component kích hoạt hành vi chỉnh sửa dữ liệu (POST, PUT, PATCH, DELETE) bắt buộc phải:
1. **Immediately disable** nút bấm/vùng tương tác ngay khi click (`disabled = true`).
2. **Hiển thị Spinner / Trạng thái loading** để người dùng nhận thức được hệ thống đang xử lý.
3. Khi sử dụng React Query hook (như `useRegister` đã khai báo ở trên), thuộc tính `isPending` trả về từ hook sẽ tự động quản lý logic này:

```tsx
import { useRegister } from "../api/register";

export function RegisterButton() {
  const { mutate, isPending } = useRegister();

  const handleRegister = () => {
    mutate({ data: { email: "test@pwb.com", password: "secure" } });
  };

  return (
    <button onClick={handleRegister} disabled={isPending}>
      {isPending ? <Spinner className="mr-2" /> : null}
      Đăng ký
    </button>
  );
}
```
