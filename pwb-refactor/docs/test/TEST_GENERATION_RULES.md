# Test Generation Rules — Cho AI Agent khi viết test

> File này định nghĩa các **quy tắc bắt buộc** khi AI generate code test cho `pwb-refactor/Backend/`.
>
> AI phải tuân thủ **MỌI** rule dưới đây. Nếu conflict giữa rule và yêu cầu trong test plan, rule này thắng.
>
> **Chuẩn áp dụng**: Test Pyramid + FIRST (Tim Ottinger) + Right-BICEP (Brian Marick) + CORRECT (IEEE 829).

---

## 1. Trước khi viết bất kỳ test nào

### 1.1 Đọc code nguồn trước

- [ ] Đã đọc toàn bộ file `.java` của class cần test (không chỉ signature)
- [ ] Đã đọc tất cả interface/dependency mà class inject
- [ ] Đã hiểu các exception code có thể throw (xem `IamErrorCode` hoặc error code enum tương ứng)
- [ ] Đã xác định method nào là public API cần test
- [ ] Đã xác định test thuộc layer nào (Unit / Component / Integration / Contract / E2E)
- [ ] Đã map test với chuẩn phù hợp (Right-BICEP letter / CORRECT letter)

### 1.2 Tuân thủ rule workspace

- [ ] **KHÔNG thêm code comment** trong test (`// this does X`)
- [ ] **KHÔNG dùng Lombok sai cách**: dùng `@RequiredArgsConstructor`, `@Slf4j` khi cần
- [ ] **KHÔNG tự ý commit/push** test sau khi viết xong
- [ ] **Đặt tên file**: `*Test.java` cho unit test, `*IT.java` cho integration test
- [ ] **Tag test theo chuẩn**: Comment trong test plan (KHÔNG phải code) phải ghi rõ test thuộc Right-BICEP letter nào

---

## 2. Test Class Structure

### 2.1 Package

Test class phải **mirror đúng package** của class nguồn.

```java
// Source: com.pwb.iam.application.usecase.impl.RegisterUseCaseImpl
// Test:   com.pwb.iam.application.usecase.impl.RegisterUseCaseImplTest
```

### 2.2 Annotation theo Test Pyramid

```java
// Unit test - pure Java, không cần Spring (70%)
class RegisterUseCaseImplTest { }

// Component test - 1 layer Spring, mock phần còn lại (15%)
@WebMvcTest(AuthController.class)
class AuthControllerTest { }

@WebMvcTest + DataIntegrityViolationException
class IamExceptionHandlerTest { }

// Integration test - adapter + container (10%)
@DataJpaTest
@Testcontainers
class UserRepositoryImplIT { }

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TokenManagerServiceAdapterIT { }

// Contract test - HTTP contract (3%)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthEndpointContractTest { }

// E2E test - full flow (5%)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthFlowIT { }
```

### 2.3 Naming convention & Test Pyramid mapping

**Nguyên tắc DDD trong project PWB**:
- `Port` = interface (define ở `domain/service/` hoặc `application/port/`)
- `Adapter` = implementation (define ở `infrastructure/service/impl/`)
- `Repository` = port + impl (interface + JPA adapter)

| Test class | Pyramid layer | Annotation | Container |
|------------|---------------|------------|-----------|
| `XxxUseCaseImplTest` | Unit | `@ExtendWith(MockitoExtension.class)` | None |
| `XxxModelTest` (EmailAddress, User, OtpCode) | Unit | `@ExtendWith(MockitoExtension.class)` | None |
| `XxxMapperTest` | Unit | `@ExtendWith(MockitoExtension.class)` | None |
| `XxxFacadeImplTest` | Unit | `@ExtendWith(MockitoExtension.class)` | None |
| `XxxControllerTest` | Component | `@WebMvcTest` | MockMvc |
| `XxxExceptionHandlerTest` | Component | `@WebMvcTest` | MockMvc |
| `XxxAdapterTest` (no container) | Unit | `@ExtendWith(MockitoExtension.class)` | None |
| `XxxAdapterIT` (with container) | Integration | `@SpringBootTest` + Testcontainers | Redis/Postgres/Kafka |
| `XxxRepositoryImplIT` | Integration | `@DataJpaTest` + Testcontainers | Postgres |
| `XxxFlowIT` | E2E | `@SpringBootTest` + Testcontainers | Redis + Postgres |
| `XxxContractTest` | Contract | `@SpringBootTest` | MockMvc |
| `XxxFilterTest` | Component | `@WebMvcTest` | MockMvc |
| `XxxArgumentResolverTest` | Unit | `@ExtendWith(MockitoExtension.class)` | None |

**Phân biệt `Test` vs `IT`**:
- `XxxTest` = không cần external container (dù chạy `@SpringBootTest`)
- `XxxIT` = cần Testcontainers (Redis, Postgres, Kafka, MinIO)
- File Maven: `Test` chạy ở `mvn test`, `IT` chạy ở `mvn verify` (cần Failsafe plugin)

---

## 3. Test Method Rules

### 3.1 Naming

**BẮT BUỘC**: `should_<expected>_when_<condition>`, dùng `_` thay space, tiếng Anh.

