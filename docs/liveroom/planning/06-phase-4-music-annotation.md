# Phase 4: Music + Annotation

**Thời gian**: 7-10 ngày
**Mục tiêu**: Shared music, annotation với optimistic lock + sequence number
**Doc tham chiếu chính**: `liveroom-api-spec.md` §2.5-2.6, `liveroom-ws-protocol.md` §4.5-4.6, `liveroom-concurrency.md` §3

---

## 🤖 AGENT BRIEFING

> **Copy toàn bộ khối code dưới đây → paste cho AI agent (Cursor).**
> Agent sẽ tự đọc docs và làm theo đúng quy trình.

```
Bạn là Senior Backend Dev + Frontend Dev cho dự án PWB MiNi (Spring Boot multi-module + Next.js 14 + TypeScript).

Project: d:\Learning\Project\PWB_MiNi\
Module: liveroom (Backend/modules/liveroom/, FE: frontend/src/features/liveroom/)

## 8 WORKSPACE RULES (BẮT BUỘC TUÂN THỦ):
1. senior-dev-coding-standards.mdc (SOLID, Clean Code)
2. prefer-lombok-backend.mdc (@Slf4j, @RequiredArgsConstructor, @Builder)
3. no-code-comments-backend.mdc
4. no-code-comments-frontend.mdc
5. no-hardcoded-messages-backend.mdc
6. no-auto-create-tests-backend.mdc
7. no-auto-commit-push-backend.mdc
8. respond-in-vietnamese.mdc

## PHASE 4 LÀ GÌ:
Music + Annotation - Shared music control qua STOMP (R-MUSIC-09 v1.8) + optimistic lock @Version trên PlaybackState + sequence number + owner absent logic + annotation CRUD + session history view. 4 STOMP + 1 REST + 4 WS events + 5 FE components + i18n ~15 keys.

## ⚠️ QUYẾT ĐỊNH QUAN TRỌNG (đã chốt):
- Music control CHỈ qua STOMP (R-MUSIC-09 v1.8) - KHÔNG dùng REST POST
- Optimistic lock: PlaybackState có @Version, KHÔNG có PESSIMISTIC_WRITE
- Sequence number cho music events (R-MUSIC-10 v1.8) - client apply theo order
- Owner absent → pause music + block non-owner control (R-MUSIC-11 v1.8)

## BẠN PHẢI ĐỌC TRƯỚC KHI CODE (theo thứ tự):
1. docs/liveroom/planning/README.md
2. docs/liveroom/planning/00-reading-guide.md
3. docs/liveroom/planning/06-phase-4-music-annotation.md (FILE NÀY - chi tiết 16 tasks)
4. docs/liveroom/planning/10-codebase-templates.md
5. docs/liveroom/planning/08-self-review-checklist.md
6. docs/liveroom/api-spec.md §2.5 (music STOMP), §2.6 (annotation)
7. docs/liveroom/ws-protocol.md §4.5 (music events), §4.6 (annotation events)
8. docs/liveroom/concurrency.md §3 (optimistic lock strategy)
9. docs/liveroom/data-model.md §3.8 (PlaybackState schema với @Version)
10. docs/liveroom/jobs.md §6-8 (music-related jobs)
11. docs/liveroom/screen-inventory.md SC-06 (music player), SC-07 (session history)
12. docs/liveroom/business-requirements.md R-MUSIC-01..11, R-ANNOT-01..08
13. docs/liveroom/i18n-keys.md (15 keys mới)

## 16 TASKS BẠN PHẢI LÀM (theo thứ tự):

### Task 4.1 - STOMP /music/play (Select + Start)
MusicStompController @MessageMapping. MusicService.play: validate room ACTIVE + song ownership + song PROCESSED + owner absent check. ⚠️ Dùng saveAndFlush + try/catch OptimisticLockingFailureException → throw LIVEROOM_MUSIC_STATE_CONFLICT. Update sequenceNumber++. Broadcast MUSIC_SONG_CHANGED + MUSIC_PLAYBACK_STATE_CHANGED.

### Task 4.2 - STOMP /music/pause, /seek, /volume
3 endpoints tương tự play. Volume 0-100 validate. Tất cả dùng optimistic lock + sequenceNumber++.

### Task 4.3 - REST GET /music/state
Read-only endpoint trả state hiện tại. Validate ACTIVE.

### Task 4.4 - PlaybackState + @Version
⚠️ Entity có @Version (KHÁC với LiveRoom). Đã tạo skeleton ở Phase 0 - verify @Version đã có.

### Task 4.5 - Sequence number ordering
File: lib/sequence-number-buffer.ts (FE). Class nhận event có sequenceNumber, discard out-of-order, buffer + apply theo order.

### Task 4.6 - Owner absent logic
Trong LiveRoomService.leave: nếu owner → auto-pause music + sequenceNumber++. Trong MusicService: nếu room.owner_left_at != null + currentUserId != owner → throw LIVEROOM_MUSIC_OWNER_ABSENT.

### Task 4.7 - POST /annotations
DTO CreateAnnotationRequest (songId, positionSeconds, content). Validate ACTIVE + room ACTIVE + song đang phát + positionSeconds trong [0, song.duration]. Content 1-200 chars + HTML escape. Persist với sessionCycleId. Broadcast ANNOTATION_CREATED.

### Task 4.8 - GET /sessions, /sessions/{cycleId}/annotations
GET /sessions: list cycles của room (DESC by startedAt). GET /sessions/{cycleId}/annotations: list annotations của cycle (ASC by positionSeconds).

### Task 4.9 - WS: 4 music/annotation events
MUSIC_SONG_CHANGED, MUSIC_PLAYBACK_STATE_CHANGED, ANNOTATION_CREATED, ANNOTATION_LIST_REFRESH (khi cycle đổi).

### Task 4.10 - FE: Music player
File: components/MusicPlayer.tsx + hooks/use-music-player.ts. Album art + title + play/pause + progress bar (seek) + volume slider + owner absent indicator. Sync qua WS event + sequence number buffer.

### Task 4.11 - FE: Song picker
File: components/SongPickerDialog.tsx. List user's PROCESSED songs + search/filter + click → STOMP /music/play.

### Task 4.12 - FE: Annotation popup
File: components/AnnotationPopup.tsx. Marker trên progress bar + click show content + add new annotation.

### Task 4.13 - FE: SC-07 Session history
File: app/(live-room)/live-room/[roomCode]/history/page.tsx. List previous sessions + click cycle → view annotations + date/time.

### Task 4.14 - FE: Mobile progress bar
File: components/MobileProgressBar.tsx. Touch-friendly + sync với desktop.

### Task 4.15 - i18n keys (~15 keys)
liveroom.music.play, liveroom.music.pause, liveroom.music.seek, liveroom.music.volume, liveroom.music.owner_absent, liveroom.music.song_picker, liveroom.music.no_songs, liveroom.annotation.add, liveroom.annotation.popup, ... (15 keys).

### Task 4.16 - E2E test race condition
2 users cùng click Play → 1 success, 1 STOMP ERROR LIVEROOM_MUSIC_STATE_CONFLICT → failing user auto re-fetch state.

## YÊU CẦU ĐẶC BIỆT:
- KHÔNG thêm comment
- KHÔNG hardcode message
- ⚠️ Music control CHỈ qua STOMP (KHÔNG dùng REST POST cho control)
- ⚠️ PlaybackState dùng @Version optimistic lock (KHÔNG PESSIMISTIC_WRITE)
- Sequence number cho music events
- Frontend: "use client", useTranslations, type-safe
- HTML escape annotation content
- Không commit tự động
- Trả lời user bằng tiếng Việt

## OUTPUT MONG ĐỢI:
- Code đầy đủ 16 tasks
- E2E race condition test pass
- Tự check 08-self-review-checklist.md
- Báo cáo file + checklist + warning

## BẮT ĐẦU ĐỌC 13 DOCS TRÊN. SAU ĐÓ LÀM TỪNG TASK THEO THỨ TỰ.
```

