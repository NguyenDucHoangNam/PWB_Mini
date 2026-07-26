# 08 - Self-Review Checklist

**Mục đích**: Checklist tự kiểm tra trước khi commit / merge / nghiệm thu.

**Khi nào dùng**: Sau khi code xong 1 task, TRƯỚC khi commit.

---

## 📋 Cách dùng

1. Mở file này
2. Đi qua từng mục theo thứ tự
3. Tick `[x]` nếu pass, `[ ]` nếu chưa
4. Nếu có mục nào fail → sửa trước khi commit
5. Nếu tất cả pass → safe to commit

---

## 1. Code Style & Conventions (8 rules)

### 1.1 Lombok usage (Backend)
- [ ] Service/Component dùng `@Slf4j` + `@RequiredArgsConstructor`
- [ ] DTO/Request/Response dùng `@Data` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor`
- [ ] JPA Entity dùng `@Getter` + `@Setter` + `@NoArgsConstructor` + `@Builder` (KHÔNG `@Data`)
- [ ] KHÔNG viết getter/setter/constructor/toString thủ công
- [ ] KHÔNG dùng `@Autowired` field injection

### 1.2 No code comments (Backend)
- [ ] KHÔNG có `//` hoặc `/* */` comment nào trong source code
- [ ] KHÔNG có `TODO`, `FIXME`, `HACK`, `XXX` markers
- [ ] KHÔNG có section divider comments
- [ ] Javadoc chỉ dùng cho public APIs và có giá trị thật

### 1.3 No code comments (Frontend)
- [ ] KHÔNG có `//` hoặc `/* */` comment nào trong source code
- [ ] KHÔNG có `TODO`, `FIXME`, `HACK`, `XXX` markers
- [ ] `'use client'` / `'use server'` directives OK (không phải comment)
- [ ] JSDoc chỉ dùng cho public APIs

### 1.4 Naming convention
- [ ] Class: `PascalCase` (LiveRoom, JoinRequestService)
- [ ] Method: `camelCase` (createRoom, approveJoinRequest)
- [ ] Variable: `camelCase` (currentUserId, roomCode)
- [ ] Constant: `UPPER_SNAKE_CASE` (MAX_RETRY_ATTEMPTS)
- [ ] Boolean: prefix `is/has/can/should` (isActive, hasPermission)

### 1.5 Response format nhất quán
- [ ] Tất cả REST response dùng `ApiResponse<T>` wrapper
- [ ] HTTP status code đúng (200/201/204/400/401/403/404/409/500)
- [ ] Error response có `code + message`

### 1.6 Method ngắn gọn
- [ ] Method < 50 dòng (lý tưởng < 20 dòng)
- [ ] Early return / guard clause pattern
- [ ] Một method làm một việc

### 1.7 Imports rõ ràng
- [ ] KHÔNG có wildcard `import java.util.*;`
- [ ] Đã remove unused imports

### 1.8 No dead code
- [ ] KHÔNG có commented-out code
- [ ] KHÔNG có unused variables / methods / classes

---

## 2. SOLID Principles

### 2.1 Single Responsibility
- [ ] Mỗi class có một lý do để thay đổi
- [ ] Service không làm quá nhiều việc (tách EmailService, ReportService)

### 2.2 Open/Closed
- [ ] Dùng interface/strategy thay vì switch-case khổng lồ
- [ ] Mở rộng qua composition, không sửa code cũ

### 2.3 Liskov Substitution
- [ ] Override methods giữ nguyên contract

### 2.4 Interface Segregation
- [ ] Interface nhỏ, chuyên biệt
- [ ] KHÔNG ép implement methods không dùng

### 2.5 Dependency Inversion
- [ ] Phụ thuộc interface (JpaRepository, LiveRoomService), không phụ thuộc concrete

---

## 3. Error Handling

### 3.1 Custom Exception + ErrorCode
- [ ] Throw `BusinessException` với ErrorCode enum, KHÔNG throw `RuntimeException` với string
- [ ] ErrorCode có HttpStatus + code + defaultMessage
- [ ] KHÔNG nuốt exception (`catch (Exception e) {}`)
- [ ] KHÔNG log rồi throw lại (gây duplicate log)