```
✅ should_throw_user_not_found_when_id_invalid
✅ should_return_access_token_when_credentials_valid
✅ should_normalize_email_to_lowercase

❌ testRegister
❌ registerTest
❌ test_register_success
❌ shouldSucceed
```

### 3.2 Tag test với chuẩn (Right-BICEP / CORRECT)

Mỗi test phải được tag với **1 chuẩn** (Right-BICEP letter hoặc CORRECT letter). Tag được ghi trong **test plan**, KHÔNG ghi trong code test.

```java
// Trong test plan:
// | should_reject_null_email | E | null → IllegalArgumentException |
//                                  ↑ chuẩn "Error" (Right-BICEP)

@Test
void should_reject_null_email() {
    assertThatThrownBy(() -> EmailAddress.of(null))
        .isInstanceOf(IllegalArgumentException.class);
}
```

**Right-BICEP letters**: B (Boundary), I (Inverse), C (Cross-check), E (Error), P (Performance)

**CORRECT letters**: C (Conformance), O (Ordering), R (Reference), E (Existence), C (Cardinality), T (Time)

### 3.3 Body structure (AAA - Arrange-Act-Assert)

Mỗi test method phải có 3 phần rõ ràng. **KHÔNG cần comment** trong code vì code đã tự giải thích qua naming.

```java
@Test
void should_throw_when_email_already_registered() {
    RegisterCommand command = new RegisterCommand("a@b.com", "Pass1234!@#", "John");
    when(userRepository.existsByEmail("a@b.com")).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode.code").isEqualTo("IAM_001");

    verifyNoInteractions(passwordHasher);
    verifyNoInteractions(emailDeliveryPort);
}
```

### 3.4 Số assertion

- **Lý tưởng**: 1 assertion per test
- **Chấp nhận được**: 2-3 assertions nếu check cùng 1 behavior (vd check cả status code + body)
- **KHÔNG chấp nhận**: 5+ assertions trong 1 test

### 3.5 Không test implementation detail

```java
// ❌ SAI - test chi tiết implementation
verify(otpGenerator).generate();
verify(otpGenerator).hash(rawCode);
verify(emailDeliveryPort).enqueue(any());

// ✅ ĐÚNG - test business behavior
User saved = useCase.execute(command);
assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
assertThat(saved.getOauthProvider()).isEqualTo(OAuthProvider.LOCAL);
```

Verify chỉ khi **mock return value quan trọng cho behavior** (vd `verify(publisher).publishAuthSuccess()` trong login success).

### 3.5.1 Quy tắc `verify()` chi tiết

**Rule 1: Tránh `verify(mock).method(any())`**

```java
// ❌ SAI - verify quá mơ hồ, không assert gì cụ thể
verify(emailDeliveryPort).enqueue(any());

// ✅ ĐÚNG - dùng ArgumentCaptor để verify payload
ArgumentCaptor<EmailEnqueueCommand> captor = ArgumentCaptor.forClass(EmailEnqueueCommand.class);
verify(emailDeliveryPort).enqueue(captor.capture());
assertThat(captor.getValue().template()).isEqualTo(EmailTemplate.OTP_REGISTER);
```

**Rule 2: KHÔNG dùng `verifyNoMoreInteractions()` trừ khi test pure delegation**

```java
// ❌ SAI - test use case phức tạp, không nên verify tất cả
verifyNoMoreInteractions(userRepository, emailDeliveryPort, otpCodeRepository);

// ✅ ĐÚNG - chỉ verify call quan trọng (mock return value)
verify(publisher).publishAuthSuccess();
```

**Rule 3: Dùng `times()` rõ ràng khi cần**

```java
// ✅ OK - verify call count (business rule quan trọng)
verify(attemptChecker, times(1)).recordFailure(email);

// ✅ OK - verify NEVER (negative assertion)
verify(attemptChecker, never()).recordFailure(email);
```

**Rule 4: Tránh `verify()` trên stub-only mock**

```java
// ❌ SAI - stub mock không cần verify
verify(passwordHasher).hash("raw"); // passwordHasher chỉ là stub

// ✅ ĐÚNG - verify behavior trên entity thật
User saved = useCase.execute(command);
assertThat(saved.getPasswordHash()).isEqualTo("hashed:raw");
```

**Rule 5: `verify()` trong chain test phải rõ intent**

```java
// ✅ OK - khi test ordering giữa các call (CORRECT - O)
InOrder inOrder = inOrder(userRepository, emailDeliveryPort);
inOrder.verify(userRepository).save(any());
inOrder.verify(emailDeliveryPort).enqueue(any());
```

### 3.6 Right-BICEP checklist cho mỗi test

Trước khi viết test, tự hỏi:

- [ ] **B**oundary: Test có cover min/max/off-by-one không?
- [ ] **I**nverse: Test có verify kết quả ngược lại không?
- [ ] **C**ross-check: Test có dùng kỹ thuật khác verify không?
- [ ] **E**rror: Test có cover sad path không?
- [ ] **P**erformance: Test có verify SLA không?

### 3.7 CORRECT checklist cho mỗi test