---

## 📋 TASK DETAILS

---

## 📋 Tổng quan Phase 4

| Task | Thời gian | Độ khó | Trạng thái |
|---|---|---|---|
| 4.1 STOMP /music/play (select + start) | 6 giờ | ⭐⭐⭐⭐ | ⏳ |
| 4.2 STOMP /music/pause, /seek, /volume | 4 giờ | ⭐⭐⭐ | ⏳ |
| 4.3 REST GET /music/state | 2 giờ | ⭐⭐ | ⏳ |
| 4.4 PlaybackState entity + @Version | 3 giờ | ⭐⭐⭐ | ⏳ |
| 4.5 Sequence number ordering | 3 giờ | ⭐⭐⭐ | ⏳ |
| 4.6 Owner absent → pause + block | 3 giờ | ⭐⭐⭐ | ⏳ |
| 4.7 POST /annotations | 4 giờ | ⭐⭐⭐ | ⏳ |
| 4.8 GET /sessions, /annotations | 3 giờ | ⭐⭐ | ⏳ |
| 4.9 WS: 4 music/annotation events | 4 giờ | ⭐⭐⭐ | ⏳ |
| 4.10 FE: Music player | 6 giờ | ⭐⭐⭐ | ⏳ |
| 4.11 FE: Song picker | 4 giờ | ⭐⭐⭐ | ⏳ |
| 4.12 FE: Annotation popup | 5 giờ | ⭐⭐⭐ | ⏳ |
| 4.13 FE: SC-07 Session history | 4 giờ | ⭐⭐⭐ | ⏳ |
| 4.14 FE: Mobile progress bar | 3 giờ | ⭐⭐ | ⏳ |
| 4.15 i18n keys (~15 keys) | 1 giờ | ⭐ | ⏳ |
| 4.16 E2E test (race condition) | 3 giờ | ⭐⭐ | ⏳ |

