# IAM — Quản trị người dùng

> `/api/v1/admin/users` — danh sách · chi tiết · đổi vai trò · cấm · bỏ cấm · xoá · thống kê
> Bối cảnh: [IAM — Tour](iam-00-tour.md) · Tìm kiếm tách riêng: [iam-06](iam-06-tim-kiem-nguoi-dung.md)

---

## 1. Bài toán

Quản trị viên cần công cụ xử lý người dùng vi phạm. Nhưng công cụ đó, nếu làm ẩu, chính là công cụ để **một admin phá cả hệ thống** — vô tình hoặc cố ý.

Ba tình huống phải chặn bằng thiết kế, không bằng nhắc nhở:

1. Admin tự cấm chính mình → mất quyền, không ai gỡ được
2. Admin cấm admin khác → chiến tranh nội bộ, hoặc kẻ chiếm được một tài khoản admin sẽ vô hiệu hoá toàn bộ đội quản trị
3. Admin tự phong ai đó lên admin → leo thang đặc quyền

Cả ba được chặn ở **đúng một chỗ**.

---

## 2. `AdminUserGuard` — một cổng cho mọi thao tác

```java
public User loadAndValidate(UUID adminId, UUID targetUserId) {
    if (adminId.equals(targetUserId)) {
        throw new BusinessException(IamErrorCode.ADMIN_CANNOT_MODIFY_SELF);
    }
    User target = userRepository.findById(targetUserId)
            .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));
    if (target.getRole() == RoleName.ADMIN) {
        throw new BusinessException(IamErrorCode.ADMIN_CANNOT_MODIFY_ADMIN);
    }
    return target;
}
```

**Bốn use case đều bắt đầu bằng đúng dòng này**: `ban`, `unban`, `changeRole`, `delete`. Không use case nào tự tải người dùng lên.

Giá trị của việc gom vào một chỗ: thêm một thao tác quản trị mới thì lập trình viên **buộc phải** gọi guard để lấy được đối tượng — quên gọi nghĩa là không có gì để làm việc. Quy tắc bảo vệ tự đi theo, không cần ai nhớ.

Hai quy tắc trong đó có ý nghĩa khác nhau:

- **Không tự sửa mình** chặn tai nạn. Không ai cố tình tự cấm mình.
- **Không sửa admin khác** chặn tấn công. Nó khiến vai trò ADMIN trở thành trạng thái **không thể gỡ qua API** — muốn hạ một admin phải sửa thẳng trong database.

Quy tắc thứ ba nằm riêng ở `AdminChangeRoleUseCaseImpl`, **trước cả guard**:

```java
if (command.newRole() == RoleName.ADMIN) {
    throw new BusinessException(IamErrorCode.ADMIN_INVALID_ROLE_ASSIGNMENT);
}
```

Kết hợp hai quy tắc lại: **API này chỉ đi được một chiều.** Có thể hạ hoặc nâng giữa `USER` và `PRO`, không bao giờ chạm được vào `ADMIN` — không tạo ra được, không gỡ đi được. Admin duy nhất là admin đã seed sẵn hoặc do người có quyền vào database tạo ra.

---

## 3. Bốn thao tác

| Thao tác | Đổi gì | Thu hồi phiên | Có thể hoàn tác |
|---|---|---|---|
| `POST /{id}/ban` | `status → BANNED`, ghi lý do + ai cấm | ✅ | ✅ bằng `unban` |
| `POST /{id}/unban` | `status → ACTIVE` | ❌ | — |
| `PATCH /{id}/role` | `role → USER \| PRO` | ❌ | ✅ đổi lại |
| `DELETE /{id}` | `status → PENDING_DELETION`, `deleted = true` | ✅ | ❌ **không có API** |

### 3.1. Thu hồi phiên: hai chỗ có, hai chỗ không

`ban` và `delete` gọi `revokeAllRefreshTokensForUser`. Đúng — cấm ai đó mà họ vẫn dùng tiếp thì việc cấm vô nghĩa.

`unban` không cần (họ đã bị đá ra rồi). Nhưng **`changeRole` cũng không gọi**, và đó là một lỗ hổng thật:

> Vai trò nằm trong **claim `role` của JWT** ([02 §5.1](02-lat-cat-doc-dang-nhap.md)). Hạ một người từ `PRO` xuống `USER` **không** làm access token hiện tại của họ mất quyền PRO — token đã ký rồi, và filter đọc quyền thẳng từ claim. Họ giữ quyền PRO thêm tối đa 15 phút, và nếu họ gọi `refresh` trong khoảng đó thì token mới **được cấp lại từ `User` đã đọc lại từ database**, nên lúc ấy mới đúng.

