# Voice Module — Implementation Tasks

> **Ngày tạo**: 2026-07-19
> **Dựa trên**: [voice-module-plan.md](./voice-module-plan.md)
> **Trạng thái**: TASK 0-13 Done · TASK 14+ Pending
> **Quy ước đánh dấu**: `[ ]` chưa làm · `[x]` đã xong · `[~]` đang làm

---

## Tổng quan nghiệp vụ

| Nghiệp vụ | Chi tiết |
|---|---|
| Voice tag | User tự tạo watermark riêng (TTS hoặc upload audio) |
| Song source | Upload từ máy (MP3, WAV, FLAC) |
| TTS provider | Google Cloud TTS |
| Processing | User tùy chọn — không chọn tag thì không chèn |
| Multiple tags | 1 voice tag cho mỗi bài hát |
| Storage | Giữ cả original + processed |
| Access control | Chỉ PRO users (`@PreAuthorize("hasRole('PRO')")`) |
| Audio format | Giữ nguyên format gốc |
| FFmpeg | Jaffree (Java wrapper) |
| Playback | Streaming only (presigned URL 1h) |
| TTS cache | Redis (Spring Cache + Redis) |
| Async processing | Outbox + Kafka |
| Audio validation | Extension whitelist + magic byte + size + duration |
| Preview | Sau khi lưu mới nghe được |

---

## Module Structure (mục tiêu)

```
Backend/
├── shared-kernel/        # ✅ Đã có
├── shared-web/           # ✅ Đã có
├── shared-storage/       # ✅ TASK 1 Done
├── bootstrap/            # ✅ Đã có
└── modules/
    ├── iam/              # ✅ Đã có (TASK 0 Done - JwtFilter fix)
    ├── notification/     # ✅ Đã có
    ├── outbox/           # ✅ Đã có (refactor aggregateType)
    └── voice/            # ✅ TASK 2-9 Done · TASK 10+ Pending
```

**Voice module progress**:
- ✅ TASK 2 — Module skeleton
- ✅ TASK 3 — DB migrations (V8)
- ✅ TASK 4 — Error codes + i18n
- ✅ TASK 5 — Domain models (VoiceTag, JPA entity, mapper, repository)
- ✅ TASK 6 — DTOs (CreateTts / Upload / Update requests + Response)
- ✅ TASK 7 — Google TTS service + Redis cache
- ✅ TASK 8 — Service + Facade + AudioFileValidator + FFprobe wrapper
- ✅ TASK 9 — Voice tag REST controller (PRO role check + shared-web `@CurrentUser` abstraction)
- ✅ TASK 10 — Song domain models + repositories (9 files)
- ✅ TASK 11 — Song DTOs + i18n validation keys (7 files + 14 keys × 3 locales)
- ✅ TASK 12 — Song service + facade (4 files)
- ✅ TASK 13 — Song REST controller (13 endpoints, HTTP 302 streaming redirect)

---

## ✅ [x] TASK 0: Fix JwtAuthenticationFilter (PREREQUISITE) — Code done, smoke test pending

**Mục tiêu**: Enforce `UserStatus` trong JWT filter — chặn BANNED/DELETED user dùng access token cũ.

**Bối cảnh**: Hiện tại `JwtAuthenticationFilter` chỉ verify signature/expiration mà KHÔNG check `UserStatus`. Nếu user bị BAN nhưng token còn hạn 14 phút → vẫn dùng được.

**Files cần update**:

- [x] `Backend/modules/iam/src/main/java/com/pwb/iam/infrastructure/security/filter/JwtAuthenticationFilter.java` — sau khi load user từ DB, check `user.getStatus() == ACTIVE`, nếu không thì clear `SecurityContext` và throw `BusinessException(UNAUTHORIZED)`.

**Compile check**: `mvn -pl modules/iam -am clean compile` → BUILD SUCCESS (98 source files, 0 ERROR, 0 WARNING) ✅

**Test thủ công**:

- [x] Login user → lấy access token → set status = BANNED → gọi endpoint bất kỳ → phải trả 401.

> **Note**: Code đã implement và compile pass. Smoke test chưa chạy thực tế trên running app (sếp confirm bỏ qua). Cần verify manually trước khi ship voice.

**Tại sao là prerequisite**: Voice module dùng `@PreAuthorize("hasRole('PRO')")` nhưng đó chỉ check role, không check status. Nếu user bị BAN mà còn role PRO → vẫn pass authz.

---

## ✅ [x] TASK 1: Setup shared-storage Module

**Mục tiêu**: Tạo module dùng chung cho S3/Local storage abstraction.

### Files cần tạo

- [x] `Backend/shared-storage/pom.xml` (deps: shared-kernel, AWS SDK s3 + s3-transfer-manager + auth, lombok)
- [x] `Backend/shared-storage/src/main/java/com/pwb/storage/api/StorageService.java` (interface full feature)
- [x] `Backend/shared-storage/src/main/java/com/pwb/storage/api/StorageException.java` (extends `BaseBusinessException`)
- [x] `Backend/shared-storage/src/main/java/com/pwb/storage/api/dto/UploadResult.java`
- [x] `Backend/shared-storage/src/main/java/com/pwb/storage/api/dto/PresignedUrlResult.java`
- [x] `Backend/shared-storage/src/main/java/com/pwb/storage/api/dto/ObjectMetadata.java`
- [x] `Backend/shared-storage/src/main/java/com/pwb/storage/infrastructure/config/StorageProperties.java` (`@ConfigurationProperties("app.storage")`)
- [x] `Backend/shared-storage/src/main/java/com/pwb/storage/infrastructure/config/StorageProviderType.java` (enum `S3, LOCAL`)
- [x] `Backend/shared-storage/src/main/java/com/pwb/storage/infrastructure/config/StorageAutoConfig.java` (`@Configuration` + `@ConditionalOnProperty`)
- [x] `Backend/shared-storage/src/main/java/com/pwb/storage/infrastructure/impl/S3StorageServiceImpl.java` (`@ConditionalOnProperty(provider=S3)`)
- [x] `Backend/shared-storage/src/main/java/com/pwb/storage/infrastructure/impl/LocalStorageServiceImpl.java` (`@ConditionalOnProperty(provider=LOCAL)`)
- [x] `Backend/shared-storage/src/main/java/com/pwb/storage/infrastructure/util/MediaTypeUtils.java` (magic byte detection)

### Files cần update

- [x] `Backend/pom.xml` (parent) — thêm `<module>shared-storage</module>`
- [x] `Backend/bootstrap/pom.xml` — thêm dependency `shared-storage`
- [x] `Backend/shared-kernel/src/main/java/com/pwb/backend/exception/ErrorCode.java` — thêm các code storage:

```java
// Storage Module (prefix: STORAGE_)
STORAGE_UPLOAD_FAILED    ("STORAGE_001", "Storage upload failed.",          500),
STORAGE_DOWNLOAD_FAILED  ("STORAGE_002", "Storage download failed.",        500),
STORAGE_OBJECT_NOT_FOUND ("STORAGE_003", "Storage object not found.",       404),
STORAGE_DELETE_FAILED    ("STORAGE_004", "Storage delete failed.",          500),
STORAGE_PRESIGN_FAILED   ("STORAGE_005", "Storage presign URL failed.",     500),
STORAGE_INVALID_KEY      ("STORAGE_006", "Invalid storage key format.",     400);
```