---

## Task 4.1: STOMP /music/play (Select + Start)

**Mục tiêu**: STOMP endpoint chọn bài + phát nhạc.

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.5.2 (đã sửa ở C3 - chỉ STOMP)
- `liveroom-business-requirements.md` R-MUSIC-01, R-MUSIC-09 v1.8
- `liveroom-concurrency.md` §3 (optimistic lock)

**Business rules**:
- R-MUSIC-01: Chỉ owner của song mới chọn được
- R-MUSIC-02: Song status = PROCESSED
- R-MUSIC-09: Music control CHỈ qua STOMP (v1.8)
- R-MUSIC-10: Optimistic lock `@Version` (R-CHAT-05 v1.8)
- R-MUSIC-11: Owner absent → block (v1.8)

**Template STOMP Handler**:

```java
@Slf4j
@Controller
@RequiredArgsConstructor
public class MusicStompController {

    private final MusicService musicService;

    @MessageMapping("/liveroom/{roomId}/music/play")
    public void play(@DestinationVariable UUID roomId, @Payload MusicPlayRequest request, Principal principal) {
        try {
            UUID userId = UUID.fromString(principal.getName());
            musicService.play(roomId, request.getSongId(), userId);
        } catch (BusinessException e) {
            // Send STOMP ERROR
            throw e;
        }
    }
}

@Service
@RequiredArgsConstructor
public class MusicService {

    private final PlaybackStateRepository playbackStateRepository;
    private final LiveRoomRepository liveRoomRepository;
    private final SongService songService;
    private final LiveRoomRealtimeBroadcaster broadcaster;

    @Transactional
    public void play(UUID roomId, UUID songId, UUID currentUserId) {
        LiveRoom room = liveRoomRepository.findById(roomId)
            .orElseThrow(() -> new BusinessException("LIVEROOM_NOT_FOUND"));

        if (room.getStatus() != RoomStatus.ACTIVE) {
            throw new BusinessException("LIVEROOM_ROOM_ENDED");
        }

        // R-MUSIC-11: Owner absent
        if (room.getOwnerLeftAt() != null && !room.getOwnerId().equals(currentUserId)) {
            throw new BusinessException("LIVEROOM_MUSIC_OWNER_ABSENT");
        }

        // R-MUSIC-01: Song ownership
        SongDto song = songService.getSong(songId);
        if (!song.getUserId().equals(currentUserId)) {
            throw new BusinessException("LIVEROOM_MUSIC_NOT_OWN_SONG");
        }

        // R-MUSIC-02: Song status
        if (!song.getStatus().equals(SongStatus.PROCESSED)) {
            throw new BusinessException("LIVEROOM_MUSIC_NOT_READY");
        }

        // R-MUSIC-10: Optimistic lock
        PlaybackState state = playbackStateRepository.findByRoomId(roomId)
            .orElseGet(() -> PlaybackState.builder()
                .roomId(roomId)
                .sequenceNumber(0L)
                .volumePercent(80)
                .build());

        state.setSongId(songId);
        state.setStatus(PlaybackStatus.PLAYING);
        state.setCurrentPositionSeconds(0.0);
        state.setSequenceNumber(state.getSequenceNumber() + 1);
        state.setLastUpdatedAt(OffsetDateTime.now());
        state.setLastUpdatedBy(currentUserId);

        try {
            playbackStateRepository.saveAndFlush(state);
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException("LIVEROOM_MUSIC_STATE_CONFLICT");
        }

        broadcaster.broadcast(roomId, "MUSIC_SONG_CHANGED", MusicSongChangedEvent.builder()
            .roomId(roomId)
            .songId(songId)
            .songTitle(song.getTitle())
            .songArtist(song.getArtist())
            .songDurationSeconds(song.getDurationSeconds())
            .build());

        broadcaster.broadcast(roomId, "MUSIC_PLAYBACK_STATE_CHANGED", MusicPlaybackStateChangedEvent.fromEntity(state));
    }
}
```