Gọi `revokeAllRefreshTokensForUser` ở đây sẽ rút ngắn cửa sổ đó xuống bằng cách bắt họ đăng nhập lại. Vẫn không đóng hẳn được 15 phút của access token — cùng giới hạn nêu ở [iam-02 §4.2](iam-02-mat-khau.md).

Điều này khớp với một ghi chú trong yêu cầu của Live Room: *PRO bị hạ vai trò giữa phiên → buộc kết thúc phòng*, nhưng **không có nguồn kích hoạt** vì IAM không phát sự kiện đổi vai trò. `AdminChangeRoleUseCaseImpl` không gọi `authEventPublisher`, và ngay cả nếu có thì implementation duy nhất cũng chỉ ghi log ([02 §10](02-lat-cat-doc-dang-nhap.md)).

### 3.2. Xoá là xoá mềm, và là đường một chiều

`markPendingDeletion()` đặt `status = PENDING_DELETION` và `deleted = true`. Dữ liệu vẫn nguyên trong bảng.

Nhưng **không có endpoint khôi phục**, và mọi truy vấn đều lọc `deletedFalse` (thấy rõ ở `findByIdAndDeletedFalse`, `findByIdInAndDeletedFalse`). Nên xoá mềm ở đây cho lợi ích của việc *giữ dữ liệu* — toàn vẹn tham chiếu tới bài hát, phòng, tin nhắn — chứ không cho lợi ích *hoàn tác được*.

Cũng **không có công việc xoá cứng** sau một thời gian. Trạng thái tên là `PENDING_DELETION` nhưng không có gì đang chờ để hoàn tất nó; nó là trạng thái cuối trên thực tế.

---

## 4. Thống kê

`GET /admin/users/stats` trả 11 con số bằng **11 truy vấn `COUNT` riêng biệt**:

```
tổng · ACTIVE · BANNED · PENDING_DELETION · PENDING_VERIFICATION
USER · PRO · ADMIN
mới hôm nay · mới tuần này · mới tháng này
```

Mốc thời gian tính theo **UTC**, và tuần bắt đầu từ thứ Hai (`ChronoField.DAY_OF_WEEK, 1`).

Hai điều đáng biết:

**11 lần quét bảng cho một lần bấm.** Với 7 người dùng thì không ai nhận ra. Với vài trăm nghìn thì đây là endpoint chậm nhất của module, và có thể gộp thành một câu `GROUP BY` duy nhất.

**"Hôm nay" là hôm nay theo UTC.** Người dùng Việt Nam xem lúc 6 giờ sáng sẽ thấy con số của ngày hôm trước, vì UTC lúc đó vẫn chưa sang ngày mới. Không có cấu hình múi giờ nào cho việc này.

---

## 5. Đọc thẳng JPA entity từ tầng application

`AdminUserGuard` giữ **hai** thứ để truy cập dữ liệu:

```java
private final UserRepository userRepository;      // port domain
private final UserJpaRepository userJpaRepository; // JPA thô

public AdminUserView toAdminView(User user) {
    UserJpaEntity entity = userJpaRepository.findByIdAndDeletedFalse(user.getUserId())...;
    return AdminUserView.from(user, entity.getCreatedAt(), entity.getUpdatedAt());
}
```

Đây là chỗ phá quy tắc phụ thuộc đã nêu ở [01 §3.1](01-architecture-overview.md): `application` không được biết tới `infrastructure`. Nó xuất hiện ở **ba** nơi trong nhánh admin — `AdminUserGuard`, `AdminListUsersUseCaseImpl`, `AdminSearchUsersUseCaseImpl`.

Nguyên nhân gốc: `User` (domain) không mang `createdAt`/`updatedAt` vì `UserMapper` của IAM **không gọi `restoreAuditTimestamps`**, khác với 10 mapper của Audio và Live Room. Tầng application cần dấu thời gian để hiển thị, không lấy được từ domain model, nên với tay xuống entity.

Cách chữa đúng khuôn là để `UserMapper.toDomain` khôi phục dấu thời gian như hai module kia — [01 §9](01-architecture-overview.md).

Thêm một hệ quả: `toAdminView` **tải lại người dùng từ database lần thứ hai** chỉ để lấy hai cột. Mỗi thao tác quản trị chạy một truy vấn thừa.

---

## 6. Phân quyền

