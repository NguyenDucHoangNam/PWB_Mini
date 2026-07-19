# Voice Module — Implementation Tasks

> **Ngày tạo**: 2026-07-19
> **Dựa trên**: [voice-module-plan.md](./voice-module-plan.md)
> **Trạng thái**: Ready for Implementation
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
├── shared-storage/       # 📋 TASK 1 (NEW)
├── bootstrap/            # ✅ Đã có
└── modules/
    ├── iam/              # ✅ Đã có (cần update JwtAuthenticationFilter — TASK 0)
    ├── notification/     # ✅ Đã có
    ├── outbox/           # ✅ Đã có (cần refactor aggregateType)
    └── voice/            # 📋 TASKS 2-19 (đã có skeleton REVOKED)
```

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

## TASK 1: Setup shared-storage Module

**Mục tiêu**: Tạo module dùng chung cho S3/Local storage abstraction.

### Files cần tạo

- [ ] `Backend/shared-storage/pom.xml` (deps: shared-kernel, AWS SDK s3 + s3-transfer-manager + auth, lombok)
- [ ] `Backend/shared-storage/src/main/java/com/pwb/storage/api/StorageService.java` (interface full feature)
- [ ] `Backend/shared-storage/src/main/java/com/pwb/storage/api/StorageException.java` (extends `BaseBusinessException`)
- [ ] `Backend/shared-storage/src/main/java/com/pwb/storage/api/dto/UploadResult.java`
- [ ] `Backend/shared-storage/src/main/java/com/pwb/storage/api/dto/PresignedUrlResult.java`
- [ ] `Backend/shared-storage/src/main/java/com/pwb/storage/api/dto/ObjectMetadata.java`
- [ ] `Backend/shared-storage/src/main/java/com/pwb/storage/infrastructure/config/StorageProperties.java` (`@ConfigurationProperties("app.storage")`)
- [ ] `Backend/shared-storage/src/main/java/com/pwb/storage/infrastructure/config/StorageProviderType.java` (enum `S3, LOCAL`)
- [ ] `Backend/shared-storage/src/main/java/com/pwb/storage/infrastructure/config/StorageAutoConfig.java` (`@Configuration` + `@ConditionalOnProperty`)
- [ ] `Backend/shared-storage/src/main/java/com/pwb/storage/infrastructure/impl/S3StorageServiceImpl.java` (`@ConditionalOnProperty(provider=S3)`)
- [ ] `Backend/shared-storage/src/main/java/com/pwb/storage/infrastructure/impl/LocalStorageServiceImpl.java` (`@ConditionalOnProperty(provider=LOCAL)`)
- [ ] `Backend/shared-storage/src/main/java/com/pwb/storage/infrastructure/util/MediaTypeUtils.java` (magic byte detection)

### Files cần update

- [ ] `Backend/pom.xml` (parent) — thêm `<module>shared-storage</module>`
- [ ] `Backend/bootstrap/pom.xml` — thêm dependency `shared-storage`
- [ ] `Backend/shared-kernel/src/main/java/com/pwb/backend/exception/ErrorCode.java` — thêm các code storage:

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

- [ ] `Backend/bootstrap/src/main/resources/application.yml` — section `app.storage.*` (xem `voice-module-plan.md` §10.1)

### Messages cần thêm (i18n)

- [ ] `Backend/shared-web/src/main/resources/messages/messages.properties` — add `STORAGE_001..006`
- [ ] `Backend/shared-web/src/main/resources/messages/messages_en.properties` — same
- [ ] `Backend/shared-web/src/main/resources/messages/messages_vi.properties` — Vietnamese translation

### Implementation notes

- **Interface full feature** (10 methods): upload (2 overloads), download, delete, deleteAll, exists, getMetadata, generatePresignedUrl, generatePresignedUploadUrl.
- **S3 impl**: dùng `S3Client` (sync) + `S3TransferManager` cho multipart upload > 25MB.
- **Local impl**: dùng `java.nio.file.Files` + presigned URL trả về local HTTP endpoint (chỉ dùng dev/test).
- **Retry**: wrap upload/download với Spring Retry (max 3 attempts, exponential backoff 1s/3s/10s).
- **Error mapping**: `S3Exception` → `StorageException(STORAGE_UPLOAD_FAILED, e)`.

### Definition of Done

- [ ] `mvn -pl shared-storage -am clean compile` pass
- [ ] Có thể tạo 1 endpoint test `/api/v1/test/upload` (chỉ dev) upload file → lưu S3 → download lại → assert equal bytes
- [ ] Switch `app.storage.provider=LOCAL` → chạy được không cần S3 credentials

---

## TASK 2: Setup Voice Module Structure

**Mục tiêu**: Tạo voice module với dependencies, config, security, packaging.

### Files cần tạo

- [ ] `Backend/modules/voice/pom.xml` (deps: shared-kernel, shared-web, shared-storage, iam, outbox, starter-web, starter-data-jpa, starter-cache, starter-validation, spring-kafka, google-cloud-texttospeech, jaffree, lombok, mapstruct)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/config/VoiceJpaConfig.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/config/VoiceProperties.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/config/TtsProperties.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/config/AudioProcessingProperties.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/config/VoiceSecurityConfig.java` (`@EnableMethodSecurity`)