**Acceptance Criteria**:
- [ ] STOMP /app/liveroom/{roomId}/music/play {songId} → broadcast events
- [ ] Owner absent + non-owner → STOMP ERROR LIVEROOM_MUSIC_OWNER_ABSENT
- [ ] Song không thuộc user → STOMP ERROR LIVEROOM_MUSIC_NOT_OWN_SONG
- [ ] 2 users cùng play → 1 success, 1 STOMP ERROR LIVEROOM_MUSIC_STATE_CONFLICT

---

## Task 4.2: STOMP /music/pause, /seek, /volume

**Mục tiêu**: 3 STOMP control endpoints.

**Template**:

```java
@MessageMapping("/liveroom/{roomId}/music/pause")
public void pause(@DestinationVariable UUID roomId, @Payload Object empty, Principal principal) {
    UUID userId = UUID.fromString(principal.getName());
    musicService.pause(roomId, userId);
}

@Transactional
public void pause(UUID roomId, UUID currentUserId) {
    PlaybackState state = playbackStateRepository.findByRoomId(roomId)
        .orElseThrow(() -> new BusinessException("LIVEROOM_MUSIC_NOT_PLAYING"));

    if (state.getSongId() == null) {
        throw new BusinessException("LIVEROOM_MUSIC_NOT_PLAYING");
    }

    state.setStatus(PlaybackStatus.PAUSED);
    state.setSequenceNumber(state.getSequenceNumber() + 1);
    state.setLastUpdatedAt(OffsetDateTime.now());
    state.setLastUpdatedBy(currentUserId);

    try {
        playbackStateRepository.saveAndFlush(state);
    } catch (OptimisticLockingFailureException e) {
        throw new BusinessException("LIVEROOM_MUSIC_STATE_CONFLICT");
    }

    broadcaster.broadcast(roomId, "MUSIC_PLAYBACK_STATE_CHANGED", MusicPlaybackStateChangedEvent.fromEntity(state));
}

@MessageMapping("/liveroom/{roomId}/music/seek")
public void seek(@DestinationVariable UUID roomId, @Payload MusicSeekRequest request, Principal principal) {
    UUID userId = UUID.fromString(principal.getName());
    musicService.seek(roomId, request.getPositionSeconds(), userId);
}

@Transactional
public void seek(UUID roomId, double positionSeconds, UUID currentUserId) {
    if (positionSeconds < 0) {
        throw new BusinessException("LIVEROOM_MUSIC_INVALID_POSITION");
    }

    PlaybackState state = playbackStateRepository.findByRoomId(roomId)
        .orElseThrow(() -> new BusinessException("LIVEROOM_MUSIC_NOT_PLAYING"));

    SongDto song = songService.getSong(state.getSongId());
    if (positionSeconds > song.getDurationSeconds()) {
        throw new BusinessException("LIVEROOM_MUSIC_INVALID_POSITION");
    }

    state.setCurrentPositionSeconds(positionSeconds);
    state.setSequenceNumber(state.getSequenceNumber() + 1);
    state.setLastUpdatedAt(OffsetDateTime.now());
    state.setLastUpdatedBy(currentUserId);

    try {
        playbackStateRepository.saveAndFlush(state);
    } catch (OptimisticLockingFailureException e) {
        throw new BusinessException("LIVEROOM_MUSIC_STATE_CONFLICT");
    }

    broadcaster.broadcast(roomId, "MUSIC_PLAYBACK_STATE_CHANGED", MusicPlaybackStateChangedEvent.fromEntity(state));
}

@MessageMapping("/liveroom/{roomId}/music/volume")
public void changeVolume(@DestinationVariable UUID roomId, @Payload MusicVolumeRequest request, Principal principal) {
    UUID userId = UUID.fromString(principal.getName());
    musicService.changeVolume(roomId, request.getVolumePercent(), userId);
}

@Transactional
public void changeVolume(UUID roomId, int volumePercent, UUID currentUserId) {
    if (volumePercent < 0 || volumePercent > 100) {
        throw new BusinessException("LIVEROOM_MUSIC_INVALID_VOLUME");
    }

    PlaybackState state = playbackStateRepository.findByRoomId(roomId)
        .orElseThrow(() -> new BusinessException("LIVEROOM_MUSIC_NOT_PLAYING"));

    state.setVolumePercent(volumePercent);
    state.setSequenceNumber(state.getSequenceNumber() + 1);
    state.setLastUpdatedAt(OffsetDateTime.now());
    state.setLastUpdatedBy(currentUserId);

    try {
        playbackStateRepository.saveAndFlush(state);
    } catch (OptimisticLockingFailureException e) {
        throw new BusinessException("LIVEROOM_MUSIC_STATE_CONFLICT");
    }

    broadcaster.broadcast(roomId, "MUSIC_PLAYBACK_STATE_CHANGED", MusicPlaybackStateChangedEvent.fromEntity(state));
}
```