### 3.2 GlobalExceptionHandler
- [ ] Có GlobalExceptionHandler log error + trả về response chuẩn
- [ ] Validation error → 400 với list of field errors
- [ ] BusinessException → status code tương ứng

### 3.3 Try-with-resources
- [ ] Close Statement/Connection/Stream đúng cách

---

## 4. i18n / Message Externalization

### 4.1 Không hardcode message
- [ ] KHÔNG có string literal trả về user trong Controller/Service
- [ ] Message resolve qua `MessageSource.getMessage(key, args, fallback, locale)`
- [ ] Locale lấy từ `LocaleContextHolder.getLocale()`

### 4.2 Key là constant
- [ ] Key là `static final String` constant (e.g. `MSG_ROOM_CREATED`)
- [ ] KHÔNG truyền string literal lung tung

### 4.3 Validation annotation dùng i18n key
- [ ] `@NotBlank(message = "{validation.field.required}")` (KHÔNG hardcode Vietnamese)
- [ ] Fallback là key i18n

### 4.4 i18n files đầy đủ
- [ ] Key mới đã thêm vào `messages.properties` (fallback)
- [ ] Key mới đã thêm vào `messages_en.properties`
- [ ] Key mới đã thêm vào `messages_vi.properties`
- [ ] Frontend: `en.json` + `vi.json` có key tương ứng

### 4.5 Log message
- [ ] Log message dùng tiếng Anh, có context (userId, roomId...)
- [ ] KHÔNG log sensitive data (password, token)

---

## 5. Database & JPA

### 5.1 Entity đúng convention
- [ ] `@Entity @Table(name = "...")`
- [ ] `@Id @GeneratedValue(strategy = GenerationType.UUID)`
- [ ] `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` (KHÔNG `@Data`)
- [ ] `@PrePersist` cho audit columns (createdAt, updatedAt)
- [ ] `@PreUpdate` cho updatedAt

### 5.2 Locking strategy (C2)
- [ ] **LiveRoom KHÔNG có @Version** (Phase 0)
- [ ] **PlaybackState CÓ @Version** (Phase 4)
- [ ] Update LiveRoom qua `entityManager.find(..., LockModeType.PESSIMISTIC_WRITE)`
- [ ] Update PlaybackState dùng optimistic lock + retry

### 5.3 Indexes
- [ ] Cột thường query có index
- [ ] Foreign key có index
- [ ] Composite index cho query phức tạp

### 5.4 Constraints
- [ ] NOT NULL đúng cho field bắt buộc
- [ ] UNIQUE cho field unique (roomCode, ...)
- [ ] FK constraint đúng

### 5.5 N+1 query
- [ ] Dùng `@EntityGraph` hoặc `JOIN FETCH` cho quan hệ
- [ ] KHÔNG gọi `getParticipants()` trong loop

---

## 6. Concurrency

### 6.1 Pessimistic lock
- [ ] Capacity-sensitive operations dùng `PESSIMISTIC_WRITE`
- [ ] Lock acquire → check → update → release trong transaction
- [ ] Không giữ lock lâu (transaction ngắn)

### 6.2 Optimistic lock
- [ ] Music control dùng `@Version` + `OptimisticLockingFailureException`
- [ ] Client nhận 409 → re-fetch state

### 6.3 UPSERT
- [ ] RejectCounter increment dùng native UPSERT
- [ ] Idempotency key insert dùng UPSERT

### 6.4 Debounce
- [ ] Owner leave có 3s debounce
- [ ] End room có 5s undo window

### 6.5 ShedLock
- [ ] Background jobs có `@SchedulerLock`
- [ ] Lock chỉ 1 instance chạy

---

## 7. Security

### 7.1 Input validation
- [ ] `@Valid @RequestBody` cho request
- [ ] `@NotBlank`, `@Size`, `@Min`, `@Max` cho fields
- [ ] KHÔNG trust client input

