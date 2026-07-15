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
├── BackendApplication.java
├── common/i18n/         # MessageHelper for i18n-aware responses
├── config/              # Cross-cutting configs (CORS, Jackson, Redis, Kafka, etc.)
├── config/filter/       # Servlet filters (CorrelationIdFilter)
├── dto/response/        # API response DTOs (ApiResponse, PageResponse)
├── entity/              # BaseEntity + per-store packages (rdbms, mongo, search)
├── enums/               # Project enums
├── exception/           # Custom exceptions + GlobalExceptionHandler + ErrorCode
├── kafka/               # Kafka producer/consumer config
├── repository/          # Spring Data repositories
├── security/            # Security config, JWT, filters
└── websocket/           # WebSocket/STOMP config
```

## Key Decisions

- **Spring Boot 4.1.0** + Java 21
- **springdoc-openapi 3.x** for Swagger UI (Spring Boot 4 compatible)
- **spring-dotenv** for `.env` loading via BOM
- **JJWT 0.12.6** for JWT (HS256)
- **MapStruct 1.5.5.Final** for DTO mapping
- **Lombok 1.18.46** for boilerplate reduction
- **Flyway** for DB migrations (`db/migration/V*.sql`)
- **i18n** via `MessageSource` + `messages*.properties` (fallback / en / vi)

## Security

- JWT access (15 min) + refresh (7 days) tokens, signed with HS256
- All authenticated endpoints require `Authorization: Bearer <token>`
- Public paths configured via `app.security.public-paths` in `application.yaml`
- WebSocket handshake validates JWT and stashes `userId` in session attributes
- `CustomUserDetailsService.loadUserById` is currently a **stub** (TODO) — the IAM module is being redesigned

## Local Dev Tips

- Use `application-local.yaml` (gitignored) for secrets and overrides
- `data.seeder.enabled: true` seeds 3 demo users (admin / user / artist) on startup
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