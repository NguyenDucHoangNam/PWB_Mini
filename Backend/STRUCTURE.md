# Backend — Module Structure Convention

> Tài liệu này mô tả **nguyên mẫu cấu trúc modular monolith** được chuẩn hoá cho team. Mọi module nghiệp vụ mới **BẮT BUỘC** tuân theo layout `api` / `core` / `infrastructure` dưới đây.

---

## 1. Tổng quan cây thư mục

```
my-application/
├── pom.xml                          ← Parent POM (packaging = pom)
├── docker-compose.yml
├── README.md
│
├── src/                            ← Resources dùng chung (nếu có)
│   └── main/
│       └── resources/
│           ├── application.yml      ← Shared config (DB, Redis, Kafka)
│           └── logback-spring.xml
│
├── bootstrap/                      ← (Optional) Module chứa Main class
│   ├── pom.xml
│   └── src/main/java/com/example/Application.java
│
├── shared-kernel/                  ← Module dùng chung, framework-agnostic (POJO, enum, exception, DTO)
│   ├── pom.xml
│   └── src/main/java/com/example/shared/
│       ├── api/                    ← Common interfaces, DTO dùng chung
│       └── util/                   ← Helper, validator, constant
│
├── shared-web/                     ← Module dùng chung, Spring-specific (handler, filter, config)
│   ├── pom.xml
│   └── src/main/java/com/example/sharedweb/
│       ├── exception/              ← GlobalExceptionHandler (@RestControllerAdvice)
│       ├── filter/                 ← CorrelationIdFilter, RequestLoggingFilter
│       ├── config/                 ← JacksonConfig, OpenApiConfig, WebConfig
│       └── advice/                 ← ResponseBodyAdvice, ControllerAdvice khác
│
└── modules/                        ← Thư mục chứa các module nghiệp vụ
    ├── module-name-1/
    │   ├── pom.xml
    │   └── src/main/java/com/example/module1/
    │       ├── api/                ← Public API (interface, DTO, exception public)
    │       │   ├── Module1Facade.java        ← (Hoặc interface Module1Service)
    │       │   ├── dto/
    │       │   └── events/                    ← Events mà module này PUBLISH
    │       ├── core/                ← Domain logic (entities, value objects, domain events)
    │       │   ├── model/
    │       │   ├── service/        ← Domain services (business rules)
    │       │   └── events/         ← Domain events (nội bộ)
    │       └── infrastructure/     ← Tech detail (JPA, REST, Kafka, Email, ...)
    │           ├── persistence/    ← Repository impl
    │           ├── web/            ← Controller (REST)
    │           └── messaging/      ← Kafka producer/listener
    │
    └── module-name-2/
        ├── pom.xml
        └── src/main/java/com/example/module2/
            ├── api/
            ├── core/
            └── infrastructure/
```

---

## 2. Vai trò của từng layer trong 1 module

| Layer | Chứa gì | Phụ thuộc vào | Cho phép truy cập từ |
|---|---|---|---|
| `api` | Public contract: facade interface, DTO, public exception, **events publish ra ngoài** | `shared-kernel` | Module khác (qua facade) |
| `core` | Domain logic thuần: entity, value object, domain service, **domain event nội bộ**, business rule | `api` của chính nó + `shared-kernel` | `infrastructure` của chính nó |
| `infrastructure` | Adapter ra thế giới bên ngoài: JPA repository, REST controller, Kafka producer/consumer, mail, external API | `core` + `api` của chính nó + `shared-kernel` | Spring DI container |

### Nguyên tắc "mũi tên phụ thuộc"

```
        ┌──────────────────────────┐
        │      infrastructure      │   ← JPA, REST, Kafka, Mail
        └────────────┬─────────────┘
                     │ depends on
                     ▼
        ┌──────────────────────────┐
        │           core           │   ← Entity, domain service, domain event
        └────────────┬─────────────┘
                     │ depends on
                     ▼
        ┌──────────────────────────┐
        │            api           │   ← Facade, DTO, published event
        └──────────────────────────┘
```