### 7.2 Authentication
- [ ] JWT filter setup
- [ ] Protected endpoint yêu cầu JWT
- [ ] Public endpoint (health, by-code) permitAll

### 7.3 Authorization
- [ ] `@PreAuthorize` cho role check
- [ ] Owner check trong service
- [ ] Participant state check (ACTIVE only)

### 7.4 SQL injection
- [ ] Dùng `@Query` với named parameter (`:roomId`)
- [ ] KHÔNG string concat SQL

### 7.5 XSS prevention
- [ ] HTML escape trong chat/annotation
- [ ] KHÔNG render raw HTML

### 7.6 CORS
- [ ] CORS config cho frontend domain
- [ ] KHÔNG `allowedOrigins("*")` trong production

### 7.7 Sensitive data
- [ ] KHÔNG log password, token, PII
- [ ] Audit log không chứa sensitive data

---

## 8. Performance

### 8.1 Pagination
- [ ] List API dùng `Pageable`
- [ ] KHÔNG `findAll()` không giới hạn

### 8.2 Caching
- [ ] Cache read-heavy data (user permission, ...)
- [ ] Cache TTL hợp lý
- [ ] KHÔNG cache mutable data

### 8.3 Stream
- [ ] Stream cho transform/filter phức tạp
- [ ] Tránh parallelStream() nếu data nhỏ

### 8.4 Eager vs Lazy
- [ ] `@OneToMany` mặc định LAZY
- [ ] Dùng `JOIN FETCH` khi cần eager

---

## 9. Backend Specific

### 9.1 Logging
- [ ] Dùng `@Slf4j` + `log.info("roomId={}, userId={}", ...)`
- [ ] KHÔNG `log.info("data: " + obj)` (eager concat)
- [ ] Log level đúng (INFO business, WARN warning, ERROR error)

### 9.2 Transaction
- [ ] `@Transactional` cho write operations
- [ ] Read-only method `@Transactional(readOnly = true)`
- [ ] KHÔNG gọi self `@Transactional` method (self-invocation không trigger proxy)

### 9.3 REST API
- [ ] RESTful convention (GET/POST/PUT/PATCH/DELETE)
- [ ] Path variable: `/liverooms/{id}`
- [ ] Request body: `@RequestBody`
- [ ] Validation: `@Valid`

### 9.4 WS / STOMP
- [ ] Endpoint `/ws/liveroom` configured
- [ ] JWT auth trong STOMP CONNECT
- [ ] Destination: `/app/liveroom/{roomId}/...` cho send
- [ ] Destination: `/topic/liveroom/{roomId}/...` cho subscribe
- [ ] Broadcast payload có `eventType + timestamp + data`

---

## 10. Frontend Specific

### 10.1 TypeScript
- [ ] Type-safe (TypeScript strict mode)
- [ ] KHÔNG `any` (dùng `unknown` nếu cần)
- [ ] KHÔNG `@ts-ignore`

### 10.2 React patterns
- [ ] Functional components + hooks
- [ ] KHÔNG class components (trừ ErrorBoundary)
- [ ] `useEffect` có cleanup function
- [ ] `useMemo`/`useCallback` cho performance critical

### 10.3 Next.js
- [ ] `'use client'` directive cho client component
- [ ] App Router convention
- [ ] `generateMetadata` cho SEO
- [ ] Dynamic import cho heavy components

### 10.4 State management
- [ ] Zustand cho global state
- [ ] React Query cho server state (optional)
- [ ] Local state với `useState`

### 10.5 i18n (Frontend)
- [ ] Dùng `useTranslations`
- [ ] KHÔNG hardcode Vietnamese string
- [ ] Files `en.json` + `vi.json` đầy đủ

### 10.6 Accessibility
- [ ] `aria-label` cho icon buttons
- [ ] Keyboard navigation
- [ ] Color contrast WCAG AA

### 10.7 Responsive
- [ ] Mobile + desktop layout
- [ ] Touch-friendly buttons (min 44x44px)

---

## 11. Consistency Check (Cross-Doc)

