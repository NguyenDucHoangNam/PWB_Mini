# Module Development Standards

Quy chuẩn code cho tất cả modules trong `pwb-refactor/Backend/`.

---

## 1. Cấu Trúc Module

```
modules/<module-name>/
├── src/main/java/com/pwb/<module>/
│   ├── api/                    # REST controllers + DTOs
│   │   ├── controller/
│   │   └── dto/
│   │       ├── request/
│   │       └── response/
│   ├── application/           # Use cases, facade
│   │   ├── usecase/
│   │   │   └── impl/
│   │   └── facade/
│   ├── domain/                # Entities, value objects, repository interfaces
│   │   ├── model/
│   │   ├── repository/
│   │   └── service/           # Domain service interfaces (ports)
│   └── infrastructure/        # Adapters, external integrations
│       ├── service/
│       │   └── impl/          # Adapter implementations
│       ├── persistence/
│       │   ├── entity/
│       │   ├── repository/
│       │   └── mapper/
│       └── config/
├── src/main/resources/
│   └── META-INF/
│       └── spring/
│           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── pom.xml
```

---

## 2. Naming Convention

### 2.1 Interface vs Implementation

| Layer | Interface | Implementation | Annotation |
|-------|----------|----------------|------------|
| Domain Service (Port) | `XxxService.java` | - | - |
| Infrastructure | - | `XxxAdapter.java` | `@Service` |

### 2.2 Specific Naming Patterns

| Loại | Đặt tên |
|------|---------|
| Rate Limiter + Cooldown | `ThrottlingServiceAdapter.java` |
| Token (JWT + Refresh + Blacklist) | `TokenManagerServiceAdapter.java` |
| Password/OTP generators | `XxxAdapter.java` |
| In-memory fallback | `XxxInMemoryAdapter.java` |
| Stub for test | `XxxStubAdapter.java` |
| Config Properties | `XxxConfig.java` (không dùng `XxxProperties`) |
| Use Case | `XxxUseCase.java` (interface), `XxxUseCaseImpl.java` (impl) |
| Facade | `XxxFacade.java` (interface), `XxxFacadeImpl.java` (impl) |

### 2.3 Class Name Patterns

| Pattern | Ví dụ |
|---------|-------|
| Use case | `CreateUserUseCase`, `CreateUserUseCaseImpl` |
| Facade | `UserFacade`, `UserFacadeImpl` |
| Domain Service Interface | `ThrottlingService`, `TokenManagerService` |
| Adapter | `ThrottlingServiceAdapter`, `TokenManagerServiceAdapter` |
| REST Controller | `UserController` |
| Request DTO | `CreateUserRequest`, `UpdateUserRequest` |
| Response DTO | `UserResponse`, `UserListResponse` |
| Domain Model | `User`, `Role`, `OtpCode` |
| JPA Entity | `UserEntity`, `RoleEntity` |
| Repository | `UserRepository`, `RoleRepository` |
| Mapper | `UserMapper` |

---

## 3. AutoConfiguration Pattern

### 3.1 Cấu trúc

```java
@AutoConfiguration
@ConditionalOnClass({ /* relevant class */ })
@EnableConfigurationProperties({ XxxConfig.class })
@ComponentScan("com.pwb.<module>")
public class <Module>AutoConfiguration {
}
```

### 3.2 File imports

```
# src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

com.pwb.<module>.config.<Module>AutoConfiguration
com.pwb.shared.config.SharedAutoConfiguration
com.pwb.shared.web.config.SharedWebAutoConfiguration
```

### 3.3 Không dùng `@ComponentScan` ở root

`PwbApplication` chỉ chứa `@SpringBootApplication`. Mọi module tự declare package scanning qua `ComponentScan` trong AutoConfiguration của mình.

---

## 4. Domain Layer Rules

### 4.1 Pure Domain

Domain layer chỉ chứa:
- Model classes (entities, value objects)
- Repository interfaces (ports)
- Domain service interfaces (ports)
- Enums, constants

**Không** chứa Spring annotations (`@Entity`, `@Table`, etc.) — chúng thuộc infrastructure layer.

### 4.2 Domain Model

```java
// ✅ Đúng - Domain model thuần
public class User {
    private final Long id;
    private final Email email;
    private boolean active;
}

// ❌ Sai - Domain model chứa JPA annotations
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue
    private Long id;
}
```

### 4.3 JPA Entity (Infrastructure)

```java
// ✅ Đúng - Entity chỉ trong infrastructure
@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
}
```

---

## 5. Application Layer Rules

### 5.1 Use Case

```java
// Interface
public interface CreateUserUseCase {
    UserResponse execute(CreateUserRequest request);
}

// Implementation
@Service
@RequiredArgsConstructor
public class CreateUserUseCaseImpl implements CreateUserUseCase {
    private final UserRepository userRepository;
    private final PasswordPolicyService passwordPolicy;

    @Override
    public UserResponse execute(CreateUserRequest request) {
        // business logic
    }
}
```

### 5.2 Facade

```java
// Interface
public interface UserFacade {
    void register(RegisterRequest request);
    UserResponse getById(Long id);
}

// Implementation
@Service
@RequiredArgsConstructor
public class UserFacadeImpl implements UserFacade {
    private final CreateUserUseCase createUser;
    private final GetUserUseCase getUser;

    @Override
    public void register(RegisterRequest request) {
        createUser.execute(toCreateRequest(request));
    }
}
```

---

## 6. Infrastructure Layer Rules

### 6.1 Adapter Pattern

```java
@Service
@RequiredArgsConstructor
public class ThrottlingServiceAdapter implements ThrottlingService {
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    // implement interface methods
}
```

### 6.2 Config Properties

```java
// ✅ Đúng - Dùng @ConfigurationProperties + @EnableConfigurationProperties
@ConfigurationProperties(prefix = "pwb.module")
public record ModuleConfig(
    String host,
    int port
) {}

// Trong AutoConfiguration:
@EnableConfigurationProperties(ModuleConfig.class)
```

### 6.3 Mapper

```java
@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(User user);
    User toDomain(UserEntity entity);
    UserEntity toEntity(User user);
}
```

---

## 7. API Layer Rules

### 7.1 Controller

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserFacade userFacade;

    @PostMapping
    @ResponseStatus(CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userFacade.create(request);
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id) {
        return userFacade.getById(id);
    }
}
```

### 7.2 Request DTO

```java
public record CreateUserRequest(
    @NotBlank @Email
    String email,
    @NotBlank @Size(min = 8)
    String password
) {}
```

### 7.3 Response DTO

```java
public record UserResponse(
    Long id,
    String email,
    String status,
    LocalDateTime createdAt
) {}
```

---

## 8. Package Dependencies

```
api/        → application/, domain/
application/→ domain/
infrastructure→ domain/, application/
```

**Không** có dependency ngược:
- Domain không phụ thuộc application hoặc infrastructure
- Application không phụ thuộc infrastructure

---

## 9. POM.xml Module

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 ...">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.pwb</groupId>
        <artifactId>pwb-refactor</artifactId>
        <version>${revision}</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>pwb-<module></artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <!-- Module dependencies -->
    </dependencies>
</project>
```

---

## 10. Checklist Trước Khi Commit

- [ ] Domain layer không chứa Spring annotations
- [ ] Infrastructure layer implement domain interfaces
- [ ] Adapter dùng annotation `@Service`
- [ ] Config dùng pattern `*Config.java`
- [ ] AutoConfiguration có `@ComponentScan`
- [ ] File `AutoConfiguration.imports` được tạo
- [ ] POM.xml kế thừa `pwb-refactor` parent
- [ ] Không có circular dependency giữa layers
