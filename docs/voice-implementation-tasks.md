# Voice Module - Implementation Tasks

> **Ngày tạo**: 2026-07-19
> **Dựa trên**: voice-module-plan.md
> **Trạng thái**: Ready for Implementation

---

## Tổng quan nghiệp vụ

| Nghiệp vụ | Chi tiết |
|------------|----------|
| Voice tag | User tự tạo watermark riêng (TTS hoặc upload audio) |
| Song source | Upload từ máy (MP3, WAV, FLAC) |
| TTS provider | Google Cloud TTS |
| Processing | User tùy chọn - không chọn tag thì không chèn |
| Multiple tags | 1 voice tag cho mỗi bài hát |
| Storage | Giữ cả original + processed |
| Access control | Chỉ PRO users |
| Audio format | Giữ nguyên format gốc |
| FFmpeg | Docker container (Jaffree) |
| Playback | Streaming only (presigned URL) |
| TTS cache | Có cache |
| Preview | Sau khi lưu mới nghe được |

---

## Module Structure

```
Backend/
├── shared-kernel/        # ✅ Đã có
├── shared-web/           # ✅ Đã có
├── shared-storage/       # 📋 TASK 1
├── bootstrap/            # ✅ Đã có
└── modules/
    ├── iam/              # ✅ Đã có (cần update role check)
    ├── notification/     # ✅ Đã có
    ├── outbox/           # ✅ Đã có
    └── voice/            # 📋 TASKS 2-13
```

---

## Task List

### Phase 0: shared-storage Module

#### [ ] TASK 1: Setup shared-storage Module
**Mục tiêu**: Tạo module dùng chung cho S3/Local storage

**Files cần tạo:**
- [ ] `shared-storage/pom.xml`
- [ ] `shared-storage/src/main/java/com/pwb/storage/api/StorageService.java`
- [ ] `shared-storage/src/main/java/com/pwb/storage/api/StorageException.java`
- [ ] `shared-storage/src/main/java/com/pwb/storage/api/dto/UploadResult.java`
- [ ] `shared-storage/src/main/java/com/pwb/storage/infrastructure/config/StorageProperties.java`
- [ ] `shared-storage/src/main/java/com/pwb/storage/infrastructure/config/S3Config.java`
- [ ] `shared-storage/src/main/java/com/pwb/storage/infrastructure/impl/S3StorageServiceImpl.java`
- [ ] `shared-storage/src/main/java/com/pwb/storage/infrastructure/impl/LocalStorageServiceImpl.java`
- [ ] Thêm `shared-storage` vào parent pom.xml modules

**Files cần update:**
- [ ] `bootstrap/pom.xml` - thêm shared-storage dependency
- [ ] `shared-kernel/src/main/java/com/pwb/sharedkernel/infrastructure/exception/ErrorCode.java` - thêm StorageErrorCode enum values

**Config cần thêm:**
- [ ] `application.yml` - storage config section
- [ ] `messages.properties` - storage error messages

**Ghi chú:**
- Interface `StorageService` là key abstraction
- LocalStorageService dùng profile `test-storage` hoặc config flag
- S3 bucket name dùng shared: `pwb-storage`

---

### Phase 1: Voice Module Foundation

#### [ ] TASK 2: Setup Voice Module Structure
**Mục tiêu**: Tạo voice module với dependencies, config, JPA