### Config cần thêm

- [x] `Backend/bootstrap/src/main/resources/application.yml` — section `app.storage.*` (xem `voice-module-plan.md` §10.1)

### Messages cần thêm (i18n)

- [x] `Backend/shared-web/src/main/resources/messages/messages.properties` — add `STORAGE_001..006`
- [x] `Backend/shared-web/src/main/resources/messages/messages_en.properties` — same
- [x] `Backend/shared-web/src/main/resources/messages/messages_vi.properties` — Vietnamese translation

### Implementation notes

- **Interface full feature** (10 methods): upload (2 overloads), download, delete, deleteAll, exists, getMetadata, generatePresignedUrl, generatePresignedUploadUrl.
- **S3 impl**: dùng `S3Client` (sync) + `S3TransferManager` cho multipart upload > 25MB.
- **Local impl**: dùng `java.nio.file.Files` + presigned URL trả về local HTTP endpoint (chỉ dùng dev/test).
- **Retry**: wrap upload/download với Spring Retry (max 3 attempts, exponential backoff 1s/3s/10s).
- **Error mapping**: `S3Exception` → `StorageException(STORAGE_UPLOAD_FAILED, e)`.

### Definition of Done

- [x] `mvn -pl shared-storage -am clean compile` pass
- [x] Switch `app.storage.provider=LOCAL` → chạy được không cần S3 credentials

> **Note**: Test endpoint dev (q2) đã được quyết định KHÔNG tạo trong TASK này (đợi voice module TASK 2 mới smoke test end-to-end).

---

## ✅ [x] TASK 2: Setup Voice Module Structure

**Mục tiêu**: Tạo voice module với dependencies, config, security, packaging.

### Files cần tạo

- [x] `Backend/modules/voice/pom.xml` (deps: shared-kernel, shared-web, shared-storage, iam, outbox, starter-web, starter-data-jpa, starter-cache, starter-validation, spring-kafka, google-cloud-texttospeech, jaffree, lombok, mapstruct)
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/config/VoiceJpaConfig.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/config/VoiceProperties.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/config/TtsProperties.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/config/AudioProcessingProperties.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/config/VoiceSecurityConfig.java` (`@EnableMethodSecurity`)

### Files cần update

- [x] `Backend/pom.xml` (parent) — đảm bảo `<module>modules/voice</module>` đã có (skeleton)
- [x] `Backend/bootstrap/pom.xml` — thêm dependency `voice`
- [x] `Backend/bootstrap/src/main/resources/application.yml` — section `app.voice.*` (xem plan §10.1)

### Implementation notes

- Voice module là module đầu tiên dùng `shared-storage` → đảm bảo wiring qua constructor injection.
- Voice module KHÔNG inject `JwtAuthenticationFilter` hay `CustomUserDetails` trực tiếp từ IAM — chỉ cần `@AuthenticationPrincipal` + `Authentication.getAuthorities()`.

---

## ✅ [x] TASK 3: Create Database Migrations

**Mục tiêu**: Tạo bảng `voice_voice_tags`, `voice_songs`, `voice_song_tag_configs` + indexes.

### Files cần tạo

- [x] `Backend/bootstrap/src/main/resources/db/migration/V8__create_voice_tables.sql`

### SQL content

Xem chi tiết schema trong `voice-module-plan.md` §9.1. Tóm tắt:

- [x] 3 tables với audit fields (`created_at`, `updated_at`, `created_by`, `updated_by`, `deleted`, `deleted_at`, `version`)
- [x] FK constraints (`ON DELETE RESTRICT` cho user_id, `ON DELETE CASCADE` cho song_id trong config)
- [x] UNIQUE constraints (`(user_id, name)` cho tags, `(song_id)` cho config)
- [x] CHECK constraints (interval > 0, volume 0-100, fade >= 0)
- [x] 6 indexes

### Implementation notes

- Migration follow pattern V1-V7 (PostgreSQL syntax: `gen_random_uuid()`, `TIMESTAMPTZ`).
- Comment trong SQL bằng `--` để giải thích constraint.

---

## ✅ [x] TASK 4: Voice Error Codes & Messages

**Mục tiêu**: Thêm error codes cho voice module vào enum tập trung + i18n.

### Files cần update

- [x] `Backend/shared-kernel/src/main/java/com/pwb/backend/exception/ErrorCode.java` — thêm 13 codes sau:

```java
// Voice Module (prefix: VOICE_)
VOICE_TAG_NOT_FOUND       ("VOICE_001", "Voice tag not found.",                  404),
SONG_NOT_FOUND            ("VOICE_002", "Song not found.",                       404),
INVALID_AUDIO_FORMAT      ("VOICE_003", "Invalid audio format.",                 400),
FILE_TOO_LARGE            ("VOICE_004", "File size exceeds maximum allowed.",    413),
TTS_GENERATION_FAILED     ("VOICE_005", "Text-to-speech generation failed.",     500),
AUDIO_PROCESSING_FAILED   ("VOICE_006", "Audio processing failed.",              500),
INVALID_INTERVAL          ("VOICE_007", "Invalid interval value.",               400),
DUPLICATE_VOICE_TAG_NAME  ("VOICE_008", "Voice tag name already exists.",        409),
SONG_NOT_READY            ("VOICE_009", "Song is not ready for streaming.",      400),
ACCESS_DENIED_PRO_ONLY    ("VOICE_010", "This feature is available for PRO only.",403),
VOICE_TAG_IN_USE          ("VOICE_011", "Voice tag is currently in use.",        409),
SONG_ALREADY_PROCESSED    ("VOICE_012", "Song has already been processed.",      409),
INVALID_AUDIO_DURATION    ("VOICE_013", "Audio duration exceeds maximum.",       400);
```

### Files cần update (i18n)

- [x] `Backend/shared-web/src/main/resources/messages/messages.properties` — add `VOICE_001..013` + success messages
- [x] `Backend/shared-web/src/main/resources/messages/messages_en.properties` — same
- [x] `Backend/shared-web/src/main/resources/messages/messages_vi.properties` — Vietnamese translation

### Success messages cần thêm

```properties
VOICE_TAG_CREATED=Voice tag created successfully.
VOICE_TAG_UPDATED=Voice tag updated successfully.
VOICE_TAG_DELETED=Voice tag deleted successfully.
SONG_UPLOADED=Song uploaded successfully.
SONG_UPDATED=Song updated successfully.
SONG_DELETED=Song deleted successfully.
VOICE_TAG_CONFIGURED=Voice tag configured successfully.
VOICE_PROCESSING_STARTED=Voice tag processing started.
```

### Implementation notes

- **KHÔNG tạo `VoiceErrorCode` riêng** — bám sát convention central enum.
- Error code `VOICE_001..013` đã được list trong plan cũ, giữ nguyên để dễ reference.

---

## ✅ [x] TASK 5: Voice Tag Domain Models

**Mục tiêu**: Tạo domain entities + JPA entities + mappers + repositories cho Voice Tag.

### Files cần tạo

- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/api/enums/VoiceTagType.java` (TTS, UPLOADED)
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/entity/BaseEntity.java` (auditable base, copy pattern từ `IamJpaBaseEntity`)
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/core/model/VoiceTag.java` (immutable domain object với factory methods)
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/entity/VoiceTagJpaEntity.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/mapper/VoiceTagMapper.java` (MapStruct)
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/repository/VoiceTagJpaRepository.java`

