# Voice Module - Kế hoạch triển khai

> **Ngày tạo**: 2026-07-19
> **Người tạo**: AI Assistant
> **Trạng thái**: Final - Đã khảo sát nghiệp vụ

---

## Mục lục

1. [Tổng quan nghiệp vụ](#1-tổng-quan-nghiệp-vụ)
2. [Kiến trúc mới: shared-storage](#2-kiến-trúc-mới-shared-storage)
3. [Module Dependencies](#3-module-dependencies)
4. [Voice Module Architecture](#4-voice-module-architecture)
5. [Chi tiết từng Phase](#5-chi-tiết-từng-phase)
6. [API Endpoints](#6-chi-tiết-api-endpoints)
7. [Database Schema](#7-database-schema)
8. [Cấu hình](#8-cấu-hình)
9. [Timeline](#9-timeline)

---

## 1. Tổng quan nghiệp vụ

### Kết quả khảo sát

| Nghiệp vụ | Chi tiết | Ghi chú |
|------------|----------|---------|
| **Voice tag** | User tự tạo watermark riêng | Không có system default |
| **Song source** | Upload từ máy | MP3, WAV, FLAC |
| **TTS provider** | Google Cloud TTS | Bắt buộc |
| **Processing trigger** | User tùy chọn | Không chọn tag → không chèn |
| **Multiple tags** | 1 voice tag/bài hát | Không hỗ trợ nhiều tags |
| **Storage** | Giữ cả original + processed | Original để user download |
| **Access control** | Chỉ PRO users | Role check ở controller |
| **Audio format** | Giữ nguyên format gốc | Không convert |
| **FFmpeg** | Docker container (Jaffree) | Jaffree tự download binary |
| **Playback** | Streaming only | Presigned URL |
| **TTS cache** | Có cache | Hash-based key |
| **Preview** | Sau khi lưu | Không preview trước |

### Business Rules

1. **Access Control**: Chỉ user có role `PRO` mới được dùng voice module
2. **Optional Processing**: User upload song → không bắt buộc gắn voice tag
3. **1:1 Relationship**: Mỗi song có tối đa 1 voice tag config
4. **Storage Policy**: Giữ cả file gốc và file đã xử lý
5. **TTS Optimization**: Cache TTS audio theo hash(text + languageCode + config)

---

## 2. Kiến trúc mới: shared-storage

### 2.1 Mục đích

| Lý do | Giải thích |
|-------|------------|
| **DRY** | Tránh copy-paste S3 configuration vào mỗi module |
| **Consistency** | Một cách upload/download/presign duy nhất |
| **Centralized Config** | Một chỗ quản lý bucket names, regions, expiration |
| **Reusability** | Module nào cần storage chỉ cần `import shared-storage` |
| **Future-proof** | Voice, Notification, IAM, Course đều cần storage |

### 2.2 Package Structure: shared-storage

```
com.pwb.storage/
├── api/
│   ├── StorageService.java           # Interface chính
│   ├── StorageException.java         # Custom exception
│   └── dto/
│       ├── UploadResult.java         # Kết quả upload
│       └── PresignedUrlResult.java   # Kết quả presigned URL
│
├── infrastructure/
│   ├── config/
│   │   ├── StorageProperties.java    # @ConfigurationProperties
│   │   └── S3Config.java            # AWS S3 Client bean
│   ├── impl/
│   │   ├── S3StorageServiceImpl.java       # Prod: AWS S3
│   │   └── LocalStorageServiceImpl.java     # Dev/Test: Local filesystem
│   └── util/
│       └── MediaTypeUtils.java       # MIME type utilities
```

---

## 3. Module Dependencies

### 3.1 Dependency Graph

```
bootstrap
 ├── shared-kernel
 ├── shared-web
 ├── shared-storage     # NEW
 ├── iam
 ├── outbox
 └── notification

shared-storage
 └── shared-kernel

iam
 ├── shared-kernel
 ├── shared-web
 ├── outbox
 └── shared-storage    # Future: avatar storage

voice
 ├── shared-kernel
 ├── shared-web
 ├── shared-storage    # Sử dụng StorageService
 ├── iam               # User reference, PRO role check
 └── outbox            # Event publishing
```

---

## 4. Voice Module Architecture

### 4.1 Package Structure

```
com.pwb.voice/
├── api/
│   ├── VoiceTagFacade.java           # Interface
│   ├── SongFacade.java               # Interface
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
│       ├── VoiceTagType.java
│       └── SongStatus.java
│
├── core/
│   ├── model/
│   │   ├── VoiceTag.java             # Immutable domain entity
│   │   ├── Song.java
│   │   ├── SongTagConfig.java
│   │   └── BaseEntity.java
│   ├── service/
│   │   ├── VoiceTagService.java
│   │   ├── SongService.java
│   │   ├── TextToSpeechService.java
│   │   └── AudioProcessingService.java
│   └── exception/
│       └── VoiceErrorCode.java
│
├── infrastructure/
│   ├── config/
│   │   ├── VoiceJpaConfig.java
│   │   ├── VoiceProperties.java
│   │   └── TtsConfig.java
│   ├── persistence/
│   │   ├── entity/
│   │   │   ├── VoiceTagJpaEntity.java
│   │   │   ├── SongJpaEntity.java
│   │   │   └── SongTagConfigJpaEntity.java
│   │   ├── repository/
│   │   │   ├── VoiceTagJpaRepository.java
│   │   │   ├── SongJpaRepository.java
│   │   │   └── SongTagConfigJpaRepository.java
│   │   └── mapper/
│   │       ├── VoiceTagMapper.java
│   │       └── SongMapper.java
│   ├── storage/
│   │   └── (sử dụng StorageService từ shared-storage)
│   ├── tts/
│   │   └── GoogleTtsServiceImpl.java
│   ├── audio/
│   │   └── FFmpegAudioProcessingService.java
│   ├── processor/
│   │   ├── VoiceTagInsertionProcessor.java
│   │   └── VoiceProcessingConfig.java
│   └── web/
│       ├── VoiceTagController.java
│       ├── SongController.java
│       └── facade/
│           ├── VoiceTagFacadeImpl.java
│           └── SongFacadeImpl.java
```

### 4.2 Dependency Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    API Layer                                │
│  VoiceTagController          SongController                  │
└─────────────────────────────┬───────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────┐
│                    Facade Layer                             │
│  VoiceTagFacade               SongFacade                    │
└─────────────────────────────┬───────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────┐
│                   Service Layer                             │
│  VoiceTagService  SongService  TextToSpeech  AudioProcess   │
└──────┬─────────────┬──────────┬──────────────┬─────────────┘
       │             │          │              │
       │             │          │              │
       ▼             ▼          ▼              ▼
┌─────────────────────────────────────────────────────────────┐
│                Infrastructure Layer                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │StorageService│  │ Google TTS  │  │  FFmpeg (Jaffree)   │ │
│  │(shared-stor)│  │   Service   │  │                     │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
│                          │                                  │
│  ┌───────────────────────▼───────────────────────────────┐ │
│  │              Repository Layer                          │ │
│  │  VoiceTagJpaRepo  SongJpaRepo  SongTagConfigJpaRepo   │ │
│  └───────────────────────┬───────────────────────────────┘ │
│                          │                                  │
└───────────────────────────▼────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────┐
│              PostgreSQL + AWS S3                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. Chi tiết từng Phase

### Phase 0: shared-storage Module

**Mục tiêu**: Tạo module dùng chung cho S3/Local storage

**Files cần tạo:**
- `shared-storage/pom.xml`
- `StorageService.java` (interface)
- `StorageException.java`
- `StorageProperties.java`
- `S3Config.java`
- `S3StorageServiceImpl.java`
- `LocalStorageServiceImpl.java`

**Config:**
```yaml
app:
  storage:
    provider: S3  # or LOCAL
    s3:
      bucket: ${STORAGE_S3_BUCKET:pwb-storage}
      region: ${STORAGE_S3_REGION:ap-southeast-1}
    local:
      base-path: /tmp/pwb-storage
```

---

### Phase 1: Voice Module Foundation

**Mục tiêu**: Setup voice module, migrations, error codes

**Files cần tạo:**
- `modules/voice/pom.xml`
- `VoiceJpaConfig.java`
- `VoiceProperties.java`
- `TtsConfig.java`
- `V8__create_voice_tables.sql`
- `VoiceErrorCode.java`

---

### Phase 2: Voice Tag CRUD

**Mục tiêu**: CRUD đầy đủ cho Voice Tag + Google TTS

**Files cần tạo:**
- Domain models: `VoiceTag`, `VoiceTagType`
- JPA entity: `VoiceTagJpaEntity`
- Mapper: `VoiceTagMapper`
- Repository: `VoiceTagJpaRepository`
- DTOs: `CreateTtsVoiceTagRequest`, `VoiceTagResponse`
- Service: `TextToSpeechService`, `VoiceTagService`
- Facade: `VoiceTagFacade`, `VoiceTagFacadeImpl`
- Controller: `VoiceTagController`

**Business Logic:**
1. **Create TTS Voice Tag:**
   - Validate input (name, text, languageCode)
   - Check TTS cache (hash-based key)
   - If cache miss → call Google TTS API
   - Save audio to S3 via StorageService
   - Save VoiceTag to database
   - Return presigned URL for preview

2. **Create Uploaded Voice Tag:**
   - Validate audio file (format, size)
   - Upload to S3
   - Save VoiceTag to database

---

### Phase 3: Song Management

**Mục tiêu**: CRUD cho Song + Voice Tag Configuration

**Files cần tạo:**
- Domain models: `Song`, `SongStatus`, `SongTagConfig`
- JPA entities + Mappers + Repositories
- DTOs: `SongResponse`, `SongDetailResponse`, `ConfigureVoiceTagRequest`
- Service: `SongService`
- Facade: `SongFacade`, `SongFacadeImpl`
- Controller: `SongController`

**Song Upload Flow:**
1. Validate file (format: MP3/WAV/FLAC, size: max 500MB)
2. Upload to S3: `songs/{userId}/original/{uuid}.{format}`
3. Extract audio metadata (duration, format)
4. Save Song to database with status `UPLOADED`
5. Return SongResponse

**Voice Tag Configuration (Optional):**
1. User selects a VoiceTag
2. User sets: interval, volume, fade in/out
3. Save SongTagConfig to database
4. **NOT** auto-processing - user must trigger

---

### Phase 4: Audio Processing

**Mục tiêu**: FFmpeg integration để chèn voice tag

**Files cần tạo:**
- `AudioProcessingService.java` (interface)
- `FFmpegAudioProcessingService.java`
- `VoiceTagInsertionProcessor.java`
- `VoiceProcessingConfig.java`

**Processing Algorithm:**
```
Input:
  - song.mp3 (original)
  - voice_tag.mp3
  - interval = 25 seconds
  - volume = 50%
  - fade_in = 500ms, fade_out = 500ms

Process:
  1. Download song from S3
  2. Download voice tag from S3
  3. For position = 0, 25, 50, 75, ... < song_duration:
     a. At position, mix voice_tag at volume% with fade
  4. Upload processed song to S3: `songs/{userId}/processed/{uuid}.mp3`
  5. Update database: status = PROCESSED, processed_s3_key

Output:
  - Processed song in S3
  - Song.status = PROCESSED
```

**Async Processing:**
- Use `@Async` with ThreadPoolExecutor
- Pool size: 2 core, 4 max
- Queue capacity: 100
- Status updates: UPLOADED → PROCESSING → PROCESSED/FAILED

---

### Phase 5: Streaming & PRO Access

**Mục tiêu**: Streaming endpoints + PRO role check

**Streaming Endpoint:**
```java
@GetMapping("/{id}/stream")
public ResponseEntity<Void> streamSong(...) {
    String s3Key = song.getProcessedS3Key() != null
        ? song.getProcessedS3Key()
        : song.getOriginalS3Key();
    
    URL presignedUrl = storageService.generatePresignedUrl(s3Key, Duration.ofHours(1));
    return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, presignedUrl.toString())
            .build();
}
```

**PRO Role Check:**
```java
@PreAuthorize("hasRole('PRO')")
// Applied to all voice module controllers
```

---

## 6. Chi tiết API Endpoints

### Voice Tags

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/voice-tags/tts` | Create TTS voice tag |
| POST | `/api/v1/voice-tags/upload` | Upload voice tag audio |
| GET | `/api/v1/voice-tags` | List user's voice tags |
| GET | `/api/v1/voice-tags/{id}` | Get voice tag detail |
| PUT | `/api/v1/voice-tags/{id}` | Update voice tag |
| DELETE | `/api/v1/voice-tags/{id}` | Delete voice tag |
| GET | `/api/v1/voice-tags/{id}/audio` | Get presigned audio URL |

### Songs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/songs/upload` | Upload song |
| GET | `/api/v1/songs` | List user's songs (paginated) |
| GET | `/api/v1/songs/{id}` | Get song detail |
| PUT | `/api/v1/songs/{id}` | Update song metadata |
| DELETE | `/api/v1/songs/{id}` | Delete song |
| GET | `/api/v1/songs/{id}/stream` | Get presigned URL (processed) |
| GET | `/api/v1/songs/{id}/original` | Get presigned URL (original) |

### Voice Tag Configs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/songs/{songId}/voice-tag` | Configure voice tag for song |
| GET | `/api/v1/songs/{songId}/voice-tag` | Get song's voice tag config |
| PUT | `/api/v1/songs/{songId}/voice-tag` | Update voice tag config |
| DELETE | `/api/v1/songs/{songId}/voice-tag` | Remove voice tag config |
| POST | `/api/v1/songs/{songId}/process` | Trigger voice tag insertion |
| GET | `/api/v1/songs/{songId}/status` | Get processing status |

---

## 7. Database Schema

### Tables

```sql
-- voice_voice_tags
CREATE TABLE voice_voice_tags (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    created_by      VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by      VARCHAR(255) NOT NULL DEFAULT 'system',
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,
    
    user_id         UUID NOT NULL REFERENCES iam_users(id),
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    tag_type        VARCHAR(32) NOT NULL,  -- TTS, UPLOADED
    source_text     VARCHAR(4000),           -- For TTS type
    language_code   VARCHAR(10),
    s3_key          VARCHAR(512) NOT NULL,
    duration_seconds INTEGER,
    file_size_bytes BIGINT,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    
    CONSTRAINT uk_voice_tags_user_name UNIQUE (user_id, name)
);

-- voice_songs
CREATE TABLE voice_songs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    created_by       VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by       VARCHAR(255) NOT NULL DEFAULT 'system',
    deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMPTZ,
    version          BIGINT NOT NULL DEFAULT 0,
    
    user_id          UUID NOT NULL REFERENCES iam_users(id),
    title            VARCHAR(256) NOT NULL,
    artist           VARCHAR(256),
    album            VARCHAR(256),
    original_s3_key  VARCHAR(512) NOT NULL,
    processed_s3_key VARCHAR(512),
    file_size_bytes  BIGINT NOT NULL,
    duration_seconds INTEGER,
    format           VARCHAR(16) NOT NULL,
    status           VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    thumbnail_url    VARCHAR(512)
);

-- voice_song_tag_configs
CREATE TABLE voice_song_tag_configs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    created_by          VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by          VARCHAR(255) NOT NULL DEFAULT 'system',
    deleted             BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    
    song_id             UUID NOT NULL REFERENCES voice_songs(id),
    voice_tag_id        UUID NOT NULL REFERENCES voice_voice_tags(id),
    interval_seconds    INTEGER NOT NULL DEFAULT 25,
    volume_percentage   INTEGER NOT NULL DEFAULT 50,
    fade_in_duration_ms INTEGER NOT NULL DEFAULT 500,
    fade_out_duration_ms INTEGER NOT NULL DEFAULT 500,
    start_offset_seconds INTEGER NOT NULL DEFAULT 0,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    
    CONSTRAINT uk_song_tag_config_song UNIQUE (song_id)
);
```

### Indexes

```sql
CREATE INDEX ix_voice_tags_user_id ON voice_voice_tags (user_id);
CREATE INDEX ix_voice_tags_tag_type ON voice_voice_tags (tag_type);
CREATE INDEX ix_voice_songs_user_id ON voice_songs (user_id);
CREATE INDEX ix_voice_songs_status ON voice_songs (status);
CREATE INDEX ix_song_tag_configs_song_id ON voice_song_tag_configs (song_id);
```

---

## 8. Cấu hình

### application.yml

```yaml
app:
  storage:
    provider: ${STORAGE_PROVIDER:S3}
    s3:
      bucket: ${STORAGE_S3_BUCKET:pwb-storage}
      region: ${STORAGE_S3_REGION:ap-southeast-1}
      access-key: ${STORAGE_S3_ACCESS_KEY:}
      secret-key: ${STORAGE_S3_SECRET_KEY:}
      endpoint: ${STORAGE_S3_ENDPOINT:}
      path-style-access: true
      max-file-size-bytes: 524288000  # 500MB
    local:
      base-path: /tmp/pwb-storage
  
  voice:
    tts:
      credentials-path: ${GOOGLE_APPLICATION_CREDENTIALS:}
      default-language: en-US
      default-voice-name: en-US-Standard-A
      speaking-rate: 1.0
      pitch: 0.0
      cache-ttl-hours: 168  # 7 days
    audio:
      ffmpeg-path: ffmpeg  # Jaffree tự download nếu không có
      temp-dir: ${TEMP_DIR:/tmp/voice-processing}
      processing-timeout-minutes: 30
      max-duration-seconds: 600  # 10 minutes
    storage:
      voice-tags-prefix: voice-tags
      songs-prefix: songs
```

### S3 Storage Structure

```
pwb-storage/
├── voice-tags/
│   └── {userId}/
│       └── {voiceTagId}.mp3
├── songs/
│   └── {userId}/
│       ├── original/
│       │   └── {uuid}.mp3
│       └── processed/
│           └── {uuid}.mp3
```

### Environment Variables

```bash
# Storage (S3)
STORAGE_PROVIDER=S3
STORAGE_S3_BUCKET=pwb-storage
STORAGE_S3_REGION=ap-southeast-1
STORAGE_S3_ACCESS_KEY=AKIA...
STORAGE_S3_SECRET_KEY=xxx
STORAGE_S3_ENDPOINT=

# Google TTS
GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json

# Temp
TEMP_DIR=/tmp/voice-processing
```

---

## 9. Timeline

| Week | Tasks | Deliverables |
|------|-------|--------------|
| **Week 1** | Phase 0: shared-storage | StorageService dùng chung |
| | Phase 1: Voice setup + migrations | Voice module structure |
| **Week 2** | Phase 2: Voice Tag CRUD | Full Voice Tag API |
| | | TTS integration |
| **Week 3** | Phase 3: Song Management | Song upload + config |
| **Week 4** | Phase 4: Audio Processing | FFmpeg integration |
| | | Async processing |
| **Week 5** | Phase 5: Integration | PRO check |
| | | Streaming |
| | | Tests + Polish |

**Tổng: 5 tuần (~25 ngày làm việc)**

---

## References

- Chi tiết implementation: [voice-implementation-tasks.md](./voice-implementation-tasks.md)