### 11.1 BR là source of truth
- [ ] Code khớp với BR (R-XXX, UC-XXX, EC-XXX)
- [ ] State machine khớp với `liveroom-state-machines.md`
- [ ] API khớp với `liveroom-api-spec.md`

### 11.2 i18n keys
- [ ] Mỗi key có 3 file EN + VI (BE) + 2 file (FE)
- [ ] KHÔNG typo giữa các file

### 11.3 WS events
- [ ] EventName match ws-protocol.md
- [ ] Payload schema match

### 11.4 Error codes
- [ ] ErrorCode match BR / api-spec.md
- [ ] Có error code cho mỗi validation fail

### 11.5 State machine
- [ ] State transitions đúng
- [ ] Side effects đúng (PESSIMISTIC lock, WS broadcast, audit log)

---

## 12. Tests

### 12.1 Build success
- [ ] `mvn clean install` (Backend) pass
- [ ] `mvn spring-boot:run` start OK
- [ ] `pnpm build` (Frontend) pass
- [ ] `pnpm dev` start OK

### 12.2 Manual test
- [ ] 2 browsers test pass
- [ ] Edge cases (EC-XXX) pass
- [ ] i18n render đúng (EN + VI)

### 12.3 No linting error
- [ ] `pnpm lint` pass
- [ ] `pnpm type-check` pass

---

## 13. Cross-Doc Consistency (nếu phát hiện mâu thuẫn)

### 13.1 Phát hiện mâu thuẫn giữa 2 docs

**Quy tắc**: BR > mọi doc khác.

**Action**:
1. Đánh dấu `[MÂU THUẪN]` ở cả 2 docs
2. Sửa docs **không phải BR** cho khớp BR
3. Update changelog BR nếu cần
4. Update code nếu đã code theo docs sai

**Ví dụ** (đã phát hiện ở lần review trước):
- C1: BR EC-13/14 nói "xoá ChatMessage" → SAI với R-CHAT-06 v1.8 → Sửa EC-13/14
- C2: data-model.md nói `LiveRoom @Version` → SAI với BR §1.7 → Sửa data-model.md
- C3: api-spec.md nói `POST /music/select` → SAI với R-MUSIC-09 v1.8 → Sửa api-spec.md

### 13.2 Phát hiện code sai với doc

**Action**:
1. Sửa code cho khớp doc
2. Nếu doc sai → sửa doc trước, rồi sửa code

---

## 14. Final check trước commit

- [ ] Đã chạy tất cả checklist ở trên
- [ ] Đã update `09-progress-tracker.md` (đánh dấu task hoàn thành)
- [ ] KHÔNG có file thừa (log, tmp, ...)
- [ ] KHÔNG commit file credentials/secrets
- [ ] Commit message rõ ràng (scope + action)

---

## 15. Khi gặp vấn đề

| Vấn đề | Hành động |
|---|---|
| Không biết task này làm gì | Đọc phase doc `02-07` + doc tham chiếu |
| Không biết output đúng | Đọc Acceptance Criteria trong phase doc |
| Phát hiện mâu thuẫn doc | Báo cáo sếp, sửa doc khớp BR |
| Code không build | Check `mvn compile` output, fix syntax |
| Lint fail | Chạy `pnpm lint --fix` |
| Test fail | Check `tests/` folder, đọc error log |
| Stuck > 30 phút | Hỏi sếp hoặc AI agent |

---

## 16. Tham chiếu Rules

File này enforce policy đã document trong `.cursor/rules/`:

- `senior-dev-coding-standards.mdc` - SOLID, Clean Code
- `prefer-lombok-backend.mdc` - Lombok usage
- `no-code-comments-backend.mdc` - No comments
- `no-code-comments-frontend.mdc` - No comments
- `no-hardcoded-messages-backend.mdc` - i18n
- `no-auto-create-tests-backend.mdc` - No tests
- `no-auto-commit-push-backend.mdc` - No commit

Đọc 8 rules này trước khi code.

---

**Cập nhật**: 2026-07-26
