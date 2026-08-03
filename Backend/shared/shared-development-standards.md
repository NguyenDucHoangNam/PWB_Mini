# Shared Modules Development Standards

Quy chuẩn code cho tất cả shared modules trong `pwb-refactor/Backend/shared/`.

---

## 1. Cấu Trúc Shared

```
shared/
├── shared-kernel/      # POJO thuần: exception, DTO, util (không phụ thuộc Spring)
├── shared-web/         # Web infra: filter, exception handler, i18n, argument resolver
└── shared-infrastructure/  # Infra: Redis, Kafka, Storage, Mail, Outbox
```

---

## 2. Package Naming

| Module | Base Package |
|--------|-------------|
| shared-kernel | `com.pwb.shared` |
| shared-web | `com.pwb.web` |
| shared-infrastructure | `com.pwb.infra` |

**Lưu ý**: Package của shared infrastructure là `com.pwb.infra` (viết tắt), không phải `com.pwb.shared.infrastructure`.

---

## 3. shared-kernel

### 3.1 Nguyên tắc

- **Không phụ thuộc Spring** - là pure Java library
- **Không có @Configuration**, @Bean, @Component
- Chỉ chứa: POJO, enum, exception, utility classes
- Các module business (iam, order, notification) có thể import mà không lo vòng phụ thuộc

### 3.2 Cấu trúc

```
shared-kernel/src/main/java/com/pwb/shared/
├── domain/           # Base entity, abstract class
├── dto/             # ApiResponse, PageResponse, records
├── exception/       # BusinessException, ErrorCode, enum
└── util/            # Static utilities
```

### 3.3 Exception Pattern

```java
// ErrorCode enum - định nghĩa tất cả error codes
public enum ErrorCode {
    USER_NOT_FOUND("ERR_001", "User not found"),
    EMAIL_ALREADY_EXISTS("ERR_002", "Email already exists");

    private final String code;
    private final String defaultMessage;
}

// BusinessException - base exception
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, Object> metadata;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    public BusinessException(ErrorCode errorCode, Map<String, Object> metadata) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.metadata = metadata;
    }
}

// ValidationException - cho bean validation
public class ValidationException extends RuntimeException {
    private final Map<String, List<String>> errors;

    public ValidationException(Map<String, List<String>> errors) {
        super("Validation failed");
        this.errors = errors;
    }
}
```

### 3.4 DTO Pattern

```java
// ApiResponse - wrapper cho tất cả API response
public record ApiResponse<T>(
    boolean success,
    T data,
    ErrorInfo error,
    String traceId
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> error(ErrorInfo error) {
        return new ApiResponse<>(false, null, error, null);
    }
}

// ErrorInfo
public record ErrorInfo(
    String code,
    String message,
    Map<String, Object> metadata
) {}

// PageResponse - cho paginated list
public record PageResponse<T>(
    List<T> items,
    int page,
    int size,
    long totalItems,
    int totalPages
) {}
```

### 3.5 Domain Base Entity

```java
// DomainBaseEntity - base cho tất cả domain models (KHÔNG phải JPA entity)
public abstract class DomainBaseEntity {
    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
```

---

## 4. shared-web

### 4.1 Nguyên tắc

- **Chứa Spring Web infrastructure**
- Filter, Exception Handler, Argument Resolver, i18n
- Package: `com.pwb.web`
- Các module import được, nhưng **không circular dependency**

### 4.2 Cấu trúc

```
shared-web/src/main/java/com/pwb/web/
├── config/              # AutoConfiguration, WebMvcConfig, MessageSource
├── exception/           # GlobalExceptionHandler, WebErrorMapper
├── filter/              # CorrelationIdFilter, các filters
├── message/             # MessageResolver
└── security/            # CurrentUser, argument resolvers
```

### 4.3 GlobalExceptionHandler Pattern

```java
@AutoConfiguration
@EnableConfigurationProperties(MessageSourceConfig.class)
public class WebAutoConfiguration {
    @Bean
    public GlobalExceptionHandler globalExceptionHandler(MessageSource messageSource) {
        return new GlobalExceptionHandler(messageSource);
    }
}

// GlobalExceptionHandler
public class GlobalExceptionHandler {
    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<?> handleBusinessException(BusinessException ex) {
        ErrorInfo error = new ErrorInfo(
            ex.getErrorCode().getCode(),
            resolveMessage(ex),
            ex.getMetadata()
        );
        return ApiResponse.error(error);
    }
}
```

### 4.4 Argument Resolver Pattern

```java
// Annotation
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentUser {}

// Resolver
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
            && parameter.getParameterType().equals(AuthenticatedUser.class);
    }

    @Override
    public AuthenticatedUser resolveArgument(...) {
        // extract from SecurityContext or token
    }
}
```

### 4.5 Filter Pattern

```java
// CorrelationIdFilter - extract/generate correlation ID
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
```

---

## 5. shared-infrastructure

### 5.1 Nguyên tắc

- **Chứa infrastructure adapters** cho external services
- Package: `com.pwb.infra`
- Mỗi infrastructure (Redis, Kafka, Mail, Storage, Outbox) có cấu trúc riêng
- Dùng Outbox pattern cho event publishing

### 5.2 Cấu trúc chuẩn

```
shared-infrastructure/src/main/java/com/pwb/infra/
├── config/              # AutoConfiguration (InfraAutoConfiguration)
├── redis/
│   ├── config/
│   ├── constant/
│   └── service/
├── kafka/
│   ├── config/
│   ├── producer/
│   └── consumer/
├── mail/
│   ├── api/              # EmailPayload, EmailTemplate enums
│   ├── config/
│   ├── consumer/
│   ├── renderer/
│   └── properties/
├── outbox/
│   ├── api/              # OutboxEnqueueHelper, OutboxWriter interface
│   ├── config/
│   ├── core/             # OutboxStatus enum
│   ├── persistence/      # JPA entity, repository
│   ├── scheduler/
│   ├── sink/             # Publisher interface, Kafka impl
│   └── properties/
└── storage/
    ├── api/              # StorageService interface
    ├── config/
    ├── dto/              # UploadResult, PresignedUrlResult
    ├── exception/
    ├── impl/             # S3StorageServiceImpl
    ├── properties/
    └── util/
```

### 5.3 AutoConfiguration Pattern

```java
@AutoConfiguration
@ConditionalOnClass({/* relevant class */})
@EnableConfigurationProperties({
    RedisConfigProperties.class,
    KafkaProperties.class,
    MailProperties.class
})
public class InfraAutoConfiguration {
}
```

### 5.4 Storage Service Pattern

```java
// Interface - KHÔNG dùng tên "*Adapter"
public interface StorageService {
    UploadResult upload(InputStream input, String filename, String contentType);
    PresignedUrlResult generatePresignedUrl(String objectKey);
    void delete(String objectKey);
    ObjectMetadata getMetadata(String objectKey);
}

// Implementation - đặt tên theo loại
public class S3StorageServiceImpl implements StorageService { }
```

**Lưu ý**: Project hiện chỉ hỗ trợ S3 — không còn LocalStorage. Mọi môi trường (dev/prod) đều dùng S3 (MinIO local cho dev, AWS S3 thật cho prod).

### 5.5 Outbox Pattern

```java
// OutboxEnqueueHelper - helper để enqueue event
public class OutboxEnqueueHelper {
    private final OutboxWriter outboxWriter;

    public void enqueue(String aggregateType, String aggregateId,
                        String eventType, Object payload) {
        OutboxEnqueueRequested request = new OutboxEnqueueRequested(
            aggregateType, aggregateId, eventType, payload, Instant.now()
        );
        outboxWriter.write(request);
    }
}

// OutboxWriter - interface
public interface OutboxWriter {
    void write(OutboxEnqueueRequested request);
}

// Implementations
public class OutboxJpaWriter implements OutboxWriter { }
public class LoggingOutboxWriter implements OutboxWriter { }
```

---

## 6. AutoConfiguration.imports

### 6.1 Vị trí file

```
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 6.2 Nội dung

```
# shared-infrastructure
com.pwb.infra.config.InfraAutoConfiguration

# shared-web
com.pwb.web.config.WebAutoConfiguration
```

---

## 7. POM Dependencies

### 7.1 shared-kernel

```xml
<!-- Không phụ thuộc Spring - chỉ có JSR-305, Lombok -->
<dependencies>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>com.google.code.findbugs</groupId>
        <artifactId>jsr305</artifactId>
    </dependency>
</dependencies>
```

### 7.2 shared-web

```xml
<dependencies>
    <dependency>
        <groupId>com.pwb</groupId>
        <artifactId>shared-kernel</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-webmvc</artifactId>
    </dependency>
    <dependency>
        <groupId>jakarta.validation</groupId>
        <artifactId>jakarta.validation-api</artifactId>
    </dependency>
</dependencies>
```

### 7.3 shared-infrastructure

```xml
<dependencies>
    <dependency>
        <groupId>com.pwb</groupId>
        <artifactId>shared-kernel</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-mail</artifactId>
    </dependency>
</dependencies>
```

---

## 8. Checklist Trước Khi Commit

### shared-kernel
- [ ] Không có import Spring
- [ ] Không có @Configuration, @Component, @Bean
- [ ] ErrorCode enum có đầy đủ codes
- [ ] BusinessException có constructor với metadata
- [ ] ApiResponse có static factory methods

### shared-web
- [ ] GlobalExceptionHandler xử lý tất cả exception types
- [ ] Argument resolvers có supportsParameter check
- [ ] Filters extend OncePerRequestFilter
- [ ] MessageSource được cấu hình

### shared-infrastructure
- [ ] Interface đặt tên rõ ràng (StorageService, không phải StorageAdapter)
- [ ] Implementation có prefix theo loại (S3*, Local*, Kafka*)
- [ ] AutoConfiguration có @EnableConfigurationProperties
- [ ] File AutoConfiguration.imports được tạo
