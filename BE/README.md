# PWB Mini Backend (BE)

Spring Boot 4.1 backend for the PWB Mini platform.

## Prerequisites

- Java 21 (LTS)
- Docker + Docker Compose (for PostgreSQL, Redis, MongoDB, Kafka, Elasticsearch)
- Maven (optional — `mvnw.cmd` wrapper included)

## Quick Start

```powershell
cd BE
pwsh .\scripts\generate-secrets.ps1      # creates .env with random secrets
copy .\src\main\resources\application-local.template.yaml .\src\main\resources\application-local.yaml
# edit application-local.yaml if needed
docker compose -f ..\docker-compose.yml up -d
.\mvnw.cmd spring-boot:run
```

App will be available at `http://localhost:8080`.

## Spring Profiles

| Profile | Purpose |
|---|---|
| `local` | Local development with debug logging and permissive CORS |
| `dev` | Shared dev environment (Swagger UI enabled, all actuator endpoints) |
| `prod` | Production (no Swagger UI, restricted actuator) |

Active profile is set via `SPRING_PROFILES_ACTIVE` env var (see `.env.example`).

## Project Structure

```
src/main/java/com/pwb/backend/
├── BackendApplication.java          # Spring Boot entry point
│
├── config/                          # Cross-cutting configuration beans
│   ├── filter/                      # Servlet filters (CorrelationIdFilter)
│   ├── DataSeeder.java              # Seed demo data on startup
│   ├── ElasticsearchConfig.java     # Elasticsearch client config
│   ├── JacksonConfig.java           # ObjectMapper customization
│   ├── JpaAuditorAware.java         # JPA auditing (createdBy, updatedBy)
│   ├── MailProperties.java          # @ConfigurationProperties for mail
│   ├── MessageSourceConfig.java     # i18n MessageSource setup
│   ├── OpenApiConfig.java           # Swagger / OpenAPI metadata
│   ├── OtpProperties.java          # @ConfigurationProperties for OTP rules
│   ├── RedisConfig.java             # Redis connection & serializer
│   ├── S3Config.java                # AWS S3 client config
│   ├── SeederProperties.java        # Data seeder toggle & settings
│   └── StorageProperties.java       # File storage settings
│
├── controller/                      # REST controllers (API endpoints)
│   └── AuthController.java
│
├── dto/                             # Data Transfer Objects
│   ├── request/                     # Inbound request payloads
│   └── response/                    # Outbound response payloads (ApiResponse, PageResponse, ...)
│
├── entity/                          # Persistence entities
│   ├── BaseEntity.java              # Shared auditing fields (id, createdAt, updatedAt, ...)
│   ├── rdbms/                       # JPA entities for PostgreSQL (User, Role, OutboxEvent)
│   └── elasticsearch/               # Elasticsearch document entities (reserved)
│
├── enums/                           # Shared enumerations (RoleName, UserStatus, OAuthProvider, OutboxStatus)
│
├── exception/                       # Error handling
│   ├── ErrorCode.java               # Centralized error code enum
│   ├── GlobalExceptionHandler.java  # @RestControllerAdvice for all exceptions
│   └── *Exception.java              # Custom exception classes (Business, BadRequest, NotFound, ...)
│
├── kafka/                           # Apache Kafka infrastructure
│   ├── config/                      # KafkaTopicConfig, KafkaConsumerConfig
│   ├── consumer/                    # Kafka listeners (reserved)
│   ├── event/                       # Event payload DTOs (reserved)
│   └── producer/                    # Kafka producers (reserved)
│
├── mapper/                          # MapStruct mappers (Entity ↔ DTO conversion)
│   └── AuthMapper.java
│
├── repository/                      # Spring Data repositories
│   ├── BaseRepository.java          # Shared generic repository interface
│   ├── rdbms/                       # JPA repositories for PostgreSQL
│   └── elasticsearch/               # Elasticsearch repositories (reserved)
│
├── scheduler/                       # Background scheduled jobs
│   └── OutboxRelayScheduler.java    # Polls outbox table and relays events to Kafka
│
├── security/                        # Spring Security layer
│   ├── config/                      # SecurityConfig, SecurityProperties
│   ├── filter/                      # JwtAuthenticationFilter
│   ├── jwt/                         # JwtTokenProvider (token generation & validation)
│   ├── AccessDeniedHandlerImpl.java # 403 handler
│   ├── AuthEntryPoint.java          # 401 handler
│   ├── CustomUserDetails.java       # UserDetails implementation
│   └── CustomUserDetailsService.java
│
├── service/                         # Business service interfaces
│   ├── AuthService.java
│   ├── AuthEventPublisher.java
│   ├── MailService.java
│   ├── OtpService.java
│   └── impl/                        # Service implementations & internal components
│       ├── AuthServiceImpl.java
│       ├── AuthEventPublisherImpl.java
│       ├── MailServiceImpl.java
│       ├── OtpServiceImpl.java
│       └── UserEventEmailRouter.java  # Kafka listener → routes events to MailService
│
├── utils/                           # Utility classes
│   ├── helper/                      # Helper beans (MessageHelper for i18n)
│   └── validation/                  # Custom Bean Validation
│       ├── PasswordPolicy.java      # Password policy constants
│       ├── annotation/              # Custom constraint annotations (@ValidPassword)
│       └── validator/               # ConstraintValidator implementations
│
└── websocket/                       # WebSocket / STOMP support
    ├── config/                      # WebSocketConfig (STOMP broker relay)
    ├── filter/                      # JwtHandshakeInterceptor (JWT auth on WS handshake)
    └── handler/                     # WebSocket message handlers (reserved)
```