**Acceptance Criteria**:
- [ ] Pause → broadcast PLAYBACK_STATE_CHANGED
- [ ] Seek → broadcast
- [ ] Volume 0-100 → OK, 101 → ERROR
- [ ] All have optimistic lock

---

## Task 4.3: REST GET /music/state

**Mục tiêu**: Read music state qua REST.

**Template**:

```java
@GetMapping("/api/v1/liverooms/{roomId}/music/state")
public ResponseEntity<MusicStateResponse> getMusicState(@PathVariable UUID roomId, @AuthenticationPrincipal UUID currentUserId) {
    MusicStateResponse state = musicService.getState(roomId, currentUserId);
    return ResponseEntity.ok(ApiResponse.success(message("liveroom.music.state.success"), state));
}

public MusicStateResponse getState(UUID roomId, UUID currentUserId) {
    Participant participant = participantRepository.findByRoomIdAndUserId(roomId, currentUserId)
        .orElseThrow(() -> new BusinessException("LIVEROOM_NOT_IN_SESSION"));

    if (participant.getState() != ParticipantState.ACTIVE) {
        throw new BusinessException("LIVEROOM_NOT_ACTIVE");
    }

    return playbackStateRepository.findByRoomId(roomId)
        .map(MusicStateResponse::fromEntity)
        .orElse(MusicStateResponse.empty());
}
```

**Acceptance Criteria**:
- [ ] GET /music/state → 200 OK + state
- [ ] No song → songId = null
- [ ] Non-ACTIVE user → 403