**Files cần tạo:**
- [ ] `modules/voice/pom.xml`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/config/VoiceJpaConfig.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/config/VoiceProperties.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/config/TtsConfig.java`

**Files cần update:**
- [ ] `parent pom.xml` - thêm voice module
- [ ] `bootstrap/pom.xml` - thêm voice dependency
- [ ] `application.yml` - voice config section

**Ghi chú:**
- Voice module phụ thuộc: shared-storage, shared-kernel, shared-web, iam

---

#### [ ] TASK 3: Create Database Migrations
**Mục tiêu**: Tạo bảng voice_voice_tags, voice_songs, voice_song_tag_configs

**Files cần tạo:**
- [ ] `bootstrap/src/main/resources/db/migration/V8__create_voice_tables.sql`

**Table structure:**
```sql
-- voice_voice_tags
-- voice_songs
-- voice_song_tag_configs
-- Indexes
```

**Ghi chú:**
- Follow pattern migration đã có (V1-V7)
- Có soft delete (deleted, deleted_at)
- Có auditing (created_at, updated_at, created_by, updated_by)

---

#### [ ] TASK 4: Voice Error Codes & Exceptions
**Mục tiêu**: Thêm error codes cho voice module vào ErrorCode enum tập trung

**Pattern của dự án**: ErrorCode được tập trung ở `shared-kernel/src/main/java/com/pwb/backend/exception/ErrorCode.java`

**Files cần update:**
- [ ] `shared-kernel/src/main/java/com/pwb/backend/exception/ErrorCode.java` - thêm Voice error codes

**Error codes cần thêm vào ErrorCode enum:**
```java
// Voice Module (prefix: VOICE_)
VOICE_TAG_NOT_FOUND      ("VOICE_001", "Voice tag not found.", 404),
SONG_NOT_FOUND           ("VOICE_002", "Song not found.", 404),
INVALID_AUDIO_FORMAT     ("VOICE_003", "Invalid audio format.", 400),
FILE_TOO_LARGE          ("VOICE_004", "File size exceeds maximum allowed.", 413),
TTS_GENERATION_FAILED   ("VOICE_005", "Text-to-speech generation failed.", 500),
AUDIO_PROCESSING_FAILED ("VOICE_006", "Audio processing failed.", 500),
INVALID_INTERVAL         ("VOICE_007", "Invalid interval value.", 400),
DUPLICATE_VOICE_TAG_NAME("VOICE_008", "Voice tag name already exists.", 409),
SONG_NOT_READY          ("VOICE_009", "Song is not ready for streaming.", 400),
ACCESS_DENIED_PRO_ONLY  ("VOICE_010", "This feature is available for PRO users only.", 403),
VOICE_TAG_IN_USE        ("VOICE_011", "Voice tag is currently in use and cannot be deleted.", 409),
SONG_ALREADY_PROCESSED  ("VOICE_012", "Song has already been processed.", 409),
INVALID_AUDIO_DURATION  ("VOICE_013", "Audio duration exceeds maximum allowed.", 400);
```

**Files cần update (messages - i18n):**
- [ ] `shared-web/src/main/resources/messages.properties` - add voice error messages (EN)
- [ ] `shared-web/src/main/resources/messages_vi.properties` - add voice error messages (VI)

**Ghi chú:**
- ErrorCode dùng chung cho tất cả modules, không tạo VoiceErrorCode riêng
- Messages i18n có thể đặt ở `shared-web` (đã có cấu trúc i18n)

---

### Phase 2: Voice Tag CRUD

#### [ ] TASK 5: Voice Tag Domain Models
**Mục tiêu**: Tạo domain entities và JPA entities cho Voice Tag

**Files cần tạo:**
- [ ] `modules/voice/src/main/java/com/pwb/voice/api/enums/VoiceTagType.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/core/model/VoiceTag.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/entity/VoiceTagJpaEntity.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/mapper/VoiceTagMapper.java` (MapStruct)
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/repository/VoiceTagJpaRepository.java`

**Ghi chú:**
- VoiceTag là immutable domain object với factory methods
- JPA entity có soft delete, auditing
- Mapper tự động MapStruct

---

#### [ ] TASK 6: Voice Tag DTOs
**Mục tiêu**: Tạo request/response DTOs cho Voice Tag API

**Files cần tạo:**
- [ ] `modules/voice/src/main/java/com/pwb/voice/api/dto/request/CreateTtsVoiceTagRequest.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/api/dto/request/UpdateVoiceTagRequest.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/api/dto/response/VoiceTagResponse.java`

**Validation:**
- @NotBlank, @Size cho name
- @Size cho text (max 4000)
- @Pattern cho languageCode

---

#### [ ] TASK 7: Google TTS Service
**Mục tiêu**: Tích hợp Google Cloud Text-to-Speech

**Files cần tạo:**
- [ ] `modules/voice/src/main/java/com/pwb/voice/core/service/TextToSpeechService.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/tts/GoogleTtsServiceImpl.java`

**Features:**
- synthesize(text, languageCode) → byte[]
- TTS cache (hash-based key)
- Default voices: en-US, vi-VN

**Ghi chú:**
- Dùng Spring Cache cho TTS cache
- Cache key: hash(text + languageCode + voiceConfig)

---

#### [ ] TASK 8: Voice Tag Service & Facade
**Mục tiêu**: Business logic và facade layer cho Voice Tag

**Files cần tạo:**
- [ ] `modules/voice/src/main/java/com/pwb/voice/core/service/VoiceTagService.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/service/VoiceTagServiceImpl.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/api/VoiceTagFacade.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/web/facade/VoiceTagFacadeImpl.java`

**Business logic:**
- createTtsVoiceTag: generate TTS → upload to S3 → save to DB
- createUploadedVoiceTag: validate → upload to S3 → save to DB
- updateVoiceTag: update metadata
- deleteVoiceTag: soft delete + delete S3 file
- getUserVoiceTags: list by user (filter by type)

**PRO check:**
- Check user role PRO trước khi allow CRUD

---

#### [ ] TASK 9: Voice Tag Controller
**Mục tiêu**: REST endpoints cho Voice Tag

**Files cần tạo:**
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/web/VoiceTagController.java`

**Endpoints:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/voice-tags/tts` | Create TTS voice tag |
| POST | `/api/v1/voice-tags/upload` | Upload voice tag audio |
| GET | `/api/v1/voice-tags` | List user's voice tags |
| GET | `/api/v1/voice-tags/{id}` | Get voice tag detail |
| PUT | `/api/v1/voice-tags/{id}` | Update voice tag |
| DELETE | `/api/v1/voice-tags/{id}` | Delete voice tag |
| GET | `/api/v1/voice-tags/{id}/audio` | Get presigned audio URL |

**Security:**
- @PreAuthorize("isAuthenticated()")
- Check PRO role
- User chỉ access được voice tag của mình

---

### Phase 3: Song Management

#### [ ] TASK 10: Song Domain Models & Repository
**Mục tiêu**: Tạo domain entities và JPA entities cho Song

**Files cần tạo:**
- [ ] `modules/voice/src/main/java/com/pwb/voice/api/enums/SongStatus.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/core/model/Song.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/core/model/SongTagConfig.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/entity/SongJpaEntity.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/entity/SongTagConfigJpaEntity.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/mapper/SongMapper.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/repository/SongJpaRepository.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/persistence/repository/SongTagConfigJpaRepository.java`

**SongStatus values:**
- UPLOADED: just uploaded, not processed
- PROCESSING: being processed
- PROCESSED: voice tag inserted
- FAILED: processing failed

---

#### [ ] TASK 11: Song DTOs
**Mục tiêu**: Tạo request/response DTOs cho Song API

**Files cần tạo:**
- [ ] `modules/voice/src/main/java/com/pwb/voice/api/dto/request/UploadSongRequest.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/api/dto/request/UpdateSongRequest.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/api/dto/request/ConfigureVoiceTagRequest.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/api/dto/response/SongResponse.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/api/dto/response/SongDetailResponse.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/api/dto/response/VoiceTagConfigResponse.java`

---

#### [ ] TASK 12: Song Service & Facade
**Mục tiêu**: Business logic và facade layer cho Song

**Files cần tạo:**
- [ ] `modules/voice/src/main/java/com/pwb/voice/core/service/SongService.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/service/SongServiceImpl.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/api/SongFacade.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/web/facade/SongFacadeImpl.java`

**Business logic:**
- uploadSong: validate → upload to S3 → extract metadata → save to DB
- updateSong: update metadata
- deleteSong: soft delete + delete S3 files
- getUserSongs: paginated list
- configureVoiceTag: create/update config (optional)
- removeVoiceTagConfig: delete config

**Ghi chú:**
- Không tự động process - user phải trigger
- Lưu cả original_s3_key và processed_s3_key

---

#### [ ] TASK 13: Song Controller
**Mục tiêu**: REST endpoints cho Song management

**Files cần tạo:**
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/web/SongController.java`

**Endpoints:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/songs/upload` | Upload song |
| GET | `/api/v1/songs` | List user's songs (paginated) |
| GET | `/api/v1/songs/{id}` | Get song detail |
| PUT | `/api/v1/songs/{id}` | Update song metadata |
| DELETE | `/api/v1/songs/{id}` | Delete song |

**Voice Tag Config Endpoints:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/songs/{songId}/voice-tag` | Configure voice tag |
| GET | `/api/v1/songs/{songId}/voice-tag` | Get voice tag config |
| PUT | `/api/v1/songs/{songId}/voice-tag` | Update voice tag config |
| DELETE | `/api/v1/songs/{songId}/voice-tag` | Remove voice tag config |

**Streaming Endpoints:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/songs/{id}/stream` | Get presigned URL for streaming |
| GET | `/api/v1/songs/{id}/original` | Get presigned URL for original file |

---

### Phase 4: Audio Processing

#### [ ] TASK 14: Audio Processing Service (FFmpeg)
**Mục tiêu**: Tích hợp FFmpeg để chèn voice tag vào song

**Files cần tạo:**
- [ ] `modules/voice/src/main/java/com/pwb/voice/core/service/AudioProcessingService.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/audio/FFmpegAudioProcessingService.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/core/service/AudioMetadata.java`

**Features:**
- getMetadata(InputStream): extract duration, format, bitrate
- insertVoiceTagAtInterval(): mix voice tag at N-second intervals

**Ghi chú:**
- Dùng Jaffree (Java FFmpeg wrapper)
- FFmpeg binary được download tự động bởi Jaffree
- Configurable temp directory

---

#### [ ] TASK 15: Voice Tag Insertion Processor
**Mục tiêu**: Async processor để chèn voice tag vào song