### Package Conventions

| Package | Responsibility | Rules |
|---|---|---|
| `config/` | Spring `@Configuration`, `@ConfigurationProperties` | No business logic. Only wiring beans and reading properties. |
| `controller/` | REST API endpoints (`@RestController`) | Thin layer: validate input → delegate to `service` → return `ApiResponse`. No business logic. |
| `dto/request/` | Inbound request payloads | Immutable records or Lombok `@Data`. Carry validation annotations (`@NotBlank`, `@Valid`, etc.). |
| `dto/response/` | Outbound response payloads | Immutable records or Lombok `@Data`. No JPA annotations. |
| `entity/` | Persistence entities | JPA `@Entity` in `rdbms/`, Elasticsearch `@Document` in `elasticsearch/`. Must extend `BaseEntity` if applicable. |
| `enums/` | Shared enumerations | Pure enums, no Spring annotations. Used across all layers. |
| `exception/` | Error hierarchy + global handler | All custom exceptions extend `BusinessException`. `ErrorCode` is the single source of truth for error codes. |
| `kafka/` | Kafka infrastructure | Topic definitions, producer/consumer configs. Business Kafka listeners go in `service/impl/`. |
| `mapper/` | MapStruct interfaces | `@Mapper(componentModel = "spring")`. One mapper per domain aggregate. |
| `repository/` | Data access | `rdbms/` for JPA, `elasticsearch/` for ES. Extend `BaseRepository` or Spring Data interfaces. No business logic. |
| `scheduler/` | Background `@Scheduled` jobs | Infrastructure schedulers only (outbox relay, cleanup, etc.). Not business services. |
| `security/` | Authentication & authorization | JWT, filters, Security config. Self-contained — does not import from `service/`. |
| `service/` | Business service interfaces | Interfaces only at root level. All implementations go in `impl/`. |
| `service/impl/` | Service implementations & internal components | `@Service` / `@Component` classes. Kafka listeners that are part of business flow (e.g., `UserEventEmailRouter`) also live here. |
| `utils/` | Cross-cutting utilities | Stateless helpers (i18n, validation). No domain-specific logic. |
| `websocket/` | WebSocket / STOMP | Config, interceptors, message handlers. |

## Key Decisions

- **Spring Boot 4.1.0** + Java 21
- **springdoc-openapi 3.x** for Swagger UI (Spring Boot 4 compatible)
- **spring-dotenv** for `.env` loading via BOM
- **JJWT 0.12.6** for JWT (HS256)
- **MapStruct 1.5.5.Final** for DTO mapping
- **Lombok 1.18.46** for boilerplate reduction
- **Flyway** for DB migrations (`db/migration/V*.sql`)
- **i18n** via `MessageSource` + `messages*.properties` (fallback / en / vi)
- **Outbox pattern** for transactional event publishing to Kafka
- **Redis** for OTP storage with SHA-256 hashing + TTL