- [ ] **C**onformance: Format đúng chuẩn (RFC, JWT)?
- [ ] **O**rdering: Thứ tự xử lý đúng?
- [ ] **R**ange: Giá trị trong khoảng cho phép?
- [ ] **R**eference: External dependency đúng?
- [ ] **E**xistence: Tồn tại khi cần?
- [ ] **C**ardinality: 0/1/n phần tử?
- [ ] **T**ime: Đúng thời điểm?

### 3.8 `@DisplayName` — Cho test report dễ đọc

**BẮT BUỘC** dùng `@DisplayName` cho test có kịch bản phức tạp, kèm emoji phân loại:

```java
@DisplayName("🟢 [Happy] Đăng ký thành công khi email hợp lệ")
@Test
void should_register_local_user_when_email_valid_and_password_strong() { ... }

@DisplayName("🔴 [Error] Throw IAM_001 khi email đã tồn tại")
@Test
void should_throw_when_email_already_registered() { ... }

@DisplayName("🟡 [Boundary] Reset token expired đúng thời điểm expiresAt")
@Test
void should_throw_reset_token_expired_at_boundary() { ... }
```

**Emoji chuẩn**:
- 🟢 Happy path
- 🔴 Error / Sad path
- 🟡 Boundary / Edge case
- 🔵 Side effect / Verify
- 🟣 Performance / Recovery
- ⚪ Security

**Lý do**: Test report (Surefire, Jenkins) sẽ hiển thị tên dễ đọc hơn `should_throw_when_email_already_registered` cho non-technical stakeholder review.

---

## 4. Mock Rules

### 4.1 Mock gì

**MOCK** (boundary):
- `*Repository`
- `*Port` interface
- `*Adapter` của external service (vd `TokenManagerService`, `EmailDeliveryPort`)
- `RestTemplate`, `KafkaTemplate`, etc.

**KHÔNG MOCK** (value object / pure logic):
- `EmailAddress`, `Password`, `User`, `OtpCode`, `Role`, `RoleName`
- `BusinessException` và `ErrorCode` enum
- `LoginResult`, `AuthView`, các record/command
- Helper static method

### 4.2 Stub class cho interface phức tạp

Khi interface có nhiều method, **KHÔNG dùng `Mockito.mock()`** rồi stub từng method. Tạo `Stub` class:

```java
public class StubOtpGenerator implements OtpGenerator {
    private final Map<String, String> hashByCode = new ConcurrentHashMap<>();

    public void presetHash(String code, String hash) {
        hashByCode.put(code, hash);
    }

    @Override
    public String generate() {
        return "123456";
    }

    @Override
    public String hash(String raw) {
        return "sha256:" + raw;
    }

    @Override
    public boolean matches(String raw, String hash) {
        return hashByCode.getOrDefault(raw, "sha256:" + raw).equals(hash);
    }
}
```

### 4.3 Setup pattern

```java
@ExtendWith(MockitoExtension.class)
class RegisterUseCaseImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private OtpCodeRepository otpCodeRepository;
    @Mock private PasswordHasher passwordHasher;
    @Mock private ValidatePasswordPolicyUseCase validatePasswordPolicyUseCase;
    @Mock private EmailDeliveryPort emailDeliveryPort;
    @Mock private ThrottlingService throttlingService;
    @Mock private AuthEventPublisher authEventPublisher;
    @Mock private OtpProperties otpProperties;

    private StubOtpGenerator otpGenerator;
    private RegisterUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        otpGenerator = new StubOtpGenerator();
        useCase = new RegisterUseCaseImpl(
            userRepository, roleRepository, otpCodeRepository,
            passwordHasher, validatePasswordPolicyUseCase,
            otpGenerator, emailDeliveryPort, throttlingService,
            authEventPublisher, otpProperties
        );
        lenient().when(throttlingService.enforceCooldown(any(), any())).thenReturn(0L);
        lenient().when(otpProperties.getTtlMinutes()).thenReturn(5);
        lenient().when(otpProperties.getDailyLimit()).thenReturn(10);
    }
}
```

> **Dùng `lenient()`** cho stub mà một số test không cần — tránh `UnnecessaryStubbingException`.

### 4.4 Quy tắc cleanup cho MDC & ThreadLocal

Khi test có sử dụng `MDC` (logging context) hoặc `ThreadLocal` (auth context, correlation ID), **BẮT BUỘC** cleanup sau test:

```java
@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_set_correlation_id_in_mdc() {
        MDC.put("correlationId", "test-123");
        assertThat(MDC.get("correlationId")).isEqualTo("test-123");
    }
}
```

**Lý do**: MDC & ThreadLocal leak giữa các test gây flaky test khó debug. Test fail không phải vì logic sai mà vì state từ test trước.

**Rule**:
- `@BeforeEach`: reset MDC, SecurityContext, ThreadLocal
- `@AfterEach`: cleanup lại (defense in depth)
- KHÔNG BAO GIỜ `MDC.put()` without `MDC.clear()` trong test
- Dùng `try-finally` khi test phải set MDC giữa chừng:

```java
@Test
void should_propagate_correlation_id_through_async() throws Exception {
    MDC.put("correlationId", "abc-123");
    try {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            assertThat(MDC.get("correlationId")).isEqualTo("abc-123");
        });
        future.get(5, TimeUnit.SECONDS);
    } finally {
        MDC.clear();
    }
}
```

---

## 5. Assertion Library

### 5.1 Dùng AssertJ (`org.assertj.core.api.Assertions.*`)

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

// ✅ Tốt - chainable, đọc như tiếng Anh
assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
assertThatThrownBy(() -> useCase.execute(cmd)).isInstanceOf(BusinessException.class);

// ❌ Xấu - JUnit cũ, verbose
assertEquals(UserStatus.ACTIVE, user.getStatus());
assertThrows(BusinessException.class, () -> useCase.execute(cmd));
```

### 5.2 BusinessException assertion

Dùng `extracting` để check nested field:

```java
assertThatThrownBy(() -> useCase.execute(command))
    .isInstanceOf(BusinessException.class)
    .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode().code()).isEqualTo("IAM_001");
        assertThat(be.getErrorCode().category()).isEqualTo(ErrorCategory.CONFLICT);
        assertThat(be.getMetadata()).containsEntry("field", "email");
    });
```

### 5.3 Time assertion (CORRECT - T)

Dùng `Clock` mock hoặc `ArgumentCaptor` thay vì `Thread.sleep`:

```java
@Test
void should_set_otp_expiry_from_properties() {
    when(otpProperties.getTtlMinutes()).thenReturn(5);

    useCase.execute(command);

    ArgumentCaptor<OtpCode> captor = ArgumentCaptor.forClass(OtpCode.class);
    verify(otpCodeRepository).save(captor.capture());
    Duration delta = Duration.between(Instant.now(), captor.getValue().expiresAt());
    assertThat(delta).isCloseTo(Duration.ofMinutes(5), Duration.ofSeconds(2));
}
```

---

## 6. Controller Test Rules

### 6.1 Setup chuẩn

```java
@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, IamExceptionHandler.class, MessageConfig.class})
@MockBean(JwtAuthenticationFilter.class)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private IamFacade iamFacade;
    @MockBean private MessageResolver messageResolver;

    @BeforeEach
    void setUp() {
        lenient().when(messageResolver.get(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }
}
```

### 6.2 Test pattern

```java
@Test
void should_return_201_with_userId_when_register_valid() throws Exception {
    UUID userId = UUID.randomUUID();
    when(iamFacade.register(any())).thenReturn(userId);

    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "email": "a@b.com",
                  "password": "Pass1234!@#",
                  "fullName": "John"
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value(userId.toString()));
}
```

### 6.3 Test `@CurrentUser`, `@CurrentClientIp`, `@CurrentUserAgent`

Cần mock Argument Resolver:

```java
@TestConfiguration
static class TestConfig implements WebMvcConfigurer {
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter param) {
                return param.hasParameterAnnotation(CurrentUser.class);
            }
            @Override
            public Object resolveArgument(...) {
                return testUserId;
            }
        });
    }
}
```

---

## 7. Integration Test Rules

### 7.1 Testcontainers

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthFlowIT {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("pwb.iam.seeder.enabled", () -> "false");
    }

    @Autowired private TestRestTemplate restTemplate;
    @MockBean private EmailDeliveryPort emailDeliveryPort;

    @Test
    void should_register_then_verify_then_login() {
        // 1. Register
        var registerBody = Map.of(
            "email", "test@example.com",
            "password", "Pass1234!@#",
            "fullName", "Test User"
        );
        var registerResp = restTemplate.postForEntity(
            "/api/v1/auth/register", registerBody, Map.class);
        assertThat(registerResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID userId = UUID.fromString(
            (String) registerResp.getBody().get("data").get("userId"));

        // 2. Verify OTP
        // ...
    }
}
```

### 7.2 Lấy OTP từ email mock

Vì `EmailDeliveryPort` được mock trong IT, cần capture OTP để verify:

```java
@MockBean private EmailDeliveryPort emailDeliveryPort;
private final List<EmailEnqueueCommand> sentEmails = new ArrayList<>();

@BeforeEach
void setUp() {
    doAnswer(inv -> {
        sentEmails.add(inv.getArgument(0));
        return null;
    }).when(emailDeliveryPort).enqueue(any());
}

private String extractOtpFromEmail(UUID userId) {
    var email = sentEmails.stream()
        .filter(e -> e.userId().equals(userId))
        .filter(e -> e.template() == EmailTemplate.OTP_REGISTER)
        .findFirst()
        .orElseThrow();
    return email.variables().get("code");
}
```

### 7.3 CompositeContainer pattern

Khi cần nhiều container (Redis + Postgres + Kafka), dùng `Network` để gom:

```java
@Testcontainers
class AuthFlowIT {
    @Container
    static DockerComposeContainer<?> COMPOSE = new DockerComposeContainer<>(
        new File("src/test/resources/docker-compose-test.yml"))
        .withExposedServices("postgres", "redis");
}
```

---

## 8. Repository Test Rules

### 8.1 `@DataJpaTest` cho repository đơn giản

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryImplIT {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    }

    @Autowired private UserJpaRepository jpaRepository;

    private UserRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new UserRepositoryImpl(jpaRepository, new UserMapperImpl());
    }
}
```

### 8.2 Test các query method (CORRECT - O, R, E, C)

| Test case | Chuẩn |
|-----------|-------|
| `should_findByEmail_when_user_exists` | O (Ordering) |
| `should_return_empty_when_email_not_found` | E (Existence) |
| `should_findByOAuthProviderAndOAuthId` | O (Ordering) |
| `should_save_with_uuid_id` (verify UUID được generate) | T (Time) |
| `should_return_paginated_result` | C (Cardinality) |
| `should_count_records_by_date_range` | T (Time) |

---

## 9. Mail/Outbox IT Rules

### 9.1 Outbox roundtrip test

```java
@Test
void should_enqueue_email_event_to_outbox_when_user_registered() {
    User saved = useCase.execute(command);

    ArgumentCaptor<OutboxEnqueueRequested> captor =
        ArgumentCaptor.forClass(OutboxEnqueueRequested.class);
    verify(outboxWriter).write(captor.capture());

    OutboxEnqueueRequested event = captor.getValue();
    assertThat(event.aggregateType()).isEqualTo("User");
    assertThat(event.aggregateId()).isEqualTo(saved.getUserId().toString());
}
```

### 9.2 Kafka producer mock

```java
@MockBean private KafkaTemplate<String, Object> kafkaTemplate;

when(kafkaTemplate.send(anyString(), anyString(), any()))
    .thenReturn(CompletableFuture.completedFuture(null));
```

### 9.3 Outbox pattern test (transactional + relay)

**Rule 1**: Test outbox writer phải verify transactional boundary

```java
@Test
void should_rollback_outbox_when_business_transaction_fails() {
    assertThatThrownBy(() -> {
        doInTransaction(() -> {
            userRepository.save(user);
            outboxWriter.write(event);
            throw new RuntimeException("simulate failure");
        });
    }).isInstanceOf(RuntimeException.class);

    assertThat(outboxRepository.findAll()).isEmpty();
    assertThat(userRepository.findAll()).isEmpty();
}
```

**Rule 2**: Test outbox relay phải verify Kafka + DB transaction tách biệt

```java
@Test
void should_update_db_to_published_after_kafka_send_success() {
    outboxWriter.write(event);
    outboxRelayScheduler.publishPending();

    UserOutbox row = outboxRepository.findById(event.getId()).orElseThrow();
    assertThat(row.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    verify(kafkaTemplate).send(eq(topic), eq(event.getAggregateId()), eq(payload));
}
```

**Rule 3**: Test idempotency của outbox (cùng event gửi 2 lần)

```java
@Test
void should_not_publish_same_event_twice() {
    outboxWriter.write(event);
    outboxRelayScheduler.publishPending();
    outboxRelayScheduler.publishPending();

    verify(kafkaTemplate, times(1)).send(any(), any(), any());
}
```

### 9.4 Audit listener test

```java
@Test
void should_set_createdAt_on_new_entity() {
    UserEntity entity = new UserEntity();
    entity.setEmail("a@b.com");

    auditListener.onPrePersist(entity);

    assertThat(entity.getCreatedAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
}

@Test
void should_set_updatedAt_on_existing_entity() {
    UserEntity entity = new UserEntity();
    entity.setEmail("a@b.com");
    entity.setCreatedAt(Instant.now().minusSeconds(60));

    auditListener.onPreUpdate(entity);

    assertThat(entity.getUpdatedAt()).isAfter(entity.getCreatedAt());
}
```

**Rule audit test**:
- Test `onPrePersist`: set `createdAt`, `createdBy`
- Test `onPreUpdate`: set `updatedAt`, `updatedBy` (KHÔNG touch createdAt)
- Test với entity chưa có field → verify field mới set
- Test với entity đã có field → verify field KHÔNG bị overwrite

---

## 10. Forbidden Patterns

### 10.1 KHÔNG làm thế này

```java
// ❌ Dùng Thread.sleep
Thread.sleep(1000);

// ❌ Dùng Exception chung
try { useCase.execute(cmd); } catch (Exception e) { }

// ❌ Test implementation detail
verify(passwordHasher, times(1)).hash(anyString());

// ❌ Magic number không tên
assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION); // OK
assertThat(result.size()).isEqualTo(3); // ❌ magic

// ❌ Catch và nuốt exception
} catch (BusinessException e) {
    // ignore
}

// ❌ In ra console
System.out.println("User registered: " + user.getUserId());

// ❌ Comment giải thích
// Test when user doesn't exist
@Test
void test1() { ... }

// ❌ Dùng UUID.randomUUID() trong assertion
assertThat(user.getId()).isEqualTo(UUID.randomUUID()); // luôn pass

// ❌ Test 2 behavior trong 1 test method
@Test
void should_register_and_send_welcome_email() {
    // BAD: register behavior + email behavior
}

// ❌ Tag test sai chuẩn
// Trong test plan: | should_throw | E | ... |
// Nhưng code test lại verify state (không phải error)
```

### 10.2 PHẢI làm thế này

```java
// ✅ Constant cho magic number
private static final int EXPECTED_ATTEMPTS = 5;
assertThat(attempts).isEqualTo(EXPECTED_ATTEMPTS);

// ✅ Test name tự giải thích
@Test
void should_lock_otp_after_max_attempts_exceeded() { ... }

// ✅ Verify behavior, không verify mock calls (trừ khi cần thiết)
assertThatThrownBy(() -> useCase.execute(cmd))
    .isInstanceOf(BusinessException.class);

// ✅ Extract helper cho setup phức tạp
private RegisterCommand commandFor(String email, String password) { ... }

// ✅ Dùng Builder/Test data factory
private static final TestUser JOHN = TestUserBuilder.localActive().build();

// ✅ Tách 2 behavior thành 2 test
@Test
void should_register_user() { ... }

@Test
void should_send_welcome_email_after_register() { ... }

// ✅ Tag test đúng chuẩn
// Trong test plan: | should_throw_when_email_already_registered | E |
// Code test: throws BusinessException (đúng chuẩn E)
```

---

## 11. Khi test cần thiết bị external

| Thiết bị | Tool | Lý do |
|----------|------|-------|
| Redis | Testcontainers `redis:7-alpine` | Test Lua script + key expiry đúng thực tế |
| PostgreSQL | Testcontainers `postgres:16-alpine` | Test JPA + Flyway migration thật |
| Kafka | Testcontainers `confluentinc/cp-kafka:7.5.0` | Test producer/consumer thật |
| MinIO | Testcontainers `minio/minio:latest` | Test S3 API thật |
| Google API | WireMock hoặc custom mock | Không gọi Google thật trong test |
| SMTP | GreenMail hoặc Mock SMTP server | Test email delivery |

**KHÔNG BAO GIỜ** mock Redis bằng `Mockito.mock(StringRedisTemplate.class)` cho adapter test. Redis logic (Lua script, TTL, atomic ops) cần chạy thật.

### 11.1 Version pinning — BẮT BUỘC

**Container version KHÔNG ĐƯỢC dùng `latest`** — luôn pin version cụ thể:

```xml
<!-- application-test.yml -->
testcontainers:
  redis: redis:7.2.4-alpine3.19
  postgres: postgres:16.3-alpine3.19
  kafka: confluentinc/cp-kafka:7.5.3
  minio: minio/minio:RELEASE.2024-04-18T19-09-19Z
```

**Thư viện test** (trong `dependencyManagement` của parent pom):

```xml
<junit.version>5.10.2</junit.version>
<mockito.version>5.11.0</mockito.version>
<assertj.version>3.25.3</assertj.version>
<testcontainers.version>1.19.7</testcontainers.version>
<jacoco.version>0.8.11</jacoco.version>
<pitest.version>1.15.0</pitest.version>
<wiremock.version>3.5.4</wiremock.version>
<greenmail.version>2.0.1</greenmail.version>
<awaitility.version>4.2.0</awaitility.version>
```

**Lý do**:
- Container `latest` có thể break test khi có breaking change upstream
- Thư viện test cần pinned để reproducible build
- Khi upgrade → test 1 version, verify toàn bộ pass, mới commit

### 11.2 Khi nào cần upgrade version

- Khi Spring Boot release minor version mới (vd 3.5 → 3.6)
- Khi có security patch cho thư viện test
- Khi team quyết định upgrade trong sprint planning
- **KHÔNG** tự ý upgrade version trong commit nhỏ

---

## 12. Coverage target (theo Test Pyramid)

| Layer | Pyramid | Target | Lý do |
|-------|---------|--------|-------|
| `domain/model/` | Unit (70%) | 95% | Pure logic, dễ test, phải cover hết |
| `domain/service/` (interface) | Unit | 0% | Chỉ là contract, test qua adapter |
| `application/usecase/` | Unit | 90% | Business rule quan trọng nhất |
| `application/facade/` | Unit | 80% | Chỉ là delegation |
| `api/controller/` | Component | 80% | Verify status code + validation |
| `infrastructure/persistence/adapter/` | Integration | 70% | Test qua IT |
| `infrastructure/service/impl/` (adapter) | Integration | 70% | Test qua IT với container |
| `api/dto/` | Component | 60% | Validation rules |
| `infrastructure/mail/` | Unit + Integration | 70% | Mock Kafka + verify outbox |
| `infrastructure/audit/` | Unit | 60% | Listener chỉ cần test khi có logic |

### 12.1 Mutation Testing (PIT)

| Layer | Target mutation score |
|-------|----------------------|
| `domain/model/` | ≥ 80% |
| `application/usecase/` | ≥ 70% |
| `api/controller/` | ≥ 60% |

```xml
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.15.0</version>
    <configuration>
        <targetClasses>
            <param>com.pwb.iam.domain.model.*</param>
            <param>com.pwb.iam.application.usecase.impl.*</param>
        </targetClasses>
        <targetTests>
            <param>com.pwb.iam.domain.model.*Test</param>
            <param>com.pwb.iam.application.usecase.impl.*Test</param>
        </targetTests>
    </configuration>
</plugin>
```

### 12.2 JaCoCo (line coverage)

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

---

## 13. TDD Workflow

### 13.1 Red-Green-Refactor cycle

```
1. 🔴 Red:    Viết test trước, test fail
2. 🟢 Green:  Viết code production để test pass
3. 🔵 Refactor: Cải thiện code, test vẫn pass
4. 🔁 Lặp lại
```

### 13.2 Áp dụng cho từng use case

```java
// Bước 1: Red
@Test
void should_throw_when_email_already_registered() {
    when(userRepository.existsByEmail("a@b.com")).thenReturn(true);
    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(BusinessException.class);
}
// → Test fail vì chưa có code

// Bước 2: Green
// Viết code production để throw exception
// → Test pass

// Bước 3: Refactor
// Extract magic string, dùng IamErrorCode.EMAIL_ALREADY_REGISTERED
// → Test vẫn pass
```

### 13.3 Test order ưu tiên

Khi viết test cho 1 use case, viết theo thứ tự:

1. **Happy path** (test số 1)
2. **Throw cases** (test 2-5: validation, business rule, throttling)
3. **Edge cases** (test 6-8: null, blank, boundary)
4. **Side effects** (test 9-10: email enqueue, event publish, repository call ordering)

---

## 14. Khi viết test xong

### 14.1 Checklist cuối cùng

- [ ] Tất cả test pass locally: `mvn -pl modules/iam test`
- [ ] Integration test pass: `mvn -pl modules/iam verify`
- [ ] Không có `//` comment trong test code
- [ ] Không có `printStackTrace`, `System.out.println`, `Thread.sleep`
- [ ] Mỗi test độc lập (không phụ thuộc test khác)
- [ ] Tên test mô tả được kịch bản (`should_X_when_Y`)
- [ ] Stub class đặt trong package `test.support` hoặc `test.fixtures`
- [ ] Mỗi test được tag với chuẩn (Right-BICEP / CORRECT) trong test plan
- [ ] Test có `@DisplayName` với emoji phân loại (nếu > 10 test trong class)
- [ ] Không tự ý commit — đợi user review
- [ ] MDC / SecurityContext / ThreadLocal được cleanup trong `@AfterEach`
- [ ] Container version đã pin (không `latest`)
- [ ] Không `verify(mock).method(any())` — dùng ArgumentCaptor
- [ ] Audit listener test đủ 2 case (PrePersist + PreUpdate)

### 14.2 Cách báo cáo cho user

Sau khi viết test xong, báo cáo:

```
✅ Hoàn thành test cho RegisterUseCaseImpl
📁 File mới: modules/iam/src/test/java/com/pwb/iam/application/usecase/impl/RegisterUseCaseImplTest.java
📊 Số test: 8 (1 happy path, 4 sad path, 3 edge case)
🎯 Chuẩn: Right-BICEP [E×4, I×1, O×1, B×2]
🎯 Coverage: domain + use case → 90%
⏳ Cần user review trước khi commit.
```

**BẮT BUỘC báo cáo**:
- Test pyramid layer (Unit/Component/Integration/E2E)
- Số test: happy + sad + edge
- Chuẩn áp dụng: list các letter (Right-BICEP + CORRECT)
- Coverage target đạt được

### 14.3 KHÔNG tự ý chạy git

Theo rule `no-auto-commit-push-backend.mdc`, KHÔNG tự ý chạy `git add` hay `git commit`.

### 14.4 Tạo báo cáo lỗi khi test fail

**BẮT BUỘC**: Khi chạy test mà có test fail, **PHẢI tạo file báo cáo lỗi** để track và fix sau.

**File path**: `docs/test/reports/TEST_FAILURE_REPORT_<module>_<yyyy-MM-dd>.md`

Ví dụ: `docs/test/reports/TEST_FAILURE_REPORT_iam_2026-07-31.md`

**Template báo cáo**:

```markdown
# Test Failure Report — [Module] — [yyyy-MM-dd]

> Tổng: X test | ✅ Y pass | ❌ Z fail | ⏭️ W skip

## Failures

| # | Test method | Class | Expected | Actual | Root cause | File cần fix | Loại | Mức |
|---|------------|-------|----------|--------|------------|-------------|------|-----|
| 1 | `should_allow_re_registration_when_pending` | `RegisterUseCaseImplTest` | 201 | 409 `IAM_001` | `existsByEmail` không check status | `RegisterUseCaseImpl.java:63` | CODE_BUG | 🔴 |
| 2 | ... | ... | ... | ... | ... | ... | ... | ... |

## Action Items

| # | Hành động | File | Status |
|---|-----------|------|--------|
| 1 | Sửa `existsByEmail` check status | `RegisterUseCaseImpl.java` | [ ] TODO |

> Loại: `CODE_BUG` | `TEST_BUG` | `CONFIG_ISSUE` | `MISSING_IMPL`
> Mức: 🔴 Critical | 🟡 Medium | 🟢 Low
```

**Quy tắc khi tạo báo cáo**:

