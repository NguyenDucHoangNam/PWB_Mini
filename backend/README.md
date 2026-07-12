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

## TTL & Rate-Limit Defaults

| Concern | Default | Source |
| --- | --- | --- |
| Access token TTL | 900 s (15 min) | `app.security.jwt.access-token-ttl-seconds` |
| Refresh token TTL | 604 800 s (7 days) | `app.security.jwt.refresh-token-ttl-seconds` |
| JWT blacklist skew | 30 s | `app.security.jwt.blacklist-clock-skew-buffer-seconds` |
| OTP TTL | 300 s (5 min) | `app.iam.otp.ttl-seconds` |
| OTP max attempts | 5 | `app.iam.otp.max-attempts` |
| OTP resend cooldown | 60 s | `app.iam.otp.resend-cooldown-seconds` |
| Password reset token TTL | 600 s (10 min) | `app.iam.password-reset.token-ttl-seconds` |
| Account deletion grace | 30 days | `app.iam.account-deletion.grace-days` |
| Failed-login attempts / user | 5 within 15 min | `app.login.max-failed-attempts` / `failed-attempt-window-seconds` |
| Failed-login attempts / IP | 20 within 5 min | `app.login.ip-max-failed-attempts` / `ip-attempt-window-seconds` |
| Anonymous login hash retention | 30 days | `LoginSuccessListener.LAST_LOGIN_TTL_DAYS` |
| Outbox payload encryption | AES-256-GCM, optional | `app.security.outbox.encryption-key` |
| Blacklist failure policy | close-by-default (configurable fail-open) | `app.security.jwt.blacklist-fail-closed` |
| Captcha | Cloudflare Turnstile, opt-in | `app.security.captcha.turnstile.*` |

## Environment Variables (Security-Sensitive)

| Variable | Required? | Notes |
| --- | --- | --- |
| `JWT_SECRET` | yes (prod) | min 32 bytes; HS256. App fails to start in `prod` profile if missing/short. |
| `JWT_REFRESH_SECRET` | recommended | Separate HS256 key for refresh tokens. |
| `GOOGLE_CLIENT_ID` | optional | Enables `/login/google`. |
| `IAM_OUTBOX_ENCRYPTION_KEY` | recommended | AES-256 raw key (base64 or arbitrary string → SHA-256 derived). Encrypts PII payloads at-rest. Leave blank to disable encryption (warns at boot). |
| `APP_SECURITY_CAPTCHA_TURNSTILE_ENABLED` | optional | Set `true` in prod to enforce Turnstile on public auth endpoints. |
| `APP_SECURITY_CAPTCHA_TURNSTILE_SECRET_KEY` | when captcha enabled | Server-side secret from Cloudflare dashboard. |
| `APP_SECURITY_CAPTCHA_TURNSTILE_SITE_KEY` | frontend | Publishable site key. |
| `APP_SECURITY_CAPTCHA_TURNSTILE_FAIL_OPEN` | optional | Default `true`: outages bypass captcha instead of locking everyone out. |
| `APP_SECURITY_TRUSTED_PROXIES_TRUST_FORWARDED_HEADERS` | optional | Set `true` only when running behind a known reverse proxy. |
| `APP_SECURITY_TRUSTED_PROXIES_CIDRS` | optional | Comma-separated CIDRs allowed to contribute XFF (e.g. `10.0.0.0/8,127.0.0.1/32`). |
| `APP_SECURITY_ACTUATOR_PUBLIC` | optional | Set `true` to expose actuator without auth. Keep `false` in prod. |

## Captcha (Cloudflare Turnstile)

Wire Turnstile on public sign-in endpoints when abuse spikes:

1. Pull site/secret keys from [Cloudflare Turnstile dashboard](https://dash.cloudflare.com/?to=/:account/turnstile).
2. Set `APP_SECURITY_CAPTCHA_TURNSTILE_ENABLED=true` and fill in the keys above.
3. Frontend renders the Turnstile widget and forwards the token as `captchaToken` on
   `POST /api/v1/auth/register`, `/login`, `/forgot-password`, `/resend-otp`.
4. With `fail-open=true`, a Turnstile outage (5xx, network timeout) lets requests through
   and logs a `WARN` line. Flip to `fail-open=false` for stricter environments.

## Trusted Proxies & X-Forwarded-For

`HttpClientContextResolver` honors `X-Forwarded-For` **only** when both
`app.security.trusted-proxies.trust-forwarded-headers=true` AND the socket's
remote address falls inside `app.security.trusted-proxies.cidrs`. Outside of
this allowlist the socket address is used directly, preventing client-controlled
header spoofing.