## Roles

The system defines 3 roles stored in the `roles` table:

| Role | Description |
|---|---|
| `USER` | Standard account with basic access (default role for new registrations) |
| `PRO`  | Professional account with premium features |
| `ADMIN` | System administrator with full access |

The Java enum `com.pwb.backend.enums.RoleName` provides type-safe access. Renaming the legacy `ARTIST` role to `PRO` is handled by Flyway migration `V3__rename_artist_role_to_pro.sql`.

## Authentication Flow

The `AuthController` (in `controller/`) exposes the following endpoints (all under `/api/v1/auth/**`, which is in the public allowlist):

| Endpoint | Method | Auth | Purpose |
|---|---|---|---|
| `/api/v1/auth/register`     | POST | public | Create account, send OTP email |
| `/api/v1/auth/verify-otp`   | POST | public | Verify 6-digit OTP, activate account, return tokens |
| `/api/v1/auth/resend-otp`   | POST | public | Resend verification code (60s cooldown, 5/day) |
| `/api/v1/auth/login`        | POST | public | Authenticate by email/password, return tokens |
| `/api/v1/auth/refresh`      | POST | public | Exchange refresh token for new access+refresh pair |
| `/api/v1/auth/logout`       | POST | Bearer | Audit logout (stateless) |
| `/api/v1/auth/complete-profile` | POST | Bearer | Set username + fullName after verification |

OTP rules (configurable in `application-dev.yaml` under `app.otp`):
- 6 digits
- TTL: 300 seconds
- Max attempts: 5
- Resend cooldown: 60 seconds
- Daily resend limit: 5

Password policy (`@ValidPassword`):
- 8-128 characters
- At least 1 uppercase, 1 lowercase, 1 digit, 1 special character (`!@#$%^&*`)

### End-to-end sequence

1. Client `POST /api/v1/auth/register` with `{ email, password }`.
2. Backend creates user (status `PENDING_VERIFICATION`, role `USER`), generates OTP, stores hash in Redis, writes `USER_REGISTERED_OTP` to outbox.
3. `OutboxRelayScheduler` picks up the event and publishes to Kafka topic `pwb.user.events`.
4. `UserEventEmailRouter` consumes the event and calls `MailServiceImpl` to send the verification email via Gmail SMTP.
5. Client `POST /api/v1/auth/verify-otp` with `{ userId, code }`.
6. Backend verifies OTP, activates user, returns `AuthResponse` with access + refresh tokens.
7. Client optionally calls `POST /api/v1/auth/complete-profile` (Bearer) to set `username` + `fullName`.

## Email (Gmail SMTP)

Configure the following env vars in `.env`:

```dotenv
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_gmail_app_password
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
```

To obtain a Gmail App Password:

1. Enable 2-Step Verification on your Google account.
2. Visit `https://myaccount.google.com/apppasswords` and create an App Password for "Mail".
3. Use the 16-character password as `MAIL_PASSWORD`.

## Security

- JWT access (15 min) + refresh (7 days) tokens, signed with HS256
- All authenticated endpoints require `Authorization: Bearer <token>`
- Public paths configured via `app.security.public-paths` in `application.yaml`
- WebSocket handshake validates JWT and stashes `userId` in session attributes
- `CustomUserDetailsService` is fully wired to `UserRepository` (role eager-loaded via `@EntityGraph`)

## Local Dev Tips

- Use `application-local.yaml` (gitignored) for secrets and overrides
- `data.seeder.enabled: true` seeds 3 demo users (`admin` / `user` / `pro`) on startup
- `actuator/health` is exposed publicly; other actuator endpoints require auth

## Out of Scope (future work)

- Multi-role JWT claim (currently single role)
- OAuth-only users without password (table separation)
- Auto-rotating secrets
- HikariCP connection pool tuning
- Redis cluster/sentinel config
- Elasticsearch index mappings
- Rate limiting implementation
- Integration tests
- CI/CD pipeline