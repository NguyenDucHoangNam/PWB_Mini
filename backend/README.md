# PWB MiNi Backend

Spring Boot 4.1.0 backend for the PWB MiNi platform.

## Quick Start (Local Development)

### 1. Prerequisites
- Java 21
- PostgreSQL on `:5433` (override via env)
- MongoDB on `:27017`
- Redis on `:6379`
- Kafka on `:9092`
- Optional: MinIO on `:9000` (S3-compatible storage)
- Optional: GeoIP City database at `classpath:geoip/GeoLite2-City.mmdb`

### 2. Provision environment file
A `.env` file holds every secret the app needs. **It is gitignored.**
Copy the template and edit the values:

```bash
cp .env.example .env
```

`JWT_SECRET` **must** be at least 32 bytes (256 bits). The application refuses
to start otherwise.

### 3. Run
```bash
./mvnw spring-boot:run
```

`spring-dotenv` automatically loads `.env` from the working directory. Real
production deployments should inject secrets via the orchestrator
(Kubernetes Secrets, AWS Secrets Manager, Vault, ...) and **not** ship a
`.env` file.

## Configuration

| File | Purpose |
| --- | --- |
| `application.yaml` | Profiles, config imports, app metadata |
| `application-shared.yaml` | Shared infra (DB, Redis, Kafka, Mail, Storage) |
| `application-iam.yaml` | IAM-specific (JWT, OTP, lockout, GeoIP, OAuth) |
| `application-liveroom.yaml` | Live-room module |
| `application-audio.yaml` | Audio/recording module |
| `application-prod.yaml` | Production profile overrides |

## Project Structure

```
com.pwb.backend
├── BackendApplication.java
├── controller/              REST controllers (one per business domain)
├── service/                 Business services (interface in service/, impl in service/impl/)
│   └── impl/                Service implementations
├── repository/              Spring Data JPA & Mongo repositories
├── model/                   JPA entities & Mongo documents (persistence models)
├── mapper/                  MapStruct mappers between DTOs and entities
├── dto/                     DTOs split by direction
│   ├── request/             Inbound payloads
│   └── response/            Outbound payloads (incl. ApiResponse<T> wrapper)
├── exception/               Global exception handler + ErrorCode enums
├── config/                  @Configuration beans (security, redis, kafka, swagger, etc.)
├── kafka/                   Kafka producers, consumers, event payloads
├── websocket/               WebSocket/STOMP configuration & event payloads
├── cache/                   Cache abstractions and decorators around Redis
├── elasticsearch/           Elasticsearch documents, repositories, services
├── constant/                Cross-cutting constants
└── util/                    Cross-cutting helpers (no business logic)
```

Architecture follows the classic Spring Boot layered pattern
(Controller → Service → Repository). Logical separation between business
domains (IAM, Live Room, Secure Audio Streaming) is maintained via package
naming and convention; module boundaries are not enforced at build time.

## Build & Test

```bash
./mvnw test              # run all tests
./mvnw verify            # full build with integration tests
```

Tests automatically pick up `src/test/resources/application-test.properties`
which provides safe defaults (CORS origin, JWT secret).

## Security Checklist

- [x] JWT secret enforced >= 32 bytes, fail-fast on missing
- [x] BCrypt cost factor 12
- [x] CORS allows explicit origins only (wildcard rejected)
- [x] IP-based rate limit on `/api/v1/auth/**`
- [x] Account lockout after N failed attempts
- [x] Refresh tokens are signed JWTs with `jti` for revocation
- [x] Token blacklist on logout
- [x] OAuth account linking requires password verification
- [x] Outbox pattern with retry + Dead-Letter Queue
- [x] `.env` files gitignored