- **`api`** là layer thấp nhất → chỉ được depend vào `shared-kernel`.
- **`core`** depend vào `api` (và `shared-kernel`). **Không** được depend vào `infrastructure`.
- **`infrastructure`** depend vào `core` + `api`. Là layer cao nhất, nơi gắn framework/tech detail.
- Module khác **chỉ gọi qua `api` facade**, không `@Autowire` trực tiếp vào `infrastructure` của module kia.

---

## 3. Phân biệt `shared-kernel` vs `shared-web`

| Đặc điểm | `shared-kernel` | `shared-web` |
|---|---|---|
| **Phụ thuộc framework** | ❌ Không (POJO thuần Java) | ✅ Có (`spring-boot-starter-web`) |
| **Chứa gì** | Enum, exception, Value Object, DTO dùng chung | `@RestControllerAdvice`, `Filter`, `WebMvcConfigurer`, Jackson/Spring config |
| **Được dùng bởi** | Tất cả module (`api`, `core`, `infrastructure`) | `bootstrap`, `modules/<name>/infrastructure/web/` |
| **Compile độc lập** | ✅ Không cần Spring context | ⚠️ Cần `spring-boot-starter-web` trên classpath |
| **Test** | Test thuần Java (`JUnit` + `AssertJ`) | Test với `@WebMvcTest` hoặc `MockMvc` |

### Nguyên tắc chọn nơi đặt code

```
Có dùng annotation Spring (@RestControllerAdvice, @Component, @Configuration)?
        │
        ├── CÓ → shared-web/
        │
        └── KHÔNG → shared-kernel/
```

**Ví dụ thực tế:**

| Class | Vì sao đặt vào đó |
|---|---|
| `ErrorCode` (enum) | Không dùng Spring → `shared-kernel` |
| `BaseBusinessException` (abstract) | Không dùng Spring → `shared-kernel` |
| `ErrorResponse` (DTO) | Không dùng Spring → `shared-kernel` |
| `GlobalExceptionHandler` | Dùng `@RestControllerAdvice` → `shared-web` |
| `CorrelationIdFilter` | Dùng `OncePerRequestFilter` → `shared-web` |
| `JacksonConfig` | Dùng `@Configuration` + `ObjectMapper` bean → `shared-web` |

---

## 3. Chi tiết từng package con

### 3.1. `api/`

> "Hợp đồng công khai" mà module này cam kết với thế giới bên ngoài.

| Sub-package | Vai trò | Ví dụ |
|---|---|---|
| `ModuleNameFacade` (hoặc interface `ModuleNameService`) | Một entry point duy nhất để module khác gọi | `IamFacade`, `OrderFacade` |
| `dto/` | Request / Response object trao đổi với bên ngoài | `CreateUserRequest`, `UserResponse` |
| `events/` | Integration event mà module này **publish ra ngoài** | `UserRegisteredIntegrationEvent` |
| (root) | Public exception do module này tung ra | `UserNotFoundException` |

**Không được** chứa:
- Annotation Spring (`@RestController`, `@Repository`, `@Entity`).
- Class kế thừa `JpaRepository`, `Entity`, `KafkaTemplate`, v.v.

### 3.2. `core/`

> Trái tim nghiệp vụ — thuần Java, không framework.

| Sub-package | Vai trò | Ví dụ |
|---|---|---|
| `model/` | Entity + Value Object thuộc domain | `User`, `EmailAddress`, `Password` |
| `service/` | Domain service chứa business rule không thuộc về 1 entity | `PasswordPolicyService`, `UserRegistrationService` |
| `events/` | **Domain event nội bộ** (chỉ dùng trong module) | `UserPasswordChangedDomainEvent` |

**Không được** import:
- `org.springframework.web.*`, `jakarta.persistence.*`, `org.springframework.kafka.*`, `com.fasterxml.jackson.*`.
- Bất kỳ annotation framework nào.

### 3.3. `infrastructure/`

> Adapter kết nối domain với thế giới bên ngoài.

| Sub-package | Vai trò | Ví dụ |
|---|---|---|
| `persistence/` | JPA entity mapping + Repository impl | `UserJpaEntity`, `UserRepositoryImpl` |
| `web/` | REST controller, request/response mapping | `UserController`, `AuthController` |
| `messaging/` | Kafka producer/consumer, listener config | `UserEventListener`, `OrderEventPublisher` |
| (root) | External integration: mail, storage, third-party API | `MailSender`, `S3StorageClient` |

---