---

## Task 4.4: PlaybackState + @Version

**Mục tiêu**: Entity PlaybackState với @Version.

**Doc tham chiếu**:
- `liveroom-data-model.md` §3.8
- `liveroom-concurrency.md` §3

**Template**:

```java
@Entity
@Table(name = "liveroom_playback_states")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaybackState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false, unique = true)
    private LiveRoom room;

    private UUID songId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaybackStatus status;

    @Column(name = "current_position_seconds", nullable = false)
    private Double currentPositionSeconds = 0.0;

    @Column(name = "volume_percent", nullable = false)
    private Short volumePercent = 80;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber = 0L;

    @Column(name = "last_updated_at", nullable = false)
    private OffsetDateTime lastUpdatedAt;

    @Column(name = "last_updated_by")
    private UUID lastUpdatedBy;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
        if (lastUpdatedAt == null) lastUpdatedAt = OffsetDateTime.now();
        if (currentPositionSeconds == null) currentPositionSeconds = 0.0;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
```

**Acceptance Criteria**:
- [ ] Entity có @Version
- [ ] Optimistic lock hoạt động (test 2 requests đồng thời)

---

## Task 4.5: Sequence Number Ordering

**Mục tiêu**: Client apply event theo sequence number.

**Doc tham chiếu**:
- `liveroom-business-requirements.md` R-MUSIC-10 v1.8

**Template FE**:

```typescript
// frontend/src/features/liveroom/lib/sequence-number-buffer.ts
export class SequenceNumberBuffer {
  private currentSequence = 0;
  private pendingEvents: any[] = [];

  applyEvent(event: any) {
    if (event.sequenceNumber <= this.currentSequence) {
      // Out-of-order, discard
      return;
    }

    this.pendingEvents.push(event);
    this.pendingEvents.sort((a, b) => a.sequenceNumber - b.sequenceNumber);

    while (this.pendingEvents.length > 0 && this.pendingEvents[0].sequenceNumber === this.currentSequence + 1) {
      const next = this.pendingEvents.shift();
      this.applyEventToState(next);
      this.currentSequence = next.sequenceNumber;
    }
  }

  private applyEventToState(event: any) {
    // Update playback state...
  }
}
```

**Acceptance Criteria**:
- [ ] Out-of-order events discarded
- [ ] Sequence gap buffering
- [ ] Last-write-wins semantics

---

## Task 4.6: Owner Absent Logic

**Mục tiêu**: Owner leave → music pause + non-owner control disabled.

**Doc tham chiếu**:
- `liveroom-business-requirements.md` R-MUSIC-06, R-MUSIC-11

**Logic** (khi owner leave):

```java
// Trong LiveRoomService.leave():
if (room.getOwnerId().equals(currentUserId)) {
    // Owner leave
    // Auto-pause music
    Optional<PlaybackState> stateOpt = playbackStateRepository.findByRoomId(roomId);
    if (stateOpt.isPresent()) {
        PlaybackState state = stateOpt.get();
        state.setStatus(PlaybackStatus.PAUSED);
        state.setSequenceNumber(state.getSequenceNumber() + 1);
        state.setLastUpdatedAt(OffsetDateTime.now());
        state.setLastUpdatedBy(currentUserId);
        playbackStateRepository.save(state);
        broadcaster.broadcast(roomId, "MUSIC_PLAYBACK_STATE_CHANGED", ...);
    }
}
```

**Acceptance Criteria**:
- [ ] Owner leave → music PAUSED
- [ ] Non-owner click play → STOMP ERROR LIVEROOM_MUSIC_OWNER_ABSENT
- [ ] Owner rejoin → music có thể resume

---

## Task 4.7: POST /annotations

**Mục tiêu**: User tạo annotation trên bài nhạc.

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.6.1
- `liveroom-business-requirements.md` R-ANNOT-01..08

**Business rules**:
- R-ANNOT-01: Chỉ ACTIVE participants
- R-ANNOT-02: Room phải ACTIVE + có bài đang phát
- R-ANNOT-03: positionSeconds trong range [0, song.duration]
- R-ANNOT-04: content max 200 chars, not blank
- R-ANNOT-05: Persist DB với sessionCycleId
- R-ANNOT-06: Broadcast WS event

