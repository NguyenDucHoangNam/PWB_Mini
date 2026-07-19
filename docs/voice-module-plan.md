# Voice Module — Kế hoạch triển khai

> **Ngày tạo**: 2026-07-19
> **Ngày chỉnh sửa lần cuối**: 2026-07-19
> **Trạng thái**: Final — Đã khảo sát codebase hiện tại, đã chốt quyết định kiến trúc

---

## Mục lục

1. [Tổng quan nghiệp vụ](#1-tổng-quan-nghiệp-vụ)
2. [Bối cảnh & ràng buộc từ codebase hiện tại](#2-bối-cảnh--ràng-buộc-từ-codebase-hiện-tại)
3. [Module mới: shared-storage](#3-module-mới-shared-storage)
4. [Module mới: voice](#4-module-mới-voice)
5. [Module Dependencies](#5-module-dependencies)
6. [Voice Module Architecture](#6-voice-module-architecture)
7. [Chi tiết nghiệp vụ theo Phase](#7-chi-tết-nghiệp-vụ-theo-phase)
8. [API Endpoints](#8-api-endpoints)
9. [Database Schema](#9-database-schema)
10. [Cấu hình](#10-cấu-hình)
11. [Security Checklist](#11-security-checklist)
12. [Prerequisites](#12-prerequisites)
13. [Timeline](#13-timeline)

---

## 1. Tổng quan nghiệp vụ

### 1.1 Kết quả khảo sát

| Nghiệp vụ | Chi tiết | Ghi chú |
|---|---|---|
| **Voice tag** | User tự tạo watermark riêng | Không có system default |
| **Song source** | Upload từ máy | MP3, WAV, FLAC |
| **TTS provider** | Google Cloud TTS | Bắt buộc |
| **Processing trigger** | User tùy chọn | Không chọn tag → không chèn |
| **Multiple tags** | 1 voice tag / bài hát | Không hỗ trợ nhiều tag |
| **Storage** | Giữ cả original + processed | Original để user download |
| **Access control** | Chỉ PRO users | `@PreAuthorize("hasRole('PRO')")` |
| **Audio format** | Giữ nguyên format gốc | Không convert |
| **FFmpeg** | Jaffree (Java FFmpeg wrapper) | Tự download binary |
| **Playback** | Streaming only | Presigned URL 1h |
| **TTS cache** | Redis (Spring Cache) | Hash-based key |
| **Async processing** | Outbox + Kafka | Đồng bộ với notification pattern |
| **Preview** | Sau khi lưu | Không preview trước |

### 1.2 Business Rules

1. **Access Control**: Chỉ user có role `PRO` mới được dùng voice module (qua Spring Security authority `ROLE_PRO`, đã có sẵn trong `CustomUserDetails`).
2. **Optional Processing**: User upload song → không bắt buộc gắn voice tag.
3. **1:1 Relationship**: Mỗi song có tối đa 1 voice tag config (`UNIQUE (song_id)` trên `voice_song_tag_configs`).
4. **Ownership**: User chỉ thấy và thao tác được resource của chính mình (enforce ở repository method, không phải controller).
5. **Storage Policy**: Giữ cả file gốc và file đã xử lý.
6. **TTS Optimization**: Cache TTS audio theo hash của `(text, languageCode, voiceName, speakingRate, pitch)`.

---

## 2. Bối cảnh & ràng buộc từ codebase hiện tại

### 2.1 Trạng thái hiện tại

| Module | Trạng thái | Ghi chú |
|---|---|---|
| `shared-kernel` | ✅ Có | Chứa `ErrorCode` central enum, `BusinessException`, `BaseBusinessException` |
| `shared-web` | ✅ Có | Chứa `GlobalExceptionHandler`, `MessageResolver`, `ApiResponse`, i18n `messages*.properties` (72 keys) |
| `bootstrap` | ✅ Có | Spring Boot app + `application.yml` + Flyway V1-V7 |
| `modules/iam` | ✅ Có | Đã có `Role.PRO` + authority `ROLE_PRO` (chưa dùng) |
| `modules/notification` | ✅ Có | Pattern reference cho Kafka consumer + i18n email |
| `modules/outbox` | ✅ Có | Transactional outbox + `@Scheduled` relay |
| `modules/voice` | ⚠️ REVOKED | Skeleton rỗng — `pom.xml` không có dependency, 3 package trống |
| File/Object storage | ❌ Chưa có | AWS SDK BOM đã declare ở parent pom nhưng 0 Java code nào dùng |

### 2.2 Pattern đã chốt (Senior Dev convention)

| Concern | Pattern đã chốt | Lý do |
|---|---|---|
| ErrorCode | Central enum `shared-kernel/.../ErrorCode.java` | Đã có sẵn, IAM đang dùng |
| i18n messages | 3 file `shared-web/src/main/resources/messages/messages*.properties` | Đã có sẵn, resolve qua `MessageResolver` |
| Module layering | `api` → `core` → `infrastructure` | Theo `STRUCTURE.md`, mũi tên phụ thuộc một chiều |
| Facade pattern | Interface ở `api/`, impl ở `core/service/` | Tránh "god service" như `IamFacadeImpl` 593 dòng |
| Storage abstraction | `StorageService` interface trong `shared-storage` | DRY, swap S3/Local bằng config |
| Async processing | Outbox + Kafka (giống notification) | Đồng bộ với pattern hiện có |
| PRO role check | `@PreAuthorize("hasRole('PRO')")` | Authority `ROLE_PRO` đã có sẵn |
| Ownership check | Filter ở repository method (e.g. `findByIdAndUserIdAndDeletedFalse`) | Không lộ logic ở controller |
| Audio validation | Magic byte + extension whitelist + size + duration | Chặt, chống bypass extension |
| Streaming | Presigned URL thuần, expiration 1h | MVP, không cần HLS/encryption |
| TTS cache | Redis (Spring Cache + Redis) | Tận dụng Redis đã có |

### 2.3 Tech debt phát hiện cần xử lý

| Vấn đề | Xử lý |
|---|---|
| `JwtAuthenticationFilter` không enforce `UserStatus` (BANNED/DELETED user vẫn dùng được token cũ) | **Prerequisite** — phải fix TRƯỚC khi ship voice. Task riêng, không thuộc voice scope. |
| `OutboxJpaWriter` hardcode `aggregateType="User"` | Refactor nhỏ khi voice cần publish event aggregate khác. Có thể làm song song với voice. |
| Chưa có `BaseEntity` chung | Mỗi module tự tạo `BaseEntity` riêng (MVP, không refactor IAM) |
| `.env` có nhiều config chưa dùng (MongoDB, Elasticsearch, AUDIO_AES_*, PWB_AUDIO_*) | Bỏ qua trong docs này (chưa rõ requirement) |

---

## 3. Module mới: shared-storage

### 3.1 Mục đích

| Lý do | Giải thích |
|---|---|
| **DRY** | Tránh copy-paste AWS SDK config vào mỗi module |
| **Consistency** | Một cách upload/download/presign duy nhất cho cả project |
| **Centralized Config** | Một chỗ quản lý bucket names, regions, expiration, retry |
| **Reusability** | Voice, IAM (avatar), Notification (attachment), Course (video) đều dùng chung |
| **Future-proof** | Mở rộng sang MinIO, GCS, Azure Blob chỉ cần thêm impl |

### 3.2 Package Structure: shared-storage

```
com.pwb.storage/
├── api/
│   ├── StorageService.java                # Interface chính
│   ├── StorageException.java              # Custom exception
│   └── dto/
│       ├── UploadResult.java              # (key, sizeBytes, contentType, etag)
│       ├── PresignedUrlResult.java        # (url, expiresAt)
│       └── ObjectMetadata.java            # (key, sizeBytes, contentType, lastModified)
│
├── core/
│   └── (chưa cần — interface đã đủ cho MVP)
│
└── infrastructure/
    ├── config/
    │   ├── StorageProperties.java         # @ConfigurationProperties("app.storage")
    │   ├── StorageProviderType.java       # enum S3, LOCAL
    │   └── StorageAutoConfig.java         # @Configuration + @ConditionalOnProperty
    ├── impl/
    │   ├── S3StorageServiceImpl.java      # @ConditionalOnProperty(provider=S3)
    │   └── LocalStorageServiceImpl.java   # @ConditionalOnProperty(provider=LOCAL)
    └── util/
        └── MediaTypeUtils.java            # MIME type detection (magic byte)
```

### 3.3 `StorageService` interface (full feature)

```java
public interface StorageService {
    UploadResult upload(String key, InputStream content, long sizeBytes, String contentType);
    UploadResult upload(String key, byte[] content, String contentType);
    InputStream download(String key);
    void delete(String key);
    void deleteAll(List<String> keys);
    boolean exists(String key);
    ObjectMetadata getMetadata(String key);
    URL generatePresignedUrl(String key, Duration expiration);
    URL generatePresignedUploadUrl(String key, String contentType, Duration expiration);
}
```

**Lý do chọn feature set:**
- `presignedUploadUrl` cho phép client upload trực tiếp lên S3, giảm tải backend cho file lớn (500MB).
- `deleteAll` batch để tránh N+1 khi xóa nhiều file.
- `getMetadata` tránh download toàn bộ file chỉ để check size/type.
- `exists` dùng cho idempotent check trước khi upload.

### 3.4 Dependency

```xml
<!-- shared-storage/pom.xml -->
<dependencies>
    <dependency>
        <groupId>com.pwb</groupId>
        <artifactId>shared-kernel</artifactId>
    </dependency>
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>s3</artifactId>
    </dependency>
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>s3-transfer-manager</artifactId>
    </dependency>
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>auth</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

---

## 4. Module mới: voice

### 4.1 Mục đích

Cung cấp nghiệp vụ quản lý voice tag (TTS + upload) và song (upload + watermark insertion) cho user PRO. Tích hợp Google Cloud TTS cho sinh audio, FFmpeg (Jaffree) cho chèn voice tag vào bài hát.

### 4.2 Package Structure: voice

Theo đúng convention trong `STRUCTURE.md` (`api` → `core` → `infrastructure`):

```
com.pwb.voice/
├── api/
│   ├── VoiceTagFacade.java                # Interface (entry point từ module khác)
│   ├── SongFacade.java                    # Interface
│   ├── dto/
│   │   ├── request/
│   │   │   ├── CreateTtsVoiceTagRequest.java
│   │   │   ├── UpdateVoiceTagRequest.java
│   │   │   ├── UploadSongRequest.java
│   │   │   ├── ConfigureVoiceTagRequest.java
│   │   │   └── UpdateSongRequest.java
│   │   └── response/
│   │       ├── VoiceTagResponse.java
│   │       ├── SongResponse.java
│   │       ├── SongDetailResponse.java
│   │       └── VoiceTagConfigResponse.java
│   └── enums/
│       ├── VoiceTagType.java              # TTS, UPLOADED
│       ├── SongStatus.java                # UPLOADED, PROCESSING, PROCESSED, FAILED
│       └── AudioFormat.java               # MP3, WAV, FLAC
│
├── core/
│   ├── model/
│   │   ├── BaseEntity.java                # Auditable base (created_at, updated_at, deleted, version)
│   │   ├── VoiceTag.java                  # Immutable domain entity
│   │   ├── Song.java
│   │   └── SongTagConfig.java
│   ├── service/
│   │   ├── VoiceTagFacadeImpl.java        # ⭐ Impl đặt ở core/service (KHÔNG ở infrastructure.web)
│   │   ├── SongFacadeImpl.java
│   │   ├── VoiceTagService.java           # Business logic riêng
│   │   ├── SongService.java
│   │   ├── TextToSpeechService.java
│   │   └── AudioProcessingService.java
│   └── exception/
│       └── (KHÔNG tạo VoiceErrorCode riêng — dùng chung ErrorCode ở shared-kernel)
│
└── infrastructure/
    ├── config/
    │   ├── VoiceJpaConfig.java
    │   ├── VoiceProperties.java            # @ConfigurationProperties("app.voice")
    │   ├── TtsProperties.java              # @ConfigurationProperties("app.voice.tts")
    │   ├── AudioProcessingProperties.java  # @ConfigurationProperties("app.voice.audio")
    │   └── VoiceSecurityConfig.java        # @EnableMethodSecurity
    ├── persistence/
    │   ├── entity/
    │   │   ├── VoiceTagJpaEntity.java
    │   │   ├── SongJpaEntity.java
    │   │   └── SongTagConfigJpaEntity.java
    │   ├── mapper/
    │   │   ├── VoiceTagMapper.java         # MapStruct
    │   │   └── SongMapper.java
    │   └── repository/
    │       ├── VoiceTagJpaRepository.java
    │       ├── SongJpaRepository.java
    │       └── SongTagConfigJpaRepository.java
    ├── tts/
    │   └── GoogleTtsServiceImpl.java
    ├── audio/
    │   ├── FFmpegAudioProcessingService.java
    │   └── AudioMetadataExtractor.java
    ├── processor/
    │   └── VoiceTagInsertionProcessor.java   # @KafkaListener (consumer)
    └── web/
        ├── VoiceTagController.java          # @PreAuthorize("hasRole('PRO')")
        └── SongController.java              # @PreAuthorize("hasRole('PRO')")
```

### 4.3 Lưu ý quan trọng về layout

- **Facade impl ở `core/service/`**, KHÔNG ở `infrastructure/web/facade/` như IAM. Lý do: tránh "god service" và đặt application orchestration đúng tầng (application service, không phải web adapter).
- **KHÔNG tạo `VoiceErrorCode` enum riêng** — bổ sung `VOICE_*` codes vào `shared-kernel/.../ErrorCode.java`.
- **Mỗi module tự có `BaseEntity`** (MVP) — copy pattern từ `IamJpaBaseEntity`.
- **Facade chỉ delegate/compose**, KHÔNG chứa toàn bộ business logic. Business logic nằm trong các service nhỏ (`VoiceTagService`, `SongService`...).

---

## 5. Module Dependencies

### 5.1 Dependency Graph (sau khi ship voice + shared-storage)

```
bootstrap
 ├── shared-kernel
 ├── shared-web
 ├── shared-storage          # NEW
 ├── iam
 ├── outbox
 ├── notification
 └── voice                   # NEW

shared-storage
 └── shared-kernel

iam
 ├── shared-kernel
 ├── shared-web
 ├── outbox
 └── shared-storage          # future: avatar storage

voice
 ├── shared-kernel
 ├── shared-web
 ├── shared-storage          # StorageService
 ├── iam                     # User reference, role check (qua facade)
 └── outbox                  # Publish processing event
```

### 5.2 Voice module dependency (chi tiết)

```xml
<!-- modules/voice/pom.xml -->
<dependencies>
    <dependency><groupId>com.pwb</groupId><artifactId>shared-kernel</artifactId></dependency>
    <dependency><groupId>com.pwb</groupId><artifactId>shared-web</artifactId></dependency>
    <dependency><groupId>com.pwb</groupId><artifactId>shared-storage</artifactId></dependency>
    <dependency><groupId>com.pwb</groupId><artifactId>iam</artifactId></dependency>
    <dependency><groupId>com.pwb</groupId><artifactId>outbox</artifactId></dependency>

    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-cache</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.kafka</groupId><artifactId>spring-kafka</artifactId></dependency>

    <dependency><groupId>com.google.cloud</groupId><artifactId>google-cloud-texttospeech</artifactId></dependency>
    <dependency><groupId>com.github.kokorin.jaffree</groupId><artifactId>jaffree</artifactId></dependency>

    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><scope>provided</scope></dependency>
    <dependency><groupId>org.mapstruct</groupId><artifactId>mapstruct</artifactId></dependency>
</dependencies>
```

---

## 6. Voice Module Architecture

### 6.1 Dependency Flow

```
┌──────────────────────────────────────────────────────────────┐
│                       API Layer                               │
│  VoiceTagController         SongController                    │
│  + @PreAuthorize("hasRole('PRO')")                            │
└────────────────────────────┬─────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────┐
│                     Facade Layer (api/)                       │
│  VoiceTagFacade            SongFacade                         │
└────────────────────────────┬─────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────┐
│                   Service Layer (core/)                       │
│  VoiceTagService  SongService  TextToSpeech  AudioProcessing  │
│  + FacadeImpl (delegate/compose)                              │
└──────┬─────────────┬──────────┬──────────────┬────────────────┘
       │             │          │              │
       ▼             ▼          ▼              ▼
┌──────────────────────────────────────────────────────────────┐
│                Infrastructure Layer                           │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐ │
│  │StorageService│  │ Google TTS   │  │  FFmpeg (Jaffree)  │ │
│  │(shared-stor) │  │   Service    │  │                    │ │
│  └──────────────┘  └──────────────┘  └────────────────────┘ │
│                          │                                    │
│  ┌───────────────────────▼─────────────────────────────────┐ │
│  │        Outbox + Kafka (VoiceTagInsertionProcessor)      │ │
│  └───────────────────────┬─────────────────────────────────┘ │
│                          │                                    │
│  ┌───────────────────────▼─────────────────────────────────┐ │
│  │                Repository Layer                          │ │
│  │  VoiceTagJpaRepo (findByIdAndUserIdAndDeletedFalse)     │ │
│  │  SongJpaRepo                                             │ │
│  │  SongTagConfigJpaRepo                                    │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────┬───────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────┐
│                  PostgreSQL + AWS S3                           │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 Ownership & Authorization Flow

```
1. Request đến controller (có JWT token)
2. JwtAuthenticationFilter verify token, load user + role từ DB
3. @PreAuthorize("hasRole('PRO')") check authority ROLE_PRO
4. Controller gọi Facade (chỉ truyền userId từ SecurityContext)
5. Service gọi repository với method filter userId:
   - findByIdAndIdAndUserIdAndDeletedFalse(id, userId)
   - existsByIdAndUserIdAndDeletedFalse(id, userId)
6. Nếu không tìm thấy → throw BusinessException(VOICE_TAG_NOT_FOUND)
   (KHÔNG trả 403 để tránh leak existence)
```

---

## 7. Chi tiết nghiệp vụ theo Phase

### Phase 0: shared-storage Module

**Mục tiêu**: Tạo module storage abstraction dùng chung.

**Deliverables**: `StorageService` interface + 2 impl (S3/Local) + auto-config.

### Phase 1: Voice Module Foundation

**Mục tiêu**: Setup voice module, migrations, error codes, security config.

**Deliverables**: Voice pom.xml + Flyway V8 + VOICE_* ErrorCode + VoiceSecurityConfig.

### Phase 2: Voice Tag CRUD

**Mục tiêu**: CRUD đầy đủ cho Voice Tag + Google TTS.

**Business Logic**:

1. **Create TTS Voice Tag**:
   ```
   1. Validate input (name, text ≤ 4000 chars, languageCode pattern)
   2. Compute cache key = SHA-256(text + "|" + languageCode + "|" + voiceName + "|" + speakingRate + "|" + pitch)
   3. Check Redis cache (Spring Cache @Cacheable)
   4. If cache miss → GoogleTtsService.synthesize() → byte[]
   5. Upload byte[] to S3: voice-tags/{userId}/{voiceTagId}.mp3
   6. Save VoiceTag to DB
   7. Return VoiceTagResponse (không trả audio trực tiếp — client gọi /audio để lấy presigned URL)
   ```

2. **Create Uploaded Voice Tag**:
   ```
   1. Validate audio file:
      - Extension ∈ {mp3, wav, flac}
      - Magic byte match extension
      - Size ≤ 500MB
      - Duration ≤ 10 minutes (parse qua FFprobe)
   2. Upload to S3
   3. Save VoiceTag to DB
   ```

3. **Update / Delete / List**: standard CRUD với ownership check.

### Phase 3: Song Management

**Mục tiêu**: CRUD cho Song + Voice Tag Configuration.

**Song Upload Flow**:

```
1. Validate file (extension + magic byte + size + duration)
2. Upload to S3: songs/{userId}/original/{songId}.{format}
3. Save Song with status = UPLOADED
4. Return SongResponse
```

**Voice Tag Configuration (Optional, 1:1 với Song)**:

```
1. User gọi POST /songs/{songId}/voice-tag với { voiceTagId, intervalSeconds, volumePercentage, fadeInMs, fadeOutMs }
2. Validate song thuộc user (repository filter)
3. Validate voice tag thuộc user
4. Upsert SongTagConfig (UNIQUE constraint trên song_id → dùng INSERT ... ON CONFLICT)
5. KHÔNG tự động xử lý — user phải trigger qua /process
```

### Phase 4: Audio Processing (Async)

**Mục tiêu**: FFmpeg integration, async processing qua outbox + Kafka.

**Trigger Flow**:

```
1. User gọi POST /songs/{songId}/process
2. Validate song.status == UPLOADED (hoặc FAILED để retry)
3. Set song.status = PROCESSING (optimistic lock qua @Version)
4. Publish VoiceProcessingRequestedIntegrationEvent qua outbox
5. Return ngay với status = PROCESSING
```

**Processing Flow (Consumer)**:

```
1. @KafkaListener nhận VoiceProcessingRequestedIntegrationEvent
2. Load Song + SongTagConfig + VoiceTag
3. Download original từ S3: originalS3Key → temp file
4. Download voice tag từ S3: voiceTagS3Key → temp file
5. Validate: intervalSeconds > voiceTag.duration + max(fadeIn, fadeOut)
6. FFmpeg insert voice tag tại mỗi interval (atomicaminemix)
7. Upload processed to S3: songs/{userId}/processed/{songId}.{format}
8. Update Song: status = PROCESSED, processedS3Key = ...
9. Cleanup temp files
10. Nếu fail → status = FAILED + lastError, retry theo outbox policy
```

**FFmpeg Command (Jaffree wrapper)**:

```java
// Pseudo-code
FFmpegResult result = FFmpeg.atPath(tempDir)
    .addInput("-i", originalSongPath)
    .addInput("-i", voiceTagPath)
    .addInput("-filter_complex",
        "[1:a]volume={volume}[tag];" +
        "[0:a][tag]amix=inputs=2:duration=first[out]")
    .addOutput("-map", "[out]", processedSongPath)
    .execute();
```

**Interval Mixing Logic** (cần thiết kế riêng — chèn voice tag tại các mốc 25s, 50s, ...):

```java
// Dùng FFmpeg concat filter hoặc adelay filter
// Step 1: Slice voice tag audio thành N đoạn theo interval
// Step 2: Đặt mỗi đoạn tại timestamp tương ứng
// Step 3: Mix với original qua amix filter
```

### Phase 5: Streaming & Polish

**Streaming Endpoint**:

```java
@GetMapping("/{id}/stream")
public ResponseEntity<Void> streamSong(@PathVariable UUID id, Authentication auth) {
    UUID userId = currentUserId(auth);
    Song song = songRepository.findByIdAndIdAndUserIdAndDeletedFalse(id, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.SONG_NOT_FOUND));
    
    String s3Key = song.getProcessedS3Key() != null
        ? song.getProcessedS3Key()
        : song.getOriginalS3Key();
    
    URL presignedUrl = storageService.generatePresignedUrl(s3Key, Duration.ofHours(1));
    return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, presignedUrl.toString())
            .build();
}
```

**PRO Role Check**: Áp dụng `@PreAuthorize("hasRole('PRO')")` ở class-level trên `VoiceTagController` và `SongController`.

---

## 8. API Endpoints

### 8.1 Voice Tags

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/voice-tags/tts` | PRO | Tạo TTS voice tag |
| POST | `/api/v1/voice-tags/upload` | PRO | Upload voice tag audio |
| GET | `/api/v1/voice-tags` | PRO | List user's voice tags (paginated) |
| GET | `/api/v1/voice-tags/{id}` | PRO | Get voice tag detail |
| PUT | `/api/v1/voice-tags/{id}` | PRO | Update voice tag |
| DELETE | `/api/v1/voice-tags/{id}` | PRO | Soft delete voice tag |
| GET | `/api/v1/voice-tags/{id}/audio` | PRO | Get presigned audio URL |

### 8.2 Songs

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/songs/upload` | PRO | Upload song |
| GET | `/api/v1/songs` | PRO | List user's songs (paginated) |
| GET | `/api/v1/songs/{id}` | PRO | Get song detail |
| PUT | `/api/v1/songs/{id}` | PRO | Update song metadata |
| DELETE | `/api/v1/songs/{id}` | PRO | Soft delete song + S3 cleanup |

### 8.3 Voice Tag Configs (per Song)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/songs/{songId}/voice-tag` | PRO | Configure voice tag for song |
| GET | `/api/v1/songs/{songId}/voice-tag` | PRO | Get song's voice tag config |
| PUT | `/api/v1/songs/{songId}/voice-tag` | PRO | Update voice tag config |
| DELETE | `/api/v1/songs/{songId}/voice-tag` | PRO | Remove voice tag config |

### 8.4 Processing & Streaming

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/songs/{songId}/process` | PRO | Trigger voice tag insertion (async) |
| GET | `/api/v1/songs/{songId}/status` | PRO | Get processing status |
| GET | `/api/v1/songs/{id}/stream` | PRO | Get presigned URL (processed hoặc original fallback) |
| GET | `/api/v1/songs/{id}/original` | PRO | Get presigned URL (original) |

---

## 9. Database Schema

### 9.1 Flyway Migration: `V8__create_voice_tables.sql`

```sql
CREATE TABLE voice_voice_tags (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by       VARCHAR(255) NOT NULL DEFAULT 'system',
    deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMPTZ,
    version          BIGINT NOT NULL DEFAULT 0,

    user_id          UUID NOT NULL REFERENCES iam_users(id) ON DELETE RESTRICT,
    name             VARCHAR(128) NOT NULL,
    description      VARCHAR(512),
    tag_type         VARCHAR(32) NOT NULL,                 -- TTS, UPLOADED
    source_text      VARCHAR(4000),                        -- For TTS type
    language_code    VARCHAR(10),
    s3_key           VARCHAR(512) NOT NULL,
    duration_seconds INTEGER,
    file_size_bytes  BIGINT,
    is_default       BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT uk_voice_tags_user_name UNIQUE (user_id, name)
);

CREATE TABLE voice_songs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by        VARCHAR(255) NOT NULL DEFAULT 'system',
    deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMPTZ,
    version           BIGINT NOT NULL DEFAULT 0,

    user_id           UUID NOT NULL REFERENCES iam_users(id) ON DELETE RESTRICT,
    title             VARCHAR(256) NOT NULL,
    artist            VARCHAR(256),
    album             VARCHAR(256),
    original_s3_key   VARCHAR(512) NOT NULL,
    processed_s3_key  VARCHAR(512),
    file_size_bytes   BIGINT NOT NULL,
    duration_seconds  INTEGER,
    format            VARCHAR(16) NOT NULL,
    status            VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',  -- UPLOADED, PROCESSING, PROCESSED, FAILED
    thumbnail_url     VARCHAR(512),
    last_error        VARCHAR(2048)
);

CREATE TABLE voice_song_tag_configs (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by           VARCHAR(255) NOT NULL DEFAULT 'system',
    deleted              BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at           TIMESTAMPTZ,
    version              BIGINT NOT NULL DEFAULT 0,

    song_id              UUID NOT NULL REFERENCES voice_songs(id) ON DELETE CASCADE,
    voice_tag_id         UUID NOT NULL REFERENCES voice_voice_tags(id) ON DELETE RESTRICT,
    interval_seconds     INTEGER NOT NULL DEFAULT 25 CHECK (interval_seconds > 0),
    volume_percentage    INTEGER NOT NULL DEFAULT 50 CHECK (volume_percentage BETWEEN 0 AND 100),
    fade_in_duration_ms  INTEGER NOT NULL DEFAULT 500 CHECK (fade_in_duration_ms >= 0),
    fade_out_duration_ms INTEGER NOT NULL DEFAULT 500 CHECK (fade_out_duration_ms >= 0),
    start_offset_seconds INTEGER NOT NULL DEFAULT 0 CHECK (start_offset_seconds >= 0),
    enabled              BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT uk_song_tag_config_song UNIQUE (song_id)
);

CREATE INDEX ix_voice_tags_user_id      ON voice_voice_tags (user_id);
CREATE INDEX ix_voice_tags_tag_type     ON voice_voice_tags (tag_type);
CREATE INDEX ix_voice_songs_user_id     ON voice_songs (user_id);
CREATE INDEX ix_voice_songs_status      ON voice_songs (status);
CREATE INDEX ix_voice_songs_user_status ON voice_songs (user_id, status);
CREATE INDEX ix_song_tag_configs_song_id ON voice_song_tag_configs (song_id);
```

---

## 10. Cấu hình

### 10.1 `application.yml` (bổ sung)

```yaml
app:
  storage:
    provider: ${STORAGE_PROVIDER:S3}                    # S3 | LOCAL
    s3:
      bucket: ${STORAGE_S3_BUCKET:pwb-storage}
      region: ${STORAGE_S3_REGION:ap-southeast-1}
      access-key: ${STORAGE_S3_ACCESS_KEY:}
      secret-key: ${STORAGE_S3_SECRET_KEY:}
      endpoint: ${STORAGE_S3_ENDPOINT:}                  # MinIO local
      path-style-access: ${STORAGE_S3_PATH_STYLE:true}   # true cho MinIO
      presigned-url-expiration-minutes: 60
      multipart-upload-threshold-bytes: 26214400         # 25MB
    local:
      base-path: ${STORAGE_LOCAL_BASE_PATH:/tmp/pwb-storage}
    max-file-size-bytes: 524288000                        # 500MB

  voice:
    tts:
      credentials-path: ${GCP_TTS_CREDENTIALS_PATH:}
      default-language: en-US
      default-voice-name: en-US-Standard-A
      speaking-rate: 1.0
      pitch: 0.0
      cache-ttl-hours: 168                                # 7 days
      cache-key-prefix: "voice:tts:"
    audio:
      temp-dir: ${VOICE_TEMP_DIR:/tmp/voice-processing}
      processing-timeout-minutes: 30
      max-duration-seconds: 600                           # 10 minutes
      max-file-size-bytes: 524288000                      # 500MB
      allowed-formats: MP3,WAV,FLAC
    storage:
      voice-tags-prefix: voice-tags
      songs-original-prefix: songs/original
      songs-processed-prefix: songs/processed
      key-template: "{prefix}/{userId}/{id}.{format}"

spring:
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB
  cache:
    type: redis
    redis:
      time-to-live: 604800000                             # 7 days
      cache-null-values: false
  kafka:
    consumer:
      group-id: voice-processor
      auto-offset-reset: earliest
```

### 10.2 Kafka Topic

| Topic | Producer | Consumer | Payload |
|---|---|---|---|
| `voice.processing.v1` | voice (qua outbox) | voice (VoiceTagInsertionProcessor) | `VoiceProcessingRequestedIntegrationEvent` |

### 10.3 S3 Storage Structure

```
pwb-storage/
├── voice-tags/
│   └── {userId}/
│       └── {voiceTagId}.mp3
├── songs/
│   └── {userId}/
│       ├── original/
│       │   └── {songId}.{mp3|wav|flac}
│       └── processed/
│           └── {songId}.{mp3|wav|flac}
```

### 10.4 Environment Variables (bổ sung)

```bash
# Storage
STORAGE_PROVIDER=S3
STORAGE_S3_BUCKET=pwb-storage
STORAGE_S3_REGION=ap-southeast-1
STORAGE_S3_ACCESS_KEY=xxx
STORAGE_S3_SECRET_KEY=xxx
STORAGE_S3_ENDPOINT=                       # Để trống cho AWS thật, set cho MinIO local
STORAGE_S3_PATH_STYLE=true                 # true cho MinIO

# Google TTS
GCP_TTS_CREDENTIALS_PATH=/path/to/sa.json
GCP_PROJECT_ID=pwb-xxx

# Voice
VOICE_TEMP_DIR=/tmp/voice-processing
```

---

## 11. Security Checklist

| # | Mục | Status | Ghi chú |
|---|---|---|---|
| 1 | `@PreAuthorize("hasRole('PRO')")` ở controller | ✅ Plan | Authority `ROLE_PRO` đã có |
| 2 | Ownership check ở repository method | ✅ Plan | `findByIdAndUserIdAndDeletedFalse(...)` |
| 3 | Streaming endpoint check ownership trước khi presign | ✅ Plan | Tránh user A lấy presigned URL của user B |
| 4 | Audio validation: extension + magic byte + size + duration | ✅ Plan | Chống bypass extension |
| 5 | File upload size limit (500MB) | ✅ Plan | `spring.servlet.multipart.max-file-size` |
| 6 | TTS cache key include tất cả config | ✅ Plan | `hash(text + lang + voice + rate + pitch)` |
| 7 | Presigned URL expiration 1h | ✅ Plan | MVP, đủ cho streaming |
| 8 | Error response không leak existence | ✅ Plan | Trả 404 thay vì 403 |
| 9 | Log không in audio content / presigned URL | ✅ Plan | Chỉ log key, size, duration |
| 10 | **Fix JwtAuthenticationFilter enforce UserStatus** | ⚠️ **PREREQUISITE** | **Phải fix TRƯỚC khi ship voice** |
| 11 | S3 bucket policy private + presigned only | ✅ Plan | Block direct public access |
| 12 | Cleanup temp files sau processing | ✅ Plan | Tránh disk full |
| 13 | Rate limit upload endpoint | ⏳ Optional | Theo pattern `RateLimited` annotation có sẵn |

---

## 12. Prerequisites

Trước khi bắt đầu implement voice, cần:

| # | Prerequisite | Owner | Status |
|---|---|---|---|
| 1 | AWS S3 bucket `pwb-storage` (hoặc MinIO local) | DevOps | ⏳ TODO |
| 2 | Google Cloud TTS service account + credentials file | DevOps | ⏳ TODO |
| 3 | PRO role đã define trong database (V4 seed) | ✅ Done | Migration V4 đã seed |
| 4 | `application.yml` với đầy đủ config | Phần trong docs | ✅ Plan |
| 5 | **`JwtAuthenticationFilter` enforce UserStatus** | Backend | ⚠️ **PREREQUISITE — phải fix trước** |
| 6 | `OutboxJpaWriter` refactor `aggregateType` thành parameter | Backend | ⏳ Optional — chỉ cần nếu voice event aggregate khác "User" |

---

## 13. Timeline

```
Week 1:
├── Day 1: TASK 1 (shared-storage)
├── Day 2: TASK 2, 3 (Voice setup + migrations)
├── Day 3: TASK 4 (Error codes)
└── Day 4-5: TASK 5-7 (Voice Tag domain + TTS)

Week 2:
├── Day 1-2: TASK 8-9 (Voice Tag service + controller)
├── Day 3-4: TASK 10-11 (Song domain + DTOs)
└── Day 5: TASK 12-13 (Song service + controller)

Week 3:
├── Day 1-2: TASK 14 (Audio processing)
├── Day 3-4: TASK 15-16 (Async processor + trigger)
└── Day 5: TASK 17 (PRO role check)

Week 4:
├── Day 1-2: TASK 18 (Error handling + i18n)
└── Day 3-5: TASK 19-20 (Integration + Polish)

Week 5:
└── Buffer / bug fixes / PR review
```

**Tổng: 5 tuần (~25 ngày làm việc)**

> **Lưu ý**: Nếu muốn ship MVP nhanh, có thể cắt Phase 4 (audio processing) ra sprint sau và chỉ ship Phase 0-3 + 5 trong 3 tuần. Voice Tag CRUD độc lập với Audio Processing.

---

## Tham chiếu

- Chi tiết task-by-task: [voice-implementation-tasks.md](./voice-implementation-tasks.md)
- Convention cấu trúc module: [`Backend/STRUCTURE.md`](../../Backend/STRUCTURE.md)