### Files cần update

- [ ] `Backend/pom.xml` (parent) — đảm bảo `<module>modules/voice</module>` đã có (skeleton)
- [ ] `Backend/bootstrap/pom.xml` — thêm dependency `voice`
- [ ] `Backend/bootstrap/src/main/resources/application.yml` — section `app.voice.*` (xem plan §10.1)

### Implementation notes

- Voice module là module đầu tiên dùng `shared-storage` → đảm bảo wiring qua constructor injection.
- Voice module KHÔNG inject `JwtAuthenticationFilter` hay `CustomUserDetails` trực tiếp từ IAM — chỉ cần `@AuthenticationPrincipal` + `Authentication.getAuthorities()`.

---

## TASK 3: Create Database Migrations

**Mục tiêu**: Tạo bảng `voice_voice_tags`, `voice_songs`, `voice_song_tag_configs` + indexes.

### Files cần tạo

- [ ] `Backend/bootstrap/src/main/resources/db/migration/V8__create_voice_tables.sql`

### SQL content

Xem chi tiết schema trong `voice-module-plan.md` §9.1. Tóm tắt:

- 3 tables với audit fields (`created_at`, `updated_at`, `created_by`, `updated_by`, `deleted`, `deleted_at`, `version`)
- FK constraints (`ON DELETE RESTRICT` cho user_id, `ON DELETE CASCADE` cho song_id trong config)
- UNIQUE constraints (`(user_id, name)` cho tags, `(song_id)` cho config)
- CHECK constraints (interval > 0, volume 0-100, fade >= 0)
- 6 indexes

### Implementation notes

- Migration follow pattern V1-V7 (PostgreSQL syntax: `gen_random_uuid()`, `TIMESTAMPTZ`).
- Comment trong SQL bằng `--` để giải thích constraint.

---

## TASK 4: Voice Error Codes & Messages

**Mục tiêu**: Thêm error codes cho voice module vào enum tập trung + i18n.

### Files cần update

- [ ] `Backend/shared-kernel/src/main/java/com/pwb/backend/exception/ErrorCode.java` — thêm 13 codes sau:

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

- [ ] `Backend/shared-web/src/main/resources/messages/messages.properties` — add `VOICE_001..013` + success messages
- [ ] `Backend/shared-web/src/main/resources/messages/messages_en.properties` — same
- [ ] `Backend/shared-web/src/main/resources/messages/messages_vi.properties` — Vietnamese translation

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

## TASK 5: Voice Tag Domain Models

**Mục tiêu**: Tạo domain entities + JPA entities + mappers + repositories cho Voice Tag.

### Files cần tạo

- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/enums/VoiceTagType.java` (TTS, UPLOADED)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/enums/AudioFormat.java` (MP3, WAV, FLAC) — share với Song
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/core/model/BaseEntity.java` (auditable base, copy pattern từ `IamJpaBaseEntity`)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/core/model/VoiceTag.java` (immutable domain object với factory methods)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/entity/VoiceTagJpaEntity.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/mapper/VoiceTagMapper.java` (MapStruct)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/repository/VoiceTagJpaRepository.java`

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

## TASK 6: Voice Tag DTOs

**Mục tiêu**: Tạo request/response DTOs.

### Files cần tạo

- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/request/CreateTtsVoiceTagRequest.java`

```java
public class CreateTtsVoiceTagRequest {
    @NotBlank @Size(max = 128) private String name;
    @Size(max = 512) private String description;
    @NotBlank @Size(max = 4000) private String text;
    @NotBlank @Pattern(regexp = "^[a-z]{2}-[A-Z]{2}$") private String languageCode;
    private String voiceName;
    private Double speakingRate;
    private Double pitch;
}
```

- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/request/UpdateVoiceTagRequest.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/request/UploadVoiceTagRequest.java` (multipart form)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/response/VoiceTagResponse.java`

### Validation

- Dùng key i18n: `{validation.name.required}`, `{validation.text.maxlength}`, etc. (xem TASK 4 để add keys).
- KHÔNG hardcode message trong annotation.

---

## TASK 7: Google TTS Service

**Mục tiêu**: Tích hợp Google Cloud Text-to-Speech với Redis cache.

### Files cần tạo

- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/TextToSpeechService.java` (interface)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/tts/GoogleTtsServiceImpl.java` (`@Service`, `@Cacheable`)

### Features

- `synthesize(text, languageCode, voiceName, speakingRate, pitch) → byte[]`
- Cache qua `@Cacheable(value = "ttsCache", key = "#root.target.cacheKey(...)")`
- Cache key: `SHA-256(text + "|" + languageCode + "|" + voiceName + "|" + speakingRate + "|" + pitch)`
- TTL: 7 days (config trong `app.voice.tts.cache-ttl-hours`)
- Default values: `languageCode=en-US`, `voiceName=en-US-Standard-A`, `speakingRate=1.0`, `pitch=0.0`

### Implementation notes

- Inject `TextToSpeechClient` (Google SDK) qua Spring `@Configuration`.
- `@EnableCaching` ở `VoiceSecurityConfig` hoặc config riêng.
- Cache config dùng Redis (`spring.cache.type=redis`).

---

## TASK 8: Voice Tag Service & Facade

**Mục tiêu**: Business logic + facade layer cho Voice Tag (Facade impl đặt ở `core/service/`, KHÔNG ở `infrastructure/web/`).

### Files cần tạo

- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/VoiceTagFacade.java` (interface — entry point)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/VoiceTagService.java` (business logic)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/VoiceTagFacadeImpl.java` (orchestrate các use case, delegate xuống service)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/VoiceTagServiceImpl.java` (implement business logic)

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

- `createTtsTag`: validate → cache check → GoogleTts → S3 upload → DB save
- `uploadTag`: validate (extension + magic byte + size + duration) → S3 upload → DB save
- `updateTag`: chỉ update metadata (name, description) — KHÔNG cho đổi audio content
- `deleteTag`: soft delete + async S3 delete (qua outbox event để tránh block request)
- `getAudioPresignedUrl`: validate ownership → presigned URL expiration 1h

### Implementation notes

- Facade chỉ delegate/compose, không chứa business logic (tránh "god service" như `IamFacadeImpl`).
- File upload validate: extension ∈ {mp3, wav, flac}, magic byte check (3 bytes đầu), size ≤ 500MB, duration ≤ 10 min (dùng FFprobe).
- Magic byte: `ID3` (MP3), `RIFF` (WAV), `fLaC` (FLAC).

---

## TASK 9: Voice Tag Controller

**Mục tiêu**: REST endpoints cho Voice Tag.

### Files cần tạo

- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/web/VoiceTagController.java`

### Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/voice-tags/tts` | Tạo TTS voice tag |
| POST | `/api/v1/voice-tags/upload` | Upload voice tag audio (multipart) |
| GET | `/api/v1/voice-tags` | List user's voice tags |
| GET | `/api/v1/voice-tags/{id}` | Get voice tag detail |
| PUT | `/api/v1/voice-tags/{id}` | Update voice tag metadata |
| DELETE | `/api/v1/voice-tags/{id}` | Soft delete |
| GET | `/api/v1/voice-tags/{id}/audio` | Get presigned audio URL |

### Security

- Class-level `@PreAuthorize("hasRole('PRO')")`
- UserId lấy từ `Authentication.getPrincipal()` qua custom helper (hoặc inject `Authentication`)
- KHÔNG nhận `userId` từ request body/param — luôn từ SecurityContext

### Implementation notes

- Multipart upload: set `Content-Type: multipart/form-data`, dùng `@RequestParam("file") MultipartFile file`.
- Response wrapper: `ApiResponse.success(message(MSG_*), data)` (theo pattern IAM).
- Sử dụng `MessageResolver` từ shared-web để resolve i18n messages.

---

## TASK 10: Song Domain Models & Repository

**Mục tiêu**: Domain entities + JPA entities + mappers + repositories cho Song và SongTagConfig.

### Files cần tạo

- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/enums/SongStatus.java` (UPLOADED, PROCESSING, PROCESSED, FAILED)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/core/model/Song.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/core/model/SongTagConfig.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/entity/SongJpaEntity.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/entity/SongTagConfigJpaEntity.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/mapper/SongMapper.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/mapper/SongTagConfigMapper.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/repository/SongJpaRepository.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/repository/SongTagConfigJpaRepository.java`

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

## TASK 11: Song DTOs

### Files cần tạo

- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/request/UploadSongRequest.java` (multipart)
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/request/UpdateSongRequest.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/request/ConfigureVoiceTagRequest.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/response/SongResponse.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/response/SongDetailResponse.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/response/VoiceTagConfigResponse.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/dto/response/ProcessingStatusResponse.java`

---

## TASK 12: Song Service & Facade

**Mục tiêu**: Business logic + facade cho Song.

### Files cần tạo

- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/api/SongFacade.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/SongService.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/SongFacadeImpl.java`
- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/core/service/SongServiceImpl.java`

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

## TASK 13: Song Controller

### Files cần tạo

- [ ] `Backend/modules/voice/src/main/java/com/pwb/voice/infrastructure/web/SongController.java`

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

- [ ] Tất cả TASK 0-19 done
- [ ] `mvn clean install` pass với 0 error
- [ ] E2E smoke test pass
- [ ] i18n EN + VI đầy đủ
- [ ] Swagger docs đầy đủ cho voice endpoints
- [ ] `Backend/README.md` update
- [ ] PR review approved