**Template**:

```java
@Data
@Builder
public class CreateAnnotationRequest {
    @NotNull
    private UUID songId;

    @NotNull
    @DecimalMin("0")
    private Double positionSeconds;

    @NotBlank
    @Size(min = 1, max = 200)
    private String content;
}

@Transactional
public AnnotationResponse createAnnotation(UUID roomId, CreateAnnotationRequest request, UUID currentUserId) {
    LiveRoom room = liveRoomRepository.findById(roomId)
        .orElseThrow(() -> new BusinessException("LIVEROOM_NOT_FOUND"));

    if (room.getStatus() != RoomStatus.ACTIVE) {
        throw new BusinessException("LIVEROOM_ROOM_ENDED");
    }

    Participant participant = participantRepository.findByRoomIdAndUserId(roomId, currentUserId)
        .orElseThrow(() -> new BusinessException("LIVEROOM_NOT_IN_SESSION"));

    if (participant.getState() != ParticipantState.ACTIVE) {
        throw new BusinessException("LIVEROOM_NOT_ACTIVE");
    }

    PlaybackState state = playbackStateRepository.findByRoomId(roomId)
        .orElseThrow(() -> new BusinessException("LIVEROOM_MUSIC_NOT_PLAYING"));

    if (state.getSongId() == null) {
        throw new BusinessException("LIVEROOM_MUSIC_NOT_PLAYING");
    }

    if (!state.getSongId().equals(request.getSongId())) {
        throw new BusinessException("LIVEROOM_ANNOTATION_SONG_MISMATCH");
    }

    SongDto song = songService.getSong(request.getSongId());
    if (request.getPositionSeconds() > song.getDurationSeconds()) {
        throw new BusinessException("LIVEROOM_ANNOTATION_INVALID_POSITION");
    }

    String safeContent = StringEscapeUtils.escapeHtml4(request.getContent());

    Annotation annotation = Annotation.builder()
        .roomId(roomId)
        .songId(request.getSongId())
        .userId(currentUserId)
        .userEmail(userService.getUserEmail(currentUserId))
        .positionSeconds(request.getPositionSeconds())
        .content(safeContent)
        .createdAt(OffsetDateTime.now())
        .sessionCycleId(room.getCurrentSessionCycleId())
        .build();
    annotation = annotationRepository.save(annotation);

    broadcaster.broadcast(roomId, "ANNOTATION_CREATED", AnnotationDto.fromEntity(annotation));

    return AnnotationResponse.fromEntity(annotation);
}
```

**Acceptance Criteria**:
- [ ] Create annotation → 201 Created
- [ ] No song playing → 409 LIVEROOM_MUSIC_NOT_PLAYING
- [ ] position > duration → 400
- [ ] WS event broadcast

---

## Task 4.8: GET /sessions, /annotations

**Mục tiêu**: List sessions + annotations.

**Templates**:

```java
@GetMapping("/api/v1/liverooms/{roomId}/sessions")
public SessionListResponse getSessions(@PathVariable UUID roomId) {
    return SessionListResponse.builder()
        .sessions(cycleRepository.findByRoomIdOrderByStartedAtDesc(roomId).stream()
            .map(SessionDto::fromEntity).toList())
        .build();
}

@GetMapping("/api/v1/liverooms/{roomId}/sessions/{cycleId}/annotations")
public AnnotationListResponse getAnnotations(@PathVariable UUID roomId, @PathVariable UUID cycleId) {
    return AnnotationListResponse.builder()
        .annotations(annotationRepository.findByRoomIdAndSessionCycleIdOrderByPositionSecondsAsc(roomId, cycleId).stream()
            .map(AnnotationDto::fromEntity).toList())
        .build();
}
```

**Acceptance Criteria**:
- [ ] GET /sessions → list cycles
- [ ] GET /sessions/{cycleId}/annotations → list annotations

---

## Task 4.9: WS Events (4 events)

