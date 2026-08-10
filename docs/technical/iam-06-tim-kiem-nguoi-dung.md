# IAM — Tìm kiếm người dùng

> `GET /admin/users/search` · `GET /admin/users/suggest`
> Bối cảnh: [IAM — Tour](iam-00-tour.md)
> Cơ chế mô tả ở đây **dùng chung** cho tìm kiếm bài hát, voice tag và phòng live.

---

## 1. Bài toán

Tìm người dùng theo email hoặc tên, kết hợp với vài bộ lọc: trạng thái tài khoản, vai trò, nhà cung cấp OAuth. Trang quản trị cần cả một danh sách phân trang lẫn một ô gợi ý gõ tới đâu hiện tới đó.

Hệ thống từng giải bài này bằng Elasticsearch — một bản sao dữ liệu riêng, đồng bộ qua outbox và Kafka, có xếp hạng theo độ liên quan, khớp gần đúng và analyzer bỏ dấu tiếng Việt. **Toàn bộ phần đó đã bị gỡ ngày 2026-08-10.** Nếu bạn đọc một tài liệu hay một comment nào còn nhắc tới nó, tài liệu đó cũ hơn code.

Cái còn lại đơn giản hơn nhiều: một truy vấn JPA Specification chạy thẳng trên Postgres.

---

## 2. Một truy vấn, dựng từ Specification

`UserSpecifications.fromCriteria` ghép từng điều kiện có mặt trong `UserSearchCriteria`:

```java
public static Specification<UserJpaEntity> fromCriteria(UserSearchCriteria criteria) {
    Specification<UserJpaEntity> spec = notDeleted();

    if (criteria.status() != null)   spec = spec.and(hasStatus(criteria.status()));
    if (criteria.role() != null)     spec = spec.and(hasRole(criteria.role().name()));
    if (criteria.provider() != null) spec = spec.and(hasProvider(criteria.provider()));
    if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
        spec = spec.and(keywordMatch(criteria.keyword()));
    }
    return spec;
}
```

Lý do dùng Specification thay vì một loạt method dẫn xuất: bốn tiêu chí đều tuỳ chọn, tức là **16 tổ hợp**. Specification phủ hết bằng một chỗ ghép; `findByStatusAndRole…` thì cần 16 method.

`notDeleted()` luôn là điều kiện đầu tiên, không có nhánh nào bỏ qua được — tài khoản đã xoá mềm không bao giờ lọt ra trang quản trị.

Phần khớp từ khoá:

```java
public static Specification<UserJpaEntity> keywordMatch(String keyword) {
    return (root, query, cb) -> {
        String pattern = "%" + keyword.toLowerCase() + "%";
        return cb.or(
                cb.like(cb.lower(root.get("email")), pattern),
                cb.like(cb.lower(root.get("fullName")), pattern)
        );
    };
}
```

Đây là toàn bộ những gì "tìm kiếm" nghĩa là bây giờ, và cần nói thẳng nó **không** làm được gì:

| Không có | Hệ quả cụ thể |
|---|---|
| Bỏ dấu tiếng Việt | Gõ `nguyen` **không** ra `Nguyễn Văn A` |
| Khớp gần đúng | Gõ sai một chữ là không ra kết quả nào |
| Xếp hạng theo độ liên quan | Thứ tự do database quyết định, không phải độ khớp |
| Dùng được index | `LIKE '%…%'` mở đầu bằng `%` nên Postgres phải quét toàn bảng |

Số điện thoại **không** nằm trong phạm vi tìm — chỉ email và họ tên.

---

## 3. Use case: không còn nhánh nào

```java
@Override
public Page<AdminUserView> search(UUID adminId, UserSearchCriteria criteria, Pageable pageable) {
    return userJpaRepository
            .findAll(UserSpecifications.fromCriteria(criteria), pageable)
            .map(this::toView);
}
```

Trước đây chỗ này có hai nhánh: hỏi engine trước, `Optional.empty()` thì mới ngã về Postgres. Cả kiểu `Optional`, cả log `SEARCH.users fallback to database`, cả bước lấy id rồi hydrate lại từ Postgres — đều đã biến mất cùng engine. Còn đúng một đường đi.

Điều này cũng khiến một chi tiết cũ hết ý nghĩa: `total` không còn lệch với số dòng hiển thị. Trước kia tổng số là con số của engine còn danh sách đã bị lọc bớt những id không tìm thấy hàng trong database, nên một trang có thể ghi "20 kết quả" mà chỉ liệt kê 19 dòng. Giờ cả hai đều từ cùng một truy vấn.

---

## 4. Gợi ý gõ dở

```java
@Override
public List<AdminUserSuggestionView> suggest(UUID adminId, UserSearchCriteria criteria, int limit) {
    if (criteria.keyword() == null || criteria.keyword().isBlank()) {
        return List.of();
    }
    int capped = Math.clamp(limit, 1, MAX_SUGGESTIONS);   // MAX_SUGGESTIONS = 20

    return userJpaRepository
            .findAll(UserSpecifications.fromCriteria(criteria), PageRequest.of(0, capped))
            .getContent().stream()
            .map(entity -> new AdminUserSuggestionView(
                    entity.getId(), entity.getEmail(), entity.getFullName()))
            .toList();
}
```

Hai điểm đáng chú ý, cả hai đều là chặn tự bảo vệ:

**Từ khoá rỗng trả rỗng ngay**, không chạm database. Không có dòng này thì mỗi lần người dùng xoá trắng ô nhập, ô gợi ý sẽ nã một truy vấn "lấy tất cả".

**`Math.clamp(limit, 1, 20)`** chặn client tự đặt `limit=10000`. Controller cũng đã có `@Max(20)`, nên đây là lớp thứ hai — nó bảo vệ cả những người gọi use case không qua HTTP.

`suggest` và `search` giờ dùng **chung một `Specification`**, nên không còn nguy cơ hai đường lọc lệch nhau như thời còn hai cách thực thi song song.

---

## 5. Hai endpoint gần như trùng nhau

Sau khi gỡ engine, `GET /admin/users` và `GET /admin/users/search` chạy **cùng một truy vấn** — cùng `UserSpecifications.fromCriteria`, cùng `userJpaRepository.findAll`. Khác nhau đúng hai chỗ:

| | `GET /admin/users` | `GET /admin/users/search` |
|---|---|---|
| Tên tham số từ khoá | `keyword` | `q` |
| Sắp xếp mặc định | `createdAt` giảm dần | không đặt — theo thứ tự database trả |

Thời còn Elasticsearch sự tách bạch này có lý do rõ ràng: một bên đọc database và sắp theo ngày tạo, bên kia đọc engine và sắp theo độ liên quan. Lý do đó đã mất. Hai route được giữ nguyên vì giao diện quản trị đang gọi cả hai, nhưng **đây là chỗ đáng gộp** khi có dịp sửa cả frontend.

---

## 6. Quyết định & đánh đổi

| Quyết định | Thay vì | Vì sao | Cái giá |
|---|---|---|---|
| Gỡ hẳn Elasticsearch | Giữ và sửa | Bớt một dịch vụ ăn ~1.3GB RAM trên VPS 7.6GB, bớt một bản sao dữ liệu phải giữ đồng bộ | Mất bỏ dấu, khớp gần đúng, xếp hạng |
| `LIKE '%…%'` trên Postgres | `unaccent` + `pg_trgm` | Không cần `CREATE EXTENSION`, không cần migration, không cần index mới | Không bỏ dấu được, và luôn quét toàn bảng |
| Dùng Specification | Method dẫn xuất cho từng tổ hợp | Bốn tiêu chí tuỳ chọn là 16 tổ hợp | Truy vấn khó đọc hơn một `@Query` viết tay |
| `notDeleted()` không thể bỏ qua | Để người gọi tự thêm | Một chỗ quên là lộ tài khoản đã xoá | — |
| Từ khoá rỗng → trả rỗng ngay | Trả tất cả | Ô gợi ý không nã truy vấn khi bị xoá trắng | — |
| Chặn `limit` ở cả controller lẫn use case | Chỉ chặn ở controller | Người gọi không qua HTTP cũng được bảo vệ | Cùng một hằng số nằm hai nơi |

---

## 7. Tự kiểm chứng

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"admin1@gmail.com","password":"@NamHoang511"}' | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
```

**Tìm kiếm và gợi ý:**

```bash
curl -s "http://localhost:8080/api/v1/admin/users/search?q=demo" -H "Authorization: Bearer $TOKEN"
```

```bash
curl -s "http://localhost:8080/api/v1/admin/users/suggest?q=us&limit=5" -H "Authorization: Bearer $TOKEN"
```

**Thấy giới hạn của việc không bỏ dấu** — tạo một người dùng tên có dấu rồi tìm bằng chuỗi không dấu:

```bash
curl -s "http://localhost:8080/api/v1/admin/users/search?q=nguyen" -H "Authorization: Bearer $TOKEN"
```

Một tài khoản tên `Nguyễn Văn A` **sẽ không xuất hiện**. Đây là hành vi đúng như thiết kế hiện tại, không phải lỗi.

**Thấy hai endpoint trả cùng kết quả** — chỉ khác thứ tự:

```bash
curl -s "http://localhost:8080/api/v1/admin/users?keyword=demo" -H "Authorization: Bearer $TOKEN"
```

**Thấy truy vấn thật Hibernate sinh ra** — bật log rồi gọi lại `search`:

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "EXPLAIN ANALYZE SELECT * FROM iam_users WHERE deleted = false AND (lower(email) LIKE '%demo%' OR lower(full_name) LIKE '%demo%');"
```

Kế hoạch sẽ là `Seq Scan` — bằng chứng cho dòng "luôn quét toàn bảng" ở mục 2.

---

## 8. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| Không bỏ dấu tiếng Việt | `nguyen` không ra `Nguyễn` — mục 2 |
| Không chịu được gõ sai | Sai một ký tự là mất kết quả — mục 2 |
| Không xếp hạng | Thứ tự do database quyết định, không theo độ khớp |
| Luôn quét toàn bảng | `LIKE '%…%'` không dùng được B-tree index; chưa đau vì bảng còn nhỏ |
| Hai endpoint gần trùng nhau | `GET /admin/users` và `/admin/users/search` chạy cùng truy vấn — mục 5 |
| Không tìm được theo số điện thoại | Chỉ email và họ tên nằm trong phạm vi |
| Không có `search`/`suggest` cho người dùng thường | Chỉ nhánh admin có |