> **Note điều chỉnh scope**: Plan `voice-module-plan.md` §9.1 + plan TASK 5 ban đầu định nghĩa `voice_voice_tags` có thêm 4 columns (`format`, `voice_name`, `speaking_rate`, `pitch`). Tuy nhiên **SQL V8 (TASK 3) KHÔNG tạo 4 columns này** → lúc chạy runtime Hibernate schema validation fail với `missing column [format]`. Em đã chọn **Hướng A: sửa code cho khớp SQL** (DB là source of truth, an toàn cho MVP, không vi phạm Flyway best practice). Hậu quả:
> - Enum `AudioFormat` (3 values MP3/WAV/FLAC) → xóa (không còn field reference trong DB).
> - Domain `VoiceTag`: bỏ 4 fields (`voiceName`, `speakingRate`, `pitch`); `format` cũng không bao giờ có trong domain (đã fix bug "AudioFormatPlaceholder" của plan).
> - TTS custom params (`voiceName`/`speakingRate`/`pitch`) sẽ dùng default từ `TtsProperties` (TASK 7) — không cho user override ở MVP.
> - DB schema đơn giản hơn: 13 columns thay vì 17.

### Implementation notes (final)

- BaseEntity: 8 fields (id, createdAt, updatedAt, createdBy, updatedBy, deleted, deletedAt, version) + `markDeleted(deletedBy)` + `markActive()` — copy y nguyên pattern `IamJpaBaseEntity`.
- VoiceTag domain: 12 fields (id, userId, name, description, tagType, sourceText, languageCode, s3Key, durationSeconds, fileSizeBytes, isDefault + readonly getters), 3 factory methods (`createTtsTag`, `createUploadedTag`, `rehydrate`) + 4 update methods (`updateMetadata`, `updateTtsParams`, `markDefault`, `unmarkDefault`).
- VoiceTagJpaEntity: 10 fields riêng + extends BaseEntity (8 audit fields), table `voice_voice_tags`, 2 indexes + 1 unique constraint.
- VoiceTagMapper: 3 default methods (`toEntity(domain)`, `toEntity(domain, existing)`, `toDomain(entity)`).
- VoiceTagJpaRepository: 6 methods (findByIdAndDeletedFalse, findByUserId paginated, findByUserIdAndTagType, existsByUserIdAndName, existsByIdAndUserId, countByUserId).

### Repository method bắt buộc (ownership check)

```java
public interface VoiceTagJpaRepository extends JpaRepository<VoiceTagJpaEntity, UUID> {
    Optional<VoiceTagJpaEntity> findByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);
    List<VoiceTagJpaEntity> findByUserIdAndDeletedFalse(UUID userId, Pageable pageable);
    List<VoiceTagJpaEntity> findByUserIdAndTagTypeAndDeletedFalse(UUID userId, VoiceTagType type);
    boolean existsByUserIdAndNameAndDeletedFalse(UUID userId, String name);
    boolean existsByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);
    long countByUserIdAndDeletedFalse(UUID userId);
}
```

### Implementation notes

- `VoiceTag` domain object: immutable, có factory method `createTtsTag(...)`, `createUploadedTag(...)`, `rehydrate(...)`.
- Mapper dùng MapStruct `componentModel = "spring"`, `ReportingPolicy.IGNORE`.
- Soft-delete qua field `deleted` + `deletedAt` (pattern giống IAM).
- Optimistic lock qua `@Version`.

---

## ✅ [x] TASK 6: Voice Tag DTOs

**Mục tiêu**: Tạo request/response DTOs.

### Files cần tạo

- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/request/CreateTtsVoiceTagRequest.java`

```java
public class CreateTtsVoiceTagRequest {
    @NotBlank @Size(max = 128) private String name;
    @Size(max = 512) private String description;
    @NotBlank @Size(max = 4000) private String text;
    @NotBlank @Pattern(regexp = "^[a-z]{2}-[A-Z]{2}$") private String languageCode;
}
```

- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/request/UpdateVoiceTagRequest.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/request/UploadVoiceTagRequest.java` (metadata-only: name + description — file upload qua `@RequestParam` ở controller)
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/response/VoiceTagResponse.java`

### Validation

- Dùng key i18n: `{validation.name.required}`, `{validation.text.maxlength}`, etc.
- KHÔNG hardcode message trong annotation.

### Implementation notes (final)

- Validation keys (7 keys) đã được add vào cả 3 file `messages*.properties`:
  - `validation.name.required`, `validation.name.maxlength`, `validation.description.maxlength`
  - `validation.text.required`, `validation.text.maxlength`
  - `validation.languagecode.required`, `validation.languagecode.pattern`
- `UploadVoiceTagRequest` chỉ có 2 metadata fields (name + description) — file multipart upload qua `@RequestParam("file") MultipartFile` parameter riêng ở controller (TASK 9). Audio format được validate qua extension whitelist ở service layer (TASK 8) thay vì DTO.
- `VoiceTagResponse` KHÔNG có `s3Key` field (sensitive — chỉ trả presigned URL qua endpoint riêng). Cũng không có 4 fields TTS custom (`voiceName`, `speakingRate`, `pitch`, `format`) vì DB schema V8 không lưu.
- Tất cả DTO dùng `@Data + @Builder + @NoArgsConstructor + @AllArgsConstructor` (Lombok convention).
- `mvn -pl modules/voice -am clean compile` → BUILD SUCCESS.
- `mvn -pl bootstrap -am clean compile` → BUILD SUCCESS.

---

## TASK 7: Google TTS Service

**Mục tiêu**: Tích hợp Google Cloud Text-to-Speech với Redis cache.

### Files cần tạo

- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/TextToSpeechService.java` (interface)
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/tts/GoogleTtsServiceImpl.java` (`@Service`, `@Cacheable`)
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/config/TextToSpeechClientConfig.java` (bean `TextToSpeechClient`)

### Files cần update

- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/config/VoiceSecurityConfig.java` — thêm `@EnableCaching`

### Features

- `synthesize(text, languageCode) → byte[]` — chỉ 2 params (theo điều chỉnh scope Hướng A, TTS custom params không expose cho user ở MVP; voiceName/speakingRate/pitch dùng default từ `TtsProperties`).
- Cache qua `@Cacheable(value = "ttsCache", key = "#root.target.cacheKey(#text, #languageCode)")`.
- Cache key: `SHA-256(text + "|" + languageCode + "|" + defaultVoiceName + "|" + speakingRate + "|" + pitch)` — encode hex (64 chars), an toàn cho UTF-8.
- TTL: 7 days (global `spring.cache.redis.time-to-live=604800000ms` = 168h, match `ttsProperties.cacheTtlHours=168`).
- Default values (từ `TtsProperties`): `languageCode=en-US`, `voiceName=en-US-Standard-A`, `speakingRate=1.0`, `pitch=0.0`.
- AudioEncoding: `MP3` (hardcode, chưa cần configurable).

### Implementation notes

- Inject `TextToSpeechClient` qua Spring `@Configuration` (`TextToSpeechClientConfig`).
- Credentials: dùng **file path env** (`app.voice.tts.credentials-path` → `GCP_TTS_CREDENTIALS_PATH`). KHÔNG dùng inline JSON hay ADC auto-detect.
- Fail-fast: nếu `credentialsPath` rỗng → throw `IllegalStateException` ở startup (rõ ràng hơn lazy error).
- SHA-256 dùng `java.security.MessageDigest` (JDK native, không cần `commons-codec` dependency).
- Khi Google SDK ném exception → throw `BusinessException(TTS_GENERATION_FAILED, cause)`.
- Log context: `textLength`, `languageCode`, `audioBytes`, `durationMs`. KHÔNG log raw text (PII) hoặc credentials path (sensitive).
- Retry policy: KHÔNG thêm ở TASK 7 — fail → bubble up, exception handler xử lý. Có thể add `@Retryable` ở TASK 19 (polish).
- `@EnableCaching` gộp vào `VoiceSecurityConfig` (cùng với `@EnableMethodSecurity`) — tránh tạo config riêng cho 1 annotation.

### Audit notes (2026-07-19)

- [x] Interface chỉ có 1 method (đơn giản hóa). Sau này nếu cần public API cho user custom voice params → mở rộng interface.
- [x] `VoiceTagJpaRepository` đã được FIX ở TASK 5 audit turn này (bỏ `@NoRepositoryBean`/generic, thêm `AndDeletedFalse` cho 6 methods, có ownership check).
- [x] SsmlVoiceGender dùng package `v1` (không phải `v1beta1`) — fix lỗi compile đầu tiên.

---

## ✅ [x] TASK 8: Voice Tag Service & Facade — DONE (2026-07-19)

**Mục tiêu**: Business logic + facade layer cho Voice Tag (Facade impl đặt ở `core/service/`, KHÔNG ở `infrastructure/web/`).

### Files cần tạo

- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/api/VoiceTagFacade.java` (interface — entry point)
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/VoiceTagService.java` (business logic interface, 4 methods)
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/VoiceTagFacadeImpl.java` (orchestrate các use case, delegate xuống service)
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/VoiceTagServiceImpl.java` (implement business logic)
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/AudioFileValidator.java` (`@Component` — extension + magic byte + size + duration)
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/AudioMetadata.java` (record: durationSeconds, format, bitrate, sampleRate)
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/audio/AudioMetadataExtractor.java` (FFprobe wrapper — Jaffree)

### Files cần update

- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/repository/VoiceTagJpaRepository.java` — thêm method `existsByUserIdAndNameAndIdNotAndDeletedFalse` để exclude-self khi update.

### Use case methods (VoiceTagFacade)

```java
public interface VoiceTagFacade {
    VoiceTagResponse createTtsTag(UUID userId, CreateTtsVoiceTagRequest req);
    VoiceTagResponse uploadTag(UUID userId, MultipartFile file, UploadVoiceTagRequest req);
    Page<VoiceTagResponse> listTags(UUID userId, VoiceTagType type, Pageable pageable);
    VoiceTagResponse getTag(UUID userId, UUID tagId);
    VoiceTagResponse updateTag(UUID userId, UUID tagId, UpdateVoiceTagRequest req);
    void deleteTag(UUID userId, UUID tagId);
    URL getAudioPresignedUrl(UUID userId, UUID tagId, Duration expiration);
}
```

### Business logic (VoiceTagServiceImpl)

- `createTtsTag`: check duplicate name → generate UUID → `textToSpeechService.synthesize()` (Redis cache hits) → build `VoiceTag.createTtsTag(...)` với estimated duration (1MB ≈ 8s cho MP3 @128kbps) → `storageService.upload(s3Key, bytes, audio/mpeg)` → `voiceTagJpaRepository.save(...)`.
- `uploadTag`: extract extension → validate (extension, magic byte, size, duration qua FFprobe) → check duplicate name → generate UUID → `storageService.upload(InputStream, size, contentType)` → save entity.
- `updateTag`: ownership check (`findByIdAndUserIdAndDeletedFalse` → 404 `VOICE_TAG_NOT_FOUND`) → nếu `name` đổi, check duplicate (exclude-self) → `domain.updateMetadata(name, description)` → save.
- `deleteTag`: ownership check → `entity.markDeleted("system")` → save → best-effort sync `storageService.delete(s3Key)` (WARN log nếu fail, không fail request).
- `getAudioPresignedUrl` (ở facade): ownership check → `storageService.generatePresignedUrl(s3Key, expiration).getUrl()`.

### Validation chain (AudioFileValidator)

- `validateExtension`: check extension ∈ `MP3, WAV, FLAC` (parse từ `app.voice.audio.allowed-formats` ở `@PostConstruct`).
- `validateMagicBytes`: đọc 12-byte head → delegate `MediaTypeUtils.detectFromBytes()` → so khớp với extension.
- `validateSize`: `sizeBytes <= 0` hoặc `> app.voice.audio.max-file-size-bytes` → `FILE_TOO_LARGE`.
- `validateDuration`: `AudioMetadataExtractor.extract(InputStream, extension)` → FFprobe qua Jaffree (temp file ở `Files.createTempFile`, cleanup qua `Files.deleteIfExists` trong finally) → so với `max-duration-seconds` → throw `INVALID_AUDIO_DURATION`. Probe fail → `AUDIO_PROCESSING_FAILED`.

### Implementation notes

- **Facade impl ở `core/service/`** (theo plan) — KHÔNG ở `infrastructure/web/facade/` như IAM — tránh god service (IAM Facade đã 593 dòng).
- **`VoiceTagFacade` interface trả raw DTO** (không wrap `ApiResponse`) — controller (TASK 9) sẽ lo phần wrapping.
- **`VoiceTagService` interface chỉ trả `VoiceTag` domain** cho write use case — facade chịu trách nhiệm map domain → response.
- **`VoiceTagServiceImpl` không dùng `@Transactional`** ở method level vì upload S3 + TTS tốn thời gian, không nên giữ DB connection. Tuy nhiên việc save entity vẫn có `@Transactional` ngầm qua JpaRepository.
- **`MultipartFile.getInputStream()` mỗi lần gọi trả InputStream mới** — em re-open 3 lần: validate magic byte, validate duration (FFprobe), upload S3. Plan yêu cầu caller re-open sau khi validator consume stream.
- **S3 cleanup** là best-effort sync — nếu fail chỉ WARN log, KHÔNG fail request (DB đã soft-delete). TASK 15 sẽ replace bằng Outbox-driven async cleanup.
- **Estimated duration cho TTS tag**: `bytes / 1_000_000 * 8` (≈ 128kbps MP3). Đây là ước lượng thô; giá trị chính xác sẽ được FFprobe ghi đè trong TASK 14 (FFmpeg pipeline re-probe tất cả tag khi cần).
- **i18n**: KHÔNG thêm key mới — toàn bộ error codes VOICE_001..013 + success messages đã có sẵn trong 3 file `messages*.properties` từ TASK 4.
- **KHÔNG thêm `@PreAuthorize`** — chỉ controller (TASK 9) mới handle role check.
- **KHÔNG thêm VOICE_TAG_IN_USE check** — deferred sang TASK 12 (SongTagConfig).
- **Update sang `updateMetadata` chỉ apply name + description** — KHÔNG đổi audio content (plan yêu cầu).
- **Audio processing timeout**: FFprobe qua Jaffree auto-download native binaries ở first call — có thể chậm 2-3s lần đầu. Acceptable cho MVP.

### Audit notes (2026-07-19)

- [x] 7 files tạo mới, 1 file update (VoiceTagJpaRepository — thêm method exclude-self cho update).
- [x] `mvn -pl modules/voice -am clean compile` → BUILD SUCCESS (25 source files, 0 ERROR, 0 WARNING).
- [x] `mvn -pl bootstrap -am clean compile` → BUILD SUCCESS (9 modules: shared-kernel, shared-web, shared-storage, outbox, notification, iam, voice, bootstrap).
- [x] Tuân thủ: Lombok `@RequiredArgsConstructor` cho DI, `@Slf4j` cho logging, `@Data` + `@Builder` cho DTOs/record.
- [x] Không code comment (theo `no-code-comments-backend.mdc`).
- [x] Không hardcode user-facing string — toàn bộ error qua `BusinessException(ErrorCode)`, GlobalExceptionHandler resolve i18n.
- [x] Không tạo test file (theo `no-auto-create-tests-backend.mdc`).
- [x] Không commit git (theo `no-auto-commit-push-backend.mdc` — đợi sếp confirm).

### Bug fix ngẫu nhiên — IAM Mapper Refactor (2026-07-19)

Sếp report lỗi khi start app:

```
APPLICATION FAILED TO START
Description: Parameter 1 of constructor in com.pwb.iam.infrastructure.security.CustomUserDetailsService required a bean of type 'com.pwb.iam.infrastructure.persistence.mapper.UserMapper' that could not be found.
```

**Root cause** (KHÔNG do TASK 8 gây ra — bug có sẵn từ trước):

- Cả 4 mapper IAM (`UserMapper`, `RoleMapper`, `OtpCodeMapper`, `PasswordResetTokenMapper`) dùng MapStruct `@Mapper(componentModel=spring)` nhưng chỉ có `default` methods (không abstract method nào).
- MapStruct khi đó **không generate implementation class** → Spring không tạo bean.
- Bug này ngăn app start được từ trước TASK 8 (em không touch file IAM nào trong TASK 8).

**Fix strategy** (sếp chọn: `remove-mapstruct-add-component`):

- Convert 4 mapper từ interface+MapStruct → concrete class hoặc abstract class với `@Component`.
- Bỏ `@Mapper(...)` MapStruct annotation + 2 `import org.mapstruct.*`.
- Giữ nguyên logic (`default` methods → public/private methods).
- `UserMapper` thành `abstract class @Component` (vì cần `private` helper `mapRole`/`mapRoleToEntity`) + nested `@Component static class UserMapperImpl extends UserMapper` để Spring tạo được bean.
- `RoleMapper`, `OtpCodeMapper`, `PasswordResetTokenMapper` thành concrete class.
- Caller (`IamFacadeImpl`, `CustomUserDetailsService`, `RoleLookupServiceImpl`) không thay đổi — Spring tự inject bean theo type.

**Files changed**:
- [x] `Backend/modules/iam/src/main/java/com/pwb/iam/infrastructure/persistence/mapper/UserMapper.java` — interface → abstract class `@Component`
- [x] `Backend/modules/iam/src/main/java/com/pwb/iam/infrastructure/persistence/mapper/RoleMapper.java` — interface → concrete class `@Component`
- [x] `Backend/modules/iam/src/main/java/com/pwb/iam/infrastructure/persistence/mapper/OtpCodeMapper.java` — interface → concrete class `@Component`
- [x] `Backend/modules/iam/src/main/java/com/pwb/iam/infrastructure/persistence/mapper/PasswordResetTokenMapper.java` — interface → concrete class `@Component`

**Verify**: `mvn -pl bootstrap -am clean compile` → BUILD SUCCESS toàn project.

> **Note quan trọng**: Bug này có từ commit `1af9fd7` (Jul 19) — mapper refactor trước đó. Sếp nên check git blame / commit history để hiểu tại sao các mapper trước đó chỉ có default methods. Có thể ban đầu MapStruct config khác (e.g. `spring.componentModel` đúng chỗ), hoặc có plan ban đầu muốn viết abstract method sau.

---

## ✅ [x] TASK 9: Voice Tag Controller — DONE (2026-07-19)

**Mục tiêu**: REST endpoints cho Voice Tag.

### Files cần tạo

- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/web/VoiceTagController.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/response/AudioUrlResponse.java` (response riêng cho presigned audio URL, chứa `url` + `expiresAt`)

### Files cần update (cross-module)

Để tránh voice module phụ thuộc trực tiếp IAM (`com.pwb.iam.infrastructure.security.CustomUserDetails`), em tách abstraction ra `shared-web`:

- [x] `Backend/shared-web/src/main/java/com/pwb/backend/security/AuthenticatedUser.java` — interface `{ UUID getId(); String getUsername(); String getRole(); }`.
- [x] `Backend/shared-web/src/main/java/com/pwb/backend/security/CurrentUser.java` — meta-annotation `@CurrentUser` (kết hợp `@AuthenticationPrincipal`).
- [x] `Backend/shared-web/src/main/java/com/pwb/backend/security/CurrentUserArgumentResolver.java` — resolve `@CurrentUser` từ `SecurityContextHolder`, hỗ trợ cả `AuthenticatedUser` native + reflection-adapter cho `CustomUserDetails` của IAM (không cần import IAM).
- [x] `Backend/shared-web/src/main/java/com/pwb/backend/config/WebMvcConfig.java` — register `CurrentUserArgumentResolver`.
- [x] `Backend/shared-web/pom.xml` — thêm `spring-boot-starter-security` (cần cho `SecurityContextHolder`).

### Endpoints (7)

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/voice-tags/tts` | Tạo TTS voice tag (201) |
| POST | `/api/v1/voice-tags/upload` | Upload voice tag audio (multipart) (201) |
| GET | `/api/v1/voice-tags` | List user's voice tags (paginated, optional `?type=`) |
| GET | `/api/v1/voice-tags/{id}` | Get voice tag detail |
| PUT | `/api/v1/voice-tags/{id}` | Update voice tag metadata |
| DELETE | `/api/v1/voice-tags/{id}` | Soft delete |
| GET | `/api/v1/voice-tags/{id}/audio` | Get presigned audio URL (1h TTL) |

### Security

- Class-level `@PreAuthorize("hasRole('PRO')")` — tất cả 7 endpoints yêu cầu role PRO.
- `userId` lấy từ `@CurrentUser AuthenticatedUser user` → `user.getId()` (UUID).
- KHÔNG nhận `userId` từ request body/param — luôn từ SecurityContext.

### i18n messages (đã có sẵn từ TASK 4)

- `VOICE_TAG_CREATED` — cho create TTS + upload success.
- `VOICE_TAG_UPDATED` — cho update success.
- `VOICE_TAG_DELETED` — cho delete success.
- KHÔNG thêm message mới cho list/get/audio URL.

### Implementation notes

- **Multipartupload**: dùng `@RequestPart("file") MultipartFile file` + `@RequestPart("metadata") UploadVoiceTagRequest` để tách file và JSON metadata rõ ràng. Frontend gửi `multipart/form-data` với 2 part riêng.
- **List pagination**: dùng `Pageable` mặc định Spring (`@PageableDefault(size = 20)`), trả `Page<VoiceTagResponse>` trực tiếp — chưa có `PageResponse` wrapper (deferred).
- **Presigned URL TTL**: `Duration.ofHours(1)` constant `PRESIGNED_URL_TTL` — hardcoded (override sau qua query-param nếu cần).
- **AudioUrlResponse**: tách DTO riêng vì cần trả `url` + `expiresAt` (frontend countdown / refresh).
- **Reflection adapter trong resolver**: vì `shared-web` không có IAM dependency, em dùng reflection để extract `getId()` / `getUsername()` / `getAuthorities()` từ bất kỳ principal type nào (`CustomUserDetails` từ IAM hoặc bất kỳ `AuthenticatedUser` native). Loose coupling OK, không cần dependency cycle.
- **Lombok**: `@RequiredArgsConstructor` cho DI, KHÔNG `@Data` trên record.
- **i18n**: KHÔNG thêm key mới — reuse TASK 4 keys.
- **Không Swagger annotation `@Operation`/`@Tag`** (per plan) — voice module chưa có `springdoc-openapi` dependency, defer đến khi cần Swagger cho voice (có thể thêm sau).
- **KHÔNG test** (per `no-auto-create-tests-backend.mdc`).
- **KHÔNG commit git** (per `no-auto-commit-push-backend.mdc` — đợi sếp confirm).

### Architecture benefit

Trước TASK 9, voice module KHÔNG có cách nào access SecurityContext user. Sau TASK 9:
- Bất kỳ module nào depend `shared-web` đều có thể inject `@CurrentUser AuthenticatedUser` mà KHÔNG cần IAM dependency.
- Pattern scale được cho Song module (TASK 13), Notification module (sau này), vv.
- Trade-off: thêm 1 abstraction layer, nhưng đổi lại loose coupling đúng chuẩn senior.

### Audit notes (2026-07-19)

- [x] 7 file mới (5 shared-web + 2 voice).
- [x] 1 file update (`Backend/shared-web/pom.xml` — thêm starter-security).
- [x] `mvn -pl modules/voice -am clean compile` → BUILD SUCCESS.
- [x] `mvn -pl bootstrap -am compile` → BUILD SUCCESS (9 modules).
- [x] Tuân thủ: Lombok `@RequiredArgsConstructor`, `@Data` + `@Builder` cho DTOs/record.
- [x] Không code comment (per `no-code-comments-backend.mdc`).
- [x] Không hardcode user-facing string — toàn bộ message qua `MessageResolver.get(key)`.
- [x] Không tạo test file (per `no-auto-create-tests-backend.mdc`).
- [x] Không commit git (per `no-auto-commit-push-backend.mdc` — đợi sếp confirm).

---

## ✅ [x] TASK 10: Song Domain Models & Repository

**Mục tiêu**: Domain entities + JPA entities + mappers + repositories cho Song và SongTagConfig.

### Files cần tạo

- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/api/enums/SongStatus.java` (UPLOADED, PROCESSING, PROCESSED, FAILED)
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/core/model/Song.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/core/model/SongTagConfig.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/entity/SongJpaEntity.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/entity/SongTagConfigJpaEntity.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/mapper/SongMapper.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/mapper/SongTagConfigMapper.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/repository/SongJpaRepository.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/repository/SongTagConfigJpaRepository.java`

### Repository methods bắt buộc

```java
public interface SongJpaRepository extends JpaRepository<SongJpaEntity, UUID> {
    Optional<SongJpaEntity> findByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);
    Page<SongJpaEntity> findByUserIdAndDeletedFalse(UUID userId, Pageable pageable);
    Page<SongJpaEntity> findByUserIdAndStatusAndDeletedFalse(UUID userId, SongStatus status, Pageable pageable);
    boolean existsByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);
}

public interface SongTagConfigJpaRepository extends JpaRepository<SongTagConfigJpaEntity, UUID> {
    Optional<SongTagConfigJpaEntity> findBySongIdAndDeletedFalse(UUID songId);
    boolean existsBySongIdAndDeletedFalse(UUID songId);
    void deleteBySongIdAndDeletedFalse(UUID songId);
}
```

### Implementation notes

- Song có 2 S3 key: `originalS3Key` (NOT NULL) + `processedS3Key` (nullable, set khi processing xong).
- SongTagConfig có UNIQUE constraint trên `song_id` → chỉ 1 config / song (1:1).
- Update status cần qua optimistic lock (`@Version`) để tránh race khi nhiều consumer claim cùng song.

---

## ✅ [x] TASK 11: Song DTOs

### Files cần tạo

- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/request/UploadSongRequest.java` (multipart)
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/request/UpdateSongRequest.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/request/ConfigureVoiceTagRequest.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/response/SongResponse.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/response/SongDetailResponse.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/response/VoiceTagConfigResponse.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/response/ProcessingStatusResponse.java`

---

## ✅ [x] TASK 12: Song Service & Facade

**Mục tiêu**: Business logic + facade cho Song.

### Files cần tạo

- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/api/SongFacade.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/SongService.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/SongFacadeImpl.java`
- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/SongServiceImpl.java`

### Use case methods (SongFacade)

```java
public interface SongFacade {
    SongResponse uploadSong(UUID userId, MultipartFile file, UploadSongRequest req);
    Page<SongResponse> listSongs(UUID userId, SongStatus status, Pageable pageable);
    SongDetailResponse getSong(UUID userId, UUID songId);
    SongResponse updateSong(UUID userId, UUID songId, UpdateSongRequest req);
    void deleteSong(UUID userId, UUID songId);
    VoiceTagConfigResponse configureVoiceTag(UUID userId, UUID songId, ConfigureVoiceTagRequest req);
    VoiceTagConfigResponse getVoiceTagConfig(UUID userId, UUID songId);
    void removeVoiceTagConfig(UUID userId, UUID songId);
    ProcessingStatusResponse triggerProcessing(UUID userId, UUID songId);
    ProcessingStatusResponse getProcessingStatus(UUID userId, UUID songId);
    URL getStreamPresignedUrl(UUID userId, UUID songId, Duration expiration);
    URL getOriginalPresignedUrl(UUID userId, UUID songId, Duration expiration);
}
```

### Business logic notes

- `uploadSong`: validate (extension + magic byte + size + duration) → S3 upload → DB save with status=UPLOADED.
- `deleteSong`: soft delete + async S3 cleanup (original + processed) qua outbox.
- `triggerProcessing`: validate status ∈ {UPLOADED, FAILED} → set status=PROCESSING → publish `VoiceProcessingRequestedIntegrationEvent` qua outbox.
- `getStreamPresignedUrl`: chọn `processedS3Key` nếu có, fallback `originalS3Key`. Luôn check ownership trước.

---

## ✅ [x] TASK 13: Song Controller

### Files cần tạo

- [x] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/web/SongController.java`

### Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/songs/upload` | Upload song (multipart) |
| GET | `/api/v1/songs` | List user's songs (paginated) |
| GET | `/api/v1/songs/{id}` | Get song detail |
| PUT | `/api/v1/songs/{id}` | Update song metadata |
| DELETE | `/api/v1/songs/{id}` | Soft delete |
| POST | `/api/v1/songs/{songId}/voice-tag` | Configure voice tag |
| GET | `/api/v1/songs/{songId}/voice-tag` | Get voice tag config |
| PUT | `/api/v1/songs/{songId}/voice-tag` | Update voice tag config |
| DELETE | `/api/v1/songs/{songId}/voice-tag` | Remove voice tag config |
| POST | `/api/v1/songs/{songId}/process` | Trigger processing (async) |
| GET | `/api/v1/songs/{songId}/status` | Get processing status |
| GET | `/api/v1/songs/{id}/stream` | Get stream presigned URL |
| GET | `/api/v1/songs/{id}/original` | Get original presigned URL |

### Security

- Class-level `@PreAuthorize("hasRole('PRO')")`
- UserId luôn từ SecurityContext

---

## TASK 14: Audio Processing Service (FFmpeg)

**Mục tiêu**: Tích hợp FFmpeg qua Jaffree.

### Files cần tạo

- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/AudioProcessingService.java` (interface)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/AudioMetadata.java` (record: duration, format, bitrate, sampleRate)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/audio/FFmpegAudioProcessingService.java` (`@Service`)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/audio/AudioMetadataExtractor.java` (FFprobe wrapper)

### Methods

```java
public interface AudioProcessingService {
    AudioMetadata extractMetadata(Path audioFile);
    Path insertVoiceTagAtInterval(Path original, Path voiceTag, VoiceTagConfig config, Path output);
}
```

### Implementation notes

- Dùng Jaffree: `FFmpeg.atPath(tempDir).addInput(...).addOutput(...).execute()`.
- Validate trước khi mix: `intervalSeconds > voiceTag.duration + max(fadeInMs, fadeOutMs) / 1000` (throw `INVALID_INTERVAL` nếu sai).
- Temp dir từ config (`app.voice.audio.temp-dir`), cleanup sau khi xong (try-finally).
- FFmpeg command cho interval mixing (pseudo):
  ```
  ffmpeg -i original.mp3 -i voiceTag.mp3 \
    -filter_complex "[1:a]volume=0.5,adelay=25000|25000[tag1];
                     [1:a]volume=0.5,adelay=50000|50000[tag2];
                     [0:a][tag1][tag2]amix=inputs=3:duration=first[out]" \
    -map "[out]" output.mp3
  ```

---

## TASK 15: Voice Tag Insertion Processor (Kafka Consumer)

**Mục tiêu**: Async processor để chèn voice tag vào song, dùng Outbox + Kafka (đồng bộ với notification pattern).

### Files cần tạo

- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/event/VoiceProcessingRequestedIntegrationEvent.java` (record: eventId, songId, userId, occurredAt)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/processor/VoiceTagInsertionProcessor.java` (`@KafkaListener`)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/config/VoiceProcessingConfig.java`

### Flow

```
1. @KafkaListener nhận event từ topic "voice.processing.v1"
2. Load Song (status=PROCESSING) + SongTagConfig + VoiceTag
3. Download originalS3Key → temp file
4. Download voiceTagS3Key → temp file
5. FFmpeg insert voice tag (interval mixing)
6. Upload processed to processedS3Key
7. Update Song: status=PROCESSED, processedS3Key=...
8. Cleanup temp files
9. If fail: status=FAILED, lastError=msg, nack để outbox retry
```

### Implementation notes

- Consumer config: group-id=`voice-processor`, auto-offset-reset=earliest, concurrency=2.
- Dùng `@Transactional` trên method update DB, KHÔNG dùng cho toàn bộ processing (FFmpeg lâu, không nên giữ transaction).
- Retry policy: outbox đã có `app.outbox.retry.*` — tận dụng luôn (max 3 attempts, backoff 1s/5s/30s).
- DLQ (dead letter queue): nếu fail sau 3 lần → outbox status=FAILED, song.lastError lưu message.

### Prerequisite

- [ ] Refactor `OutboxJpaWriter.enqueue()` để nhận `aggregateType` thay vì hardcode `"User"` (TASK 1.5 phụ — nhỏ, có thể làm song song TASK 1).

---

## TASK 16: Song Processing Trigger

**Mục tiêu**: Endpoint trigger xử lý (TASK này nhỏ, gộp vào TASK 12-13).

### Files cần update

- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/SongFacade.java` — thêm method `triggerProcessing(userId, songId)`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/web/SongController.java` — endpoint `POST /api/v1/songs/{songId}/process`

### Response

```json
{
  "success": true,
  "data": {
    "songId": "uuid",
    "status": "PROCESSING",
    "message": "VOICE_PROCESSING_STARTED"
  }
}
```

---

## TASK 17: PRO Role Check Integration

**Mục tiêu**: Đảm bảo chỉ PRO users truy cập voice endpoints.

### Implementation

- `@PreAuthorize("hasRole('PRO')")` ở class-level trên `VoiceTagController` và `SongController`.
- KHÔNG check ở service layer — controller là đủ (defense in depth chỉ khi thực sự cần).
- IAM module KHÔNG cần thay đổi — authority `ROLE_PRO` đã có sẵn từ `CustomUserDetails`.

### Test

- [ ] User role=USER gọi `/api/v1/voice-tags` → 403.
- [ ] User role=PRO gọi thành công.
- [ ] User role=ADMIN → 403 (vì plan chỉ cho PRO, không cho ADMIN — nếu muốn ADMIN cũng dùng được: đổi thành `hasAnyRole('PRO', 'ADMIN')`).

---

## TASK 18: Error Handling & i18n

**Mục tiêu**: Hoàn thiện i18n cho mọi user-facing message.

### Files cần update

- [ ] `Backend/shared-web/src/main/resources/messages/messages.properties` — thêm tất cả `VOICE_*` success messages (xem TASK 4)
- [ ] `Backend/shared-web/src/main/resources/messages/messages_en.properties` — same
- [ ] `Backend/shared-web/src/main/resources/messages/messages_vi.properties` — Vietnamese translation (đúng dấu, tự nhiên)

### Checklist messages

- [ ] `VOICE_TAG_CREATED`, `VOICE_TAG_UPDATED`, `VOICE_TAG_DELETED`
- [ ] `SONG_UPLOADED`, `SONG_UPDATED`, `SONG_DELETED`
- [ ] `VOICE_TAG_CONFIGURED`, `VOICE_PROCESSING_STARTED`
- [ ] `ACCESS_DENIED_PRO_ONLY`
- [ ] Tất cả validation messages: `{validation.name.required}`, `{validation.text.maxlength}`, ...

### Lưu ý

- File `messages_vi.properties` hiện tại có lỗi encoding (mất dấu) ở một số key cũ — khi sửa, **dịch đúng dấu tiếng Việt**, không Google Translate máy móc.
- KHÔNG hardcode message trong annotation `@NotBlank(message = "...")` — dùng key `{...}`.

---

## TASK 19: Integration & Polish

**Mục tiêu**: Wire tất cả lại, build full project, smoke test end-to-end.

### Checklist

- [ ] `mvn clean install -DskipTests` pass ở root
- [ ] `mvn -pl modules/voice -am clean compile` pass riêng voice
- [ ] Start app với `SPRING_PROFILES_ACTIVE=local`, mở `http://localhost:8080/swagger-ui.html` — thấy voice endpoints
- [ ] Smoke test E2E:
  1. Register user → verify OTP → login → token
  2. Tạo TTS voice tag → check Redis cache key
  3. Upload song
  4. Configure voice tag cho song
  5. Trigger processing → check Kafka topic → check status change PROCESSING → PROCESSED
  6. Stream presigned URL → play được audio
- [ ] Verify ownership: user A không thấy resource của user B
- [ ] Verify file validation: upload .txt file rename .mp3 → phải reject (magic byte mismatch)
- [ ] Verify presigned URL expiration: đợi 1h → URL invalid
- [ ] Check log không in sensitive data (audio content, presigned URL query string)
- [ ] Update `Backend/README.md` và `Backend/STRUCTURE.md` với voice module mới
- [ ] Update parent `pom.xml` modules section nếu cần

---

## Implementation Order (Khuyến nghị)

```
Week 1:
├── Day 1: TASK 0 (JwtFilter fix - PREREQUISITE)
├── Day 1-2: TASK 1 (shared-storage)
├── Day 3: TASK 2, 3 (Voice setup + migrations)
├── Day 4: TASK 4 (Error codes)
└── Day 5: TASK 5-7 (Voice Tag domain + TTS)

Week 2:
├── Day 1-2: TASK 8-9 (Voice Tag service + controller)
├── Day 3-4: TASK 10-11 (Song domain + DTOs)
└── Day 5: TASK 12-13 (Song service + controller)

Week 3:
├── Day 1-2: TASK 14 (Audio processing)
├── Day 3-4: TASK 15-16 (Async processor + trigger)
└── Day 5: TASK 17 (PRO role check)

Week 4:
├── Day 1: TASK 18 (Error handling + i18n)
├── Day 2-4: TASK 19 (Integration + E2E smoke)
└── Day 5: Buffer / bug fixes

Week 5:
└── PR review + final polish
```

> **MVP shortcut**: Nếu muốn ship nhanh, cắt Phase 4 (TASK 14-16) ra sprint sau. Voice Tag CRUD độc lập với Audio Processing — frontend có thể integrate Phase 2-3 + 5 trong 3 tuần.

---

## Prerequisites

| # | Prerequisite | Owner | Status |
|---|---|---|---|
| 1 | `JwtAuthenticationFilter` enforce UserStatus | Backend | ⚠️ TASK 0 — phải xong trước |
| 2 | AWS S3 bucket `pwb-storage` (hoặc MinIO local) | DevOps | ⏳ TODO |
| 3 | Google Cloud TTS service account + credentials | DevOps | ⏳ TODO |
| 4 | PRO role seeded trong DB (V4 migration) | ✅ Done | Có sẵn |
| 5 | `OutboxJpaWriter.aggregateType` refactor thành parameter | Backend | ⏳ Nhỏ, làm song song TASK 1 |

---

## Out of Scope (CỐ Ý KHÔNG làm trong sprint này)

| Item | Lý do |
|---|---|
| Unit tests | Theo rule `no-auto-create-tests-backend.mdc` — đợi lệnh riêng |
| S3 lifecycle policy | Bỏ khỏi docs (chưa rõ infra owner) |
| HLS streaming + AES encryption | MVP dùng presigned URL thuần, dễ nâng cấp sau |
| MongoDB / Elasticsearch integration | Chưa rõ requirement |
| Audio conversion (MP3 → WAV, etc.) | Plan yêu cầu giữ nguyên format |
| Multiple voice tags per song | Plan yêu cầu 1:1 |
| Auto-processing ngay sau upload | Plan yêu cầu user trigger |

---

## Notes

- **Mỗi TASK nên được commit riêng** để dễ review (`feat(voice): TASK 1 - shared-storage module`).
- **Chạy `mvn -pl modules/voice -am clean compile`** sau mỗi TASK để verify.
- **Tuân thủ rule `no-code-comments`** — không thêm comment trong code Java/TS.
- **Facade impl ở `core/service/`**, KHÔNG ở `infrastructure/web/facade/` (tránh god service).
- **Frontend có thể bắt đầu integrate** sau TASK 9 (Voice Tag CRUD).

---

## Definition of Done (toàn sprint)

- [x] TASK 0 — JwtFilter UserStatus enforcement
- [x] TASK 1 — shared-storage module
- [x] TASK 2 — Voice module skeleton
- [x] TASK 3 — DB migrations
- [x] TASK 4 — Error codes + i18n keys
- [x] TASK 5 — Voice tag domain models
- [x] TASK 6 — Voice tag DTOs
- [x] TASK 7 — Google TTS service + cache
- [x] TASK 8 — Voice tag service + facade + audio validator + FFprobe
- [x] TASK 9 — Voice tag REST controller (with shared-web `@CurrentUser` abstraction)
- [x] TASK 10-13 — Song domain + DTOs + service + controller
- [ ] TASK 14 — FFmpeg audio processing service
- [ ] TASK 15 — Kafka async processor
- [ ] TASK 16 — Song processing trigger
- [ ] TASK 17 — PRO role integration
- [ ] TASK 18 — Error handling + i18n polish
- [ ] TASK 19 — Integration + E2E smoke test
- [ ] `mvn clean install` pass với 0 error
- [ ] E2E smoke test pass
- [ ] i18n EN + VI đầy đủ
- [ ] Swagger docs đầy đủ cho voice endpoints
- [ ] `Backend/README.md` update
- [ ] PR review approved
