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
├── iam/                  Identity & Access Management module
│   ├── api/              DTOs and events (public API)
│   └── internal/         controllers, services, repositories
├── notification/         Email delivery module (Kafka consumer)
├── liveroom/             Live-room module
├── audio/                Audio/recording module
├── shared/               Cross-cutting (security, storage, exceptions, response)
└── BackendApplication.java
```

Module boundaries are enforced by Spring Modulith and ArchUnit tests
(`ModulithArchitectureTests`, `ArchitectureConventionsTests`).

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