## 4. Quy tắc vàng (MUST / MUST NOT)

### MUST
1. **Mỗi module nghiệp vụ phải có `api` + `core` + `infrastructure`.**
2. **Cross-module chỉ gọi qua `Facade` interface** ở `api` — không gọi trực tiếp `infrastructure`.
3. **Domain event nội bộ** (trong `core/events/`) phải được `infrastructure/messaging` translate thành **integration event** (trong `api/events/`) trước khi publish ra ngoài.
4. **`shared-kernel`** chỉ chứa thứ KHÔNG thuộc domain (constant, util, common DTO). Không chứa entity nghiệp vụ.
5. **`shared-web`** chỉ chứa Spring-specific shared component (handler, filter, config). Không chứa business logic.
6. **Mỗi module có `pom.xml` riêng**, khai báo dependency rõ ràng vào parent POM và các module khác (nếu cần).

### MUST NOT
1. **`core` KHÔNG được import bất kỳ thứ gì từ `infrastructure` của chính nó.**
2. **`api` KHÔNG được chứa entity JPA, controller, repository.**
3. **Module khác KHÔNG được `@Autowire` trực tiếp vào class của `infrastructure` module khác** — chỉ inject `Facade`.
4. **`shared-kernel` KHÔNG được depend vào bất kỳ module nghiệp vụ nào** (kể cả qua vòng) và KHÔNG được import bất kỳ class từ `org.springframework.*`.
5. **`shared-web` KHÔNG được depend vào bất kỳ module nghiệp vụ nào** (kể cả qua vòng). Chỉ được depend `shared-kernel` + Spring framework.
6. **`bootstrap`** chỉ chứa `Application.java` + `application.yml` gọi các facade — không chứa business logic.

---

## 5. Workflow khi thêm module mới

```
1. Tạo folder modules/<tên-module>/
2. Tạo pom.xml (kế thừa parent, packaging = jar)
3. Tạo 3 package: api/, core/, infrastructure/
4. Định nghĩa facade interface ở api/ trước (TDD contract-first)
5. Implement core/ với business rule thuần Java
6. Implement infrastructure/ với JPA / REST / Kafka
7. Viết Spring @Configuration scan package com.example.<tên-module>
8. Build & test riêng module:  mvn -pl modules/<tên-module> -am test
9. Wire vào bootstrap module nếu cần expose HTTP
```

---

## 6. Lợi ích của cấu trúc này

| Lợi ích | Cái được |
|---|---|
| **Tách bạch kỹ thuật & nghiệp vụ** | `core` test được thuần Java, không cần Spring context |
| **Module hoá thật sự** | Có thể tách `modules/order` thành microservice riêng mà không phải rewrite |
| **Onboarding nhanh** | Người mới biết ngay "tôi cần sửa business → vào `core/`, sửa REST → vào `infrastructure/web/`" |
| **Build song song** | Maven multi-module cho phép incremental build (`mvn -pl modules/<name> -am`) |
| **Phụ thuộc một chiều** | Không sợ vòng lặp, không sợ "chỗ này sửa gãy chỗ kia" |

---

## 7. Tham chiếu nhanh

| Cần làm gì? | Vào đâu? |
|---|---|
| Thêm API endpoint mới | `infrastructure/web/` |
| Thêm field cho entity | `core/model/` + `infrastructure/persistence/` (mapping JPA) |
| Thêm business rule | `core/service/` |
| Thêm DTO mới | `api/dto/` |
| Publish event cho module khác | `api/events/` (integration event) + `infrastructure/messaging/` (publisher) |
| Listen event từ module khác | `infrastructure/messaging/` (listener) + `core/service/` (handler) |
| Thêm config chung | `src/main/resources/application.yml` hoặc `bootstrap/src/main/resources/` |
| Chia sẻ util / constant / exception / enum | `shared-kernel/` |
| Chia sẻ Spring handler / filter / config dùng chung | `shared-web/` |

---

## 8. Actual Dependency Graph (as of Sprint I1)

```
iam (module)
  └── outbox (module)  [publishes to Kafka topic notification.email.v1]
       └── kafka
            └── notification (module)  [Kafka consumer → Thymeleaf + Gmail SMTP]

Modules: shared-kernel → shared-web → bootstrap
```