**Files cần tạo:**
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/processor/VoiceTagInsertionProcessor.java`
- [ ] `modules/voice/src/main/java/com/pwb/voice/infrastructure/config/VoiceProcessingConfig.java`

**Features:**
- Async processing với @Async
- ThreadPoolExecutor với configurable pool size
- Status tracking: UPLOADED → PROCESSING → PROCESSED/FAILED
- Retry logic (optional)

**Processing flow:**
1. Download original song từ S3
2. Download voice tag từ S3
3. FFmpeg insert voice tag at intervals
4. Upload processed song to S3
5. Update database: status = PROCESSED, processed_s3_key

---

#### [ ] TASK 16: Song Processing Trigger Endpoint
**Mục tiêu**: Endpoint để user trigger xử lý voice tag

**Files cần update:**
- [ ] `SongController.java` - add new endpoint

**New Endpoint:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/songs/{songId}/process` | Trigger voice tag insertion |
| GET | `/api/v1/songs/{songId}/status` | Get processing status |

**Response:**
```json
{
  "success": true,
  "data": {
    "songId": "uuid",
    "status": "PROCESSING",
    "message": "Processing started"
  }
}
```

---

### Phase 5: Integration & Polish

#### [ ] TASK 17: PRO Role Check Integration
**Mục tiêu**: Đảm bảo chỉ PRO users được dùng voice module

**Files cần update:**
- [ ] `VoiceTagController.java` - add @PreAuthorize for PRO role
- [ ] `SongController.java` - add @PreAuthorize for PRO role
- [ ] IAM module - đảm bảo RoleName.PRO tồn tại

**Implementation options:**
1. @PreAuthorize("hasRole('PRO')")
2. Custom annotation @RequirePro
3. Service layer check

---

#### [ ] TASK 18: Error Handling & i18n
**Mục tiêu**: Hoàn thiện error handling và messages

**Files cần update:**
- [ ] `messages.properties` - all voice module messages (EN)
- [ ] `messages_vi.properties` - all voice module messages (VI)

**Messages cần thêm:**
- VOICE_TAG_CREATED
- VOICE_TAG_UPDATED
- VOICE_TAG_DELETED
- SONG_UPLOADED
- SONG_DELETED
- VOICE_TAG_CONFIGURED
- ACCESS_DENIED_PRO_ONLY
- All validation messages

---

#### [ ] TASK 19: S3 Lifecycle & Cleanup
**Mục tiêu**: Cấu hình S3 lifecycle cho storage optimization

**Files cần tạo:**
- [ ] S3 lifecycle policy configuration

**Lifecycle rules:**
- `songs/original/`: Keep 90 days, then archive
- `songs/processed/`: Keep indefinitely
- `voice-tags/`: Keep indefinitely

---

#### [ ] TASK 20: Unit Tests
**Mục tiêu**: Viết unit tests cho các service chính

**Test coverage:**
- [ ] VoiceTagServiceImpl tests
- [ ] SongServiceImpl tests
- [ ] GoogleTtsServiceImpl tests
- [ ] FFmpegAudioProcessingService tests

**Ghi chú:**
- Mock StorageService
- Mock S3/TTS/FFmpeg
- Use LocalStorageService cho integration tests

---

## Implementation Order (Khuyến nghị)

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
├── Day 3: TASK 19 (S3 lifecycle)
└── Day 4-5: TASK 20 (Tests) + Bug fixes
```

---

## Prerequisites

Trước khi bắt đầu, cần có:

- [ ] AWS S3 bucket: `pwb-storage`
- [ ] Google Cloud TTS credentials
- [ ] PRO role đã define trong database (V4 seed)
- [ ] `application.yml` với đầy đủ config

---

## Checklist Completion

Đánh dấu khi hoàn thành:

- [ ] TASK 1: shared-storage Module
- [ ] TASK 2: Voice Module Setup
- [ ] TASK 3: Database Migrations
- [ ] TASK 4: Error Codes & Exceptions
- [ ] TASK 5: Voice Tag Domain Models
- [ ] TASK 6: Voice Tag DTOs
- [ ] TASK 7: Google TTS Service
- [ ] TASK 8: Voice Tag Service & Facade
- [ ] TASK 9: Voice Tag Controller
- [ ] TASK 10: Song Domain Models & Repository
- [ ] TASK 11: Song DTOs
- [ ] TASK 12: Song Service & Facade
- [ ] TASK 13: Song Controller
- [ ] TASK 14: Audio Processing Service
- [ ] TASK 15: Voice Tag Insertion Processor
- [ ] TASK 16: Song Processing Trigger
- [ ] TASK 17: PRO Role Check
- [ ] TASK 18: Error Handling & i18n
- [ ] TASK 19: S3 Lifecycle
- [ ] TASK 20: Unit Tests

---

## Notes

- Mỗi TASK nên được commit riêng để dễ review
- Chạy `mvn clean compile` sau mỗi TASK để verify
- Frontend có thể bắt đầu integrate sau TASK 9 (Voice Tag CRUD)