Endpoint dưới `/api/v1/admin/**` không nằm trong `public-endpoints`, nên `AuthorizationFilter` chặn khách vãng lai. Việc "phải là ADMIN" thì dựa vào quyền `ROLE_ADMIN` mà `JwtAuthenticationFilter` dựng từ claim `role`.

Vì quyền đọc từ **claim trong token** chứ không đọc database mỗi request, mọi thay đổi vai trò đều có độ trễ tối đa 15 phút — đúng vấn đề ở mục 3.1. Đó là cái giá cố hữu của việc chọn JWT tự chứa ([02 §11](02-lat-cat-doc-dang-nhap.md)).

---

## 7. Quyết định & đánh đổi

| Quyết định | Thay vì | Vì sao | Cái giá |
|---|---|---|---|
| Một guard cho cả bốn thao tác | Kiểm ở từng use case | Thao tác mới buộc phải đi qua guard | Guard biết cả về JPA entity (mục 5) |
| Không sửa được admin khác | Cho admin sửa nhau | Chiếm một tài khoản admin không hạ được cả đội | Hạ một admin phải vào database |
| Không phong được ADMIN qua API | Cho phong | Không leo thang đặc quyền | Thêm admin phải qua seeder hoặc database |
| Xoá mềm | Xoá cứng | Bài hát, phòng, tin nhắn của họ không thành mồ côi | Không hoàn tác được, cũng không dọn được |
| `ban` thu hồi phiên, `changeRole` không | Đối xử như nhau | — | **Lỗ hổng**: hạ vai trò không có hiệu lực ngay (mục 3.1) |
| 11 `COUNT` riêng | Một `GROUP BY` | Code dễ đọc, mỗi dòng một chỉ số | 11 lần quét bảng mỗi lần bấm |
| Mốc ngày theo UTC | Theo múi giờ người xem | Không cần cấu hình | "Hôm nay" lệch với người Việt Nam suốt 7 tiếng đầu ngày |

---

## 8. Tự kiểm chứng

Lấy token admin:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"admin1@gmail.com","password":"@NamHoang511"}' | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
```

**Xem thống kê:**

```bash
curl -s http://localhost:8080/api/v1/admin/users/stats -H "Authorization: Bearer $TOKEN"
```

**Thử tự cấm mình** — lấy `userId` của chính admin từ response đăng nhập rồi gọi `ban`. Nhận `ADMIN_CANNOT_MODIFY_SELF`.

**Thử cấm admin khác** — dùng `userId` của `admin2@gmail.com`. Nhận `ADMIN_CANNOT_MODIFY_ADMIN`.

**Thử phong ADMIN:**

```bash
curl -s -X PATCH "http://localhost:8080/api/v1/admin/users/<userId>/role" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"role":"ADMIN"}'
```

Nhận `ADMIN_INVALID_ROLE_ASSIGNMENT`.

**Thấy lỗ hổng đổi vai trò tận mắt:** đăng nhập bằng `pro1@gmail.com` và giữ access token. Dùng admin hạ tài khoản đó xuống `USER`. Giải mã phần payload của token cũ — claim `role` vẫn là `PRO`, và token vẫn dùng được tới khi hết 15 phút.

**Xem xoá mềm không xoá dữ liệu:**

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT email, status, deleted FROM iam_users ORDER BY created_at DESC LIMIT 10;"
```

---

## 9. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| Đổi vai trò không có hiệu lực ngay | Không thu hồi phiên, và claim `role` nằm trong token — mục 3.1 |
| Không có sự kiện đổi vai trò | Live Room cần nó để buộc kết thúc phòng khi PRO bị hạ, nhưng không có nguồn phát |
| `PENDING_DELETION` là trạng thái cuối | Không có API khôi phục, không có công việc xoá cứng — mục 3.2 |
| Không hạ được admin qua API | Cố ý, nhưng nghĩa là phải vào database |
| Thống kê chạy 11 `COUNT` | Mục 4 |
| "Hôm nay" theo UTC | Lệch 7 tiếng với người dùng Việt Nam — mục 4 |
| Tầng application đọc JPA entity | Ba chỗ trong nhánh admin, cộng một truy vấn thừa mỗi thao tác — mục 5 |
| Không ghi nhật ký thao tác quản trị | `ban` lưu được lý do và người thực hiện trên chính bản ghi user, nhưng đổi vai trò và xoá thì không để lại dấu vết nào ngoài dòng log. Bảng `iam_audit_logs` đã bị gỡ ở `V10` |