1. **Mỗi test fail = 1 mục** trong "Chi tiết lỗi", KHÔNG gộp
2. **Phân loại bắt buộc**: `CODE_BUG` (code sai), `TEST_BUG` (test sai), `CONFIG_ISSUE` (config thiếu), `MISSING_IMPL` (chưa implement)
3. **Root cause**: Phải dự đoán nguyên nhân dựa trên error message + code analysis
4. **File cần fix**: Ghi rõ file + line number nếu biết
5. **Mức độ**: 🔴 Critical (ảnh hưởng user), 🟡 Medium (edge case), 🟢 Low (cosmetic)
6. Nếu chạy nhiều lần trong ngày → **append** vào cùng file, thêm timestamp
7. Khi fix xong → đánh dấu `[x] DONE` trong Action Items, KHÔNG xóa mục cũ

---

## 15. Tham chiếu chuẩn

### 15.1 Nguồn gốc chuẩn

| Chuẩn | Tác giả | Năm | Tài liệu |
|-------|---------|-----|----------|
| Test Pyramid | Martin Fowler | 2012 | https://martinfowler.com/bliki/TestPyramid.html |
| FIRST | Tim Ottinger, Robert Martin | 2009 | Clean Code |
| Right-BICEP | Brian Marick | 1995 | JUnit Recipies |
| CORRECT | IEEE 829 / Andy Hunt | 1999 | Pragmatic Unit Testing |
| AAA Pattern | Bill Wake | 2003 | xUnit Test Patterns |

### 15.2 Mapping chuẩn với IAM

| Test Type | Áp dụng chuẩn chính | Phụ |
|-----------|---------------------|-----|
| Domain model (User, OtpCode, EmailAddress) | Right-BICEP (B, I, E) | CORRECT (O, T) |
| Use case (Register, Login, etc.) | CORRECT (O, R, T) | Right-BICEP (E) |
| Controller | Right-BICEP (B, E) | CORRECT (C) |
| Repository | CORRECT (O, R, E, C) | — |
| Adapter (Redis, JWT) | CORRECT (T, O) | Right-BICEP (E) |
| E2E flow | CORRECT (O, R, T) | Right-BICEP (E) |
| Contract | CORRECT (C) | — |
| Security | Right-BICEP (E, I) | — |
| Performance | Right-BICEP (P) | — |

### 15.3 Cross-reference

**File liên quan trong project**:

| File | Vai trò |
|------|---------|
| `docs/IAM_TEST_PLAN.md` | Test plan chi tiết cho module IAM — 100 test case |
| `docs/SHARED_TEST_PLAN.md` | Test plan cho shared-kernel, shared-web, shared-infrastructure — 230 test case |
| `pwb-refactor/Backend/shared/shared-development-standards.md` | Coding standard cho shared modules (cùng áp dụng cho test) |
| `.cursor/rules/pwb-refactor-context.mdc` | Project context — kiến trúc, layer, AutoConfiguration |
| `.cursor/rules/senior-dev-coding-standards.mdc` | Senior dev standard — SOLID, clean code |
| `.cursor/rules/no-code-comments-backend.mdc` | Rule comment — KHÔNG comment trong code test |
| `.cursor/rules/no-auto-create-tests-backend.mdc` | Rule viết test — chỉ viết khi user yêu cầu |
| `.cursor/rules/no-auto-commit-push-backend.mdc` | Rule git — không tự ý commit/push test |
| `.cursor/rules/respond-in-vietnamese.mdc` | Rule giao tiếp — tiếng Việt, xưng "em" |

**Mối quan hệ với test plan**:
- File này (`TEST_GENERATION_RULES.md`) định nghĩa **CÁCH viết test** (rules, patterns)
- File `IAM_TEST_PLAN.md` / `SHARED_TEST_PLAN.md` định nghĩa **TEST GÌ cần viết** (test cases)
- AI đọc cả 2 loại file trước khi generate test code

### 15.4 Quick decision tree — Khi gặp câu hỏi

```
Q: Test này thuộc layer nào?
→ Xem test có cần Spring context không
   - Không → Unit (70%)
   - Có, 1 layer → Component (15%)
   - Có container (Redis/Postgres/Kafka) → Integration (10%)
   - Có full flow HTTP → E2E (5%)

Q: Test này thuộc chuẩn nào?
→ Xem behavior cần verify
   - Validate format input → Conformance (C)
   - Thứ tự xử lý → Ordering (O)
   - External dependency → Reference (R)
   - Resource có tồn tại → Existence (E)
   - Đếm 0/1/n → Cardinality (C)
   - Thời điểm/TTL → Time (T)
   - Min/max edge → Boundary (B)
   - Verify kết quả ngược → Inverse (I)
   - Dùng kỹ thuật khác verify → Cross-check (C)
   - Sad path → Error (E)
   - SLA time → Performance (P)

Q: Code test có cần comment không?
→ KHÔNG (rule no-code-comments-backend.mdc)
→ Trừ Khi comment là Javadoc có giá trị thật

Q: Test fail liên tục, không rõ nguyên nhân?
→ Check ThreadLocal / MDC / SecurityContext cleanup (§4.4)
→ Check container có stale state (FLUSHDB giữa test)
→ Check static state ngoài test class
→ Check @DirtiesContext
```