**Mục tiêu**: 4 music/annotation events.

**4 events**:

| Event | Trigger | Payload |
|---|---|---|
| `MUSIC_SONG_CHANGED` | Song mới được chọn | `{songId, songTitle, songArtist, durationSeconds}` |
| `MUSIC_PLAYBACK_STATE_CHANGED` | Play/pause/seek/volume | `{status, positionSeconds, volumePercent, sequenceNumber, lastUpdatedBy}` |
| `ANNOTATION_CREATED` | Annotation mới | `{annotationId, songId, positionSeconds, content, userEmail, createdAt}` |
| `ANNOTATION_LIST_REFRESH` | Cycle thay đổi (reopen) | `{cycleId, annotationCount}` |

---

## Task 4.10-4.14: FE Components

### Task 4.10: Music Player

**Files**:
- `frontend/src/features/liveroom/components/MusicPlayer.tsx`
- `frontend/src/features/liveroom/hooks/use-music-player.ts`

**Components**:
- Album art
- Song title + artist
- Play/pause button
- Progress bar (seek)
- Volume slider
- Owner absent indicator
- Sync via WS event

**Acceptance Criteria**:
- [ ] Play/pause work
- [ ] Seek work
- [ ] Volume work
- [ ] Owner absent → controls disabled

---

### Task 4.11: Song Picker

**Files**:
- `frontend/src/features/liveroom/components/SongPickerDialog.tsx`

**Components**:
- List user's PROCESSED songs
- Search/filter
- Click song → STOMP /music/play

**Acceptance Criteria**:
- [ ] List songs
- [ ] Filter by status
- [ ] Click → STOMP control

---

### Task 4.12: Annotation Popup

**Files**:
- `frontend/src/features/liveroom/components/AnnotationPopup.tsx`

**Components**:
- Marker trên progress bar
- Click marker → show content
- Add new annotation button

**Acceptance Criteria**:
- [ ] Marker show đúng vị trí
- [ ] Click show popup
- [ ] Add new annotation

---

### Task 4.13: SC-07 Session History

**Files**:
- `frontend/src/app/(live-room)/live-room/[roomCode]/history/page.tsx`

**Components**:
- List previous sessions
- Click cycle → view annotations
- Date/time display

**Acceptance Criteria**:
- [ ] List sessions
- [ ] View annotations of selected cycle

---

### Task 4.14: Mobile Progress Bar

**Files**:
- `frontend/src/features/liveroom/components/MobileProgressBar.tsx`

**Acceptance Criteria**:
- [ ] Touch-friendly
- [ ] Sync với desktop

---

## Task 4.15: i18n Keys

**Keys**:
- `liveroom.music.play`
- `liveroom.music.pause`
- `liveroom.music.seek`
- `liveroom.music.volume`
- `liveroom.music.owner_absent`
- `liveroom.music.song_picker`
- `liveroom.music.no_songs`
- `liveroom.annotation.add`
- `liveroom.annotation.popup`
- ... (~15 keys)

---

## Task 4.16: E2E Test (Race Condition)

**Mục tiêu**: Test 2 users cùng tap play → 1 success, 1 conflict.

**Test scenario**:
1. User A + B đều ACTIVE
2. Cả 2 cùng click Play (cùng song khác nhau)
3. 1 success, 1 STOMP ERROR LIVEROOM_MUSIC_STATE_CONFLICT
4. Failing user toast "Nhạc đã được điều khiển bởi người khác"
5. Failing user auto re-fetch state

**Acceptance Criteria**:
- [ ] Optimistic lock protect race
- [ ] Client re-fetch state sau conflict

---

## 🚦 Definition of Done Phase 4

- [ ] Tất cả 16 tasks DONE
- [ ] STOMP /music/{play,pause,seek,volume} work
- [ ] REST GET /music/state work
- [ ] Optimistic lock protect race
- [ ] Owner absent → pause + block
- [ ] Annotation create + list
- [ ] 4 FE components render
- [ ] i18n ~15 keys
- [ ] E2E test pass

---

**Cập nhật**: 2026-07-26
