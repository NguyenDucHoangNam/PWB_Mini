# Phase 1: Create + Join

**Thời gian**: 5-7 ngày
**Mục tiêu**: Owner tạo phòng → User nhập code → Vào phòng ACTIVE
**Doc tham chiếu chính**: `liveroom-api-spec.md`, `liveroom-state-machines.md`, `liveroom-screen-inventory.md`

---

## 🤖 AGENT BRIEFING

> **Copy toàn bộ khối code dưới đây → paste cho AI agent (Cursor).**
> Agent sẽ tự đọc docs và làm theo đúng quy trình.

```
Bạn là Senior Backend Dev + Frontend Dev cho dự án PWB MiNi (Spring Boot multi-module + Next.js 14 + TypeScript).

Project: d:\Learning\Project\PWB_MiNi\
Module: liveroom (Backend/modules/liveroom/, FE: frontend/src/features/liveroom/)

## 8 WORKSPACE RULES (BẮT BUỘC TUÂN THỦ - đọc file .cursor/rules/):
1. senior-dev-coding-standards.mdc (SOLID, Clean Code)
2. prefer-lombok-backend.mdc (@Slf4j, @RequiredArgsConstructor, @Builder)
3. no-code-comments-backend.mdc (KHÔNG comment trong Java)
4. no-code-comments-frontend.mdc (KHÔNG comment trong TS/TSX)
5. no-hardcoded-messages-backend.mdc (dùng MessageSource, i18n)
6. no-auto-create-tests-backend.mdc (KHÔNG tự viết test)
7. no-auto-commit-push-backend.mdc (KHÔNG tự git commit/push)
8. respond-in-vietnamese.mdc (trả lời user bằng tiếng Việt)

## PHASE 1 LÀ GÌ:
MVP - Owner tạo phòng, user nhập code join, owner approve, cả 2 thấy nhau trong phòng. 5 BE endpoints + 3 WS events + 4 FE screens + i18n ~10 keys.

## BẠN PHẢI ĐỌC TRƯỚC KHI CODE (theo thứ tự):
1. docs/liveroom/planning/README.md (overview toàn project)
2. docs/liveroom/planning/00-reading-guide.md (cách đọc docs gốc)
3. docs/liveroom/planning/03-phase-1-create-join.md (FILE NÀY - chi tiết 13 tasks)
4. docs/liveroom/planning/10-codebase-templates.md (copy template code)
5. docs/liveroom/planning/08-self-review-checklist.md (checklist tự review)
6. docs/liveroom/api-spec.md §1.1-1.3, §2.1-2.2 (endpoints)
7. docs/liveroom/state-machines.md §1 (JoinRequest PENDING → APPROVED → ACTIVE)
8. docs/liveroom/screen-inventory.md SC-01/03/04/06
9. docs/liveroom/permission-matrix.md §1-2 (owner vs participant)
10. docs/liveroom/business-requirements.md R-CREATE-01..06, R-JOIN-01..10, R-APPROVE-01..06
11. docs/liveroom/concurrency.md §2.1 (pessimistic lock cho approve)
12. docs/liveroom/ws-protocol.md §4.1 (3 events: JOIN_REQUEST_CREATED, REQUEST_APPROVED, PARTICIPANT_JOINED)
13. docs/liveroom/i18n-keys.md §2 (10 keys cần dùng)

## 13 TASKS BẠN PHẢI LÀM (theo thứ tự):

### Task 1.1 - POST /liverooms (Create Room)
Tạo DTO (CreateRoomRequest, CreateRoomResponse), LiveRoomFacade interface + Impl (logic create + generate unique 6-char roomCode), LiveRoomController (POST /api/v1/liverooms). Business rules R-CREATE-01..06. Doc tham chiếu: api-spec.md §1.1.

### Task 1.2 - GET /liverooms/by-code/{code}
Public endpoint (không cần auth) để user tra cứu phòng. Case-insensitive code. Dùng template §4 Service.

### Task 1.3 - POST /liverooms/{id}/join-requests
Idempotency: nếu đã có PENDING → trả existing. Validate: room ACTIVE, user chưa ACTIVE, capacity, user chưa LOCKED (3 rejects), user chưa trong KICKED cooldown 5min, user không phải owner. WS broadcast JOIN_REQUEST_CREATED.

### Task 1.4 - POST /join-requests/{id}/approve (PESSIMISTIC LOCK)
⚠️ QUAN TRỌNG: dùng entityManager.find(LiveRoom.class, roomId, LockModeType.PESSIMISTIC_WRITE). KHÔNG dùng @Version trên LiveRoom. Update JoinRequest PENDING→APPROVED, tạo Participant mới state=ACTIVE, tăng current_participant_count. Nếu capacity full → REJECTED với reason ROOM_FULL. WS broadcast REQUEST_APPROVED + PARTICIPANT_JOINED + sendToUser(REQUEST_APPROVED) cho requester.

### Task 1.5 - GET /liverooms/{id}/state
Trả full state cho owner + ACTIVE participant. Validate user có ACTIVE/PENDING/APPROVED. Response: room info + participants list.

### Task 1.6 - WS: 3 events
RealtimeBroadcaster.broadcast(roomId, eventType, payload) → /topic/liveroom/{roomId}. 3 events: JOIN_REQUEST_CREATED, REQUEST_APPROVED, PARTICIPANT_JOINED. Payload theo ws-protocol.md §4.1.

### Task 1.7 - FE: SC-01 Create Room screen
File: frontend/src/app/(live-room)/live-room/create/page.tsx + components/CreateRoomForm.tsx. Form: roomName (max 100), maxParticipants slider (1-7), ownerGraceSeconds slider (30-1800). Submit → POST /liverooms → redirect /live-room/[roomCode].

### Task 1.8 - FE: SC-03 Pre-Join screen
File: app/(live-room)/live-room/page.tsx + components/PreJoinScreen.tsx. Input roomCode 6 chars uppercase auto. Click "Tìm phòng" → GET /by-code/{code}. Click "Xin vào phòng" → POST /join-requests → redirect SC-04.

### Task 1.9 - FE: SC-04 Waiting Room card
File: components/WaitingRoomCard.tsx. Hiển thị "Đang chờ owner duyệt..." + cancel button. Subscribe WS: REQUEST_APPROVED → SC-06, REQUEST_REJECTED_BY_OWNER → SC-03b.

### Task 1.10 - FE: SC-06 In-Room basic
File: app/(live-room)/live-room/[roomCode]/page.tsx + components/ImmersiveMeetingRoom.tsx + MediaTile.tsx. Header (room name, code) + participant grid (chỉ avatar, chưa có video) + footer leave button. Subscribe WS: PARTICIPANT_JOINED, PARTICIPANT_LEFT.

### Task 1.11 - FE: WS subscription hook
File: hooks/useLiveRoomSocket.ts. Subscribe /topic/liveroom/{roomId}, dispatch event theo eventType. Cleanup on unmount.

### Task 1.12 - i18n keys (~10 keys)
Thêm vào: messages.properties, messages_en.properties, messages_vi.properties (3 file BE) + frontend/messages/en.json, vi.json. Keys: liveroom.create.success, liveroom.create.error, liveroom.create.form.title, liveroom.create.form.room_name, liveroom.create.form.max_participants, liveroom.create.form.grace_seconds, liveroom.create.form.submit, liveroom.prejoin.search.placeholder, liveroom.prejoin.search.not_found, liveroom.waiting.cancel.

### Task 1.13 - E2E test 2 browsers
Test scenario: Browser A create room → Browser B join bằng code → A approve → cả 2 thấy nhau trong phòng. Test idempotency (gửi request 2 lần). Test race condition capacity.

## YÊU CẦU ĐẶC BIỆT:
- KHÔNG thêm comment vào code
- KHÔNG hardcode message, dùng MessageSource với key từ i18n-keys.md
- Dùng Lombok đúng rule (DTO @Data @Builder, Service @Slf4j @RequiredArgsConstructor, Entity KHÔNG @Data)
- ⚠️ PESSIMISTIC_WRITE khi update LiveRoom (KHÔNG @Version)
- Frontend: "use client", useTranslations, KHÔNG hardcode Vietnamese, type-safe TypeScript
- Không commit tự động
- Trả lời user bằng tiếng Việt

## OUTPUT MONG ĐỢI:
- Code đầy đủ 13 tasks (BE + FE)
- Acceptance Criteria PASS cho mỗi task
- Tự check 08-self-review-checklist.md trước báo cáo
- Báo cáo: file đã tạo + checklist pass/fail + warning + ngày hoàn thành

## BẮT ĐẦU ĐỌC 13 DOCS TRÊN. SAU ĐÓ LÀM TỪNG TASK THEO THỨ TỰ.
```

---

## 📋 TASK DETAILS

---

## 📋 Tổng quan Phase 1

| Task | Thời gian | Độ khó | Trạng thái |
|---|---|---|---|
| 1.1 POST /liverooms (create room) | 4 giờ | ⭐⭐⭐ | ⏳ |
| 1.2 GET /liverooms/by-code/{code} | 1 giờ | ⭐ | ⏳ |
| 1.3 POST /join-requests (idempotency) | 4 giờ | ⭐⭐⭐ | ⏳ |
| 1.4 POST /join-requests/{id}/approve (pessimistic lock) | 5 giờ | ⭐⭐⭐⭐ | ⏳ |
| 1.5 GET /liverooms/{id}/state (full state) | 3 giờ | ⭐⭐⭐ | ⏳ |
| 1.6 WS: 3 events (JoinRequest + Participant) | 4 giờ | ⭐⭐⭐ | ⏳ |
| 1.7 FE: SC-01 Create Room screen | 4 giờ | ⭐⭐ | ⏳ |
| 1.8 FE: SC-03 Pre-Join screen | 3 giờ | ⭐⭐ | ⏳ |
| 1.9 FE: SC-04 Waiting Room card | 3 giờ | ⭐⭐ | ⏳ |
| 1.10 FE: SC-06 In-Room basic | 5 giờ | ⭐⭐⭐ | ⏳ |
| 1.11 FE: WS subscription | 2 giờ | ⭐⭐ | ⏳ |
| 1.12 i18n keys (~10 keys) | 1 giờ | ⭐ | ⏳ |
| 1.13 E2E test (2 browsers) | 2 giờ | ⭐⭐ | ⏳ |

---

## Task 1.1: POST /liverooms (Create Room)

**Mục tiêu**: Owner tạo phòng mới.

**Doc tham chiếu**:
- `liveroom-api-spec.md` §1.1
- `liveroom-business-requirements.md` R-CREATE-01..06
- `liveroom-state-machines.md` §1 (Room creation)

**Files cần tạo/sửa**:
1. `Backend/modules/liveroom/src/main/java/com/pwb/liveroom/api/dto/request/CreateRoomRequest.java`
2. `Backend/modules/liveroom/src/main/java/com/pwb/liveroom/api/dto/response/CreateRoomResponse.java`
3. `Backend/modules/liveroom/src/main/java/com/pwb/liveroom/core/service/LiveRoomFacade.java` (interface)
4. `Backend/modules/liveroom/src/main/java/com/pwb/liveroom/core/service/LiveRoomFacadeImpl.java` (logic)
5. `Backend/modules/liveroom/src/main/java/com/pwb/liveroom/infrastructure/web/LiveRoomController.java`

**Business rules**:
- R-CREATE-01: Owner phải có plan PRO
- R-CREATE-02: Validate room_name (1-100 chars, trim, lowercase normalized)
- R-CREATE-03: max_participants mặc định 7, range 1-7
- R-CREATE-04: max_participants validate 1-7
- R-CREATE-05: room_code 6 chars A-Z0-9, unique, auto-generated
- R-CREATE-06: room_name normalized unique per owner

**Template CreateRoomRequest**:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {

    @NotBlank
    @Size(min = 1, max = 100)
    private String roomName;

    @Min(1) @Max(7)
    private Integer maxParticipants;

    @Min(30) @Max(1800)
    private Integer ownerGraceSeconds;
}
```

**Template Service**:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveRoomFacadeImpl implements LiveRoomFacade {

    private final LiveRoomRepository liveRoomRepository;
    private final RoomSessionCycleRepository cycleRepository;
    private final UserService userService; // từ IAM module
    private final MessageSource messageSource;

    private static final String MSG_ROOM_CREATED = "liveroom.create.success";

    @Transactional
    public CreateRoomResponse createRoom(CreateRoomRequest request, UUID currentUserId, Locale locale) {
        UserDto user = userService.getUser(currentUserId);
        if (!user.getPlan().equals("PRO")) {
            throw new BusinessException("USER_NOT_PRO");
        }

        String roomCode = generateUniqueRoomCode();
        String normalizedName = request.getRoomName().trim().toLowerCase();

        RoomSessionCycle firstCycle = RoomSessionCycle.builder()
            .cycleNumber(1)
            .startedAt(OffsetDateTime.now())
            .build();
        firstCycle = cycleRepository.save(firstCycle);

        LiveRoom room = LiveRoom.builder()
            .roomCode(roomCode)
            .roomName(request.getRoomName())
            .roomNameNormalized(normalizedName)
            .ownerId(currentUserId)
            .maxParticipants(request.getMaxParticipants() != null ? request.getMaxParticipants() : 7)
            .ownerGraceSeconds(request.getOwnerGraceSeconds() != null ? request.getOwnerGraceSeconds() : 60)
            .currentSessionCycleId(firstCycle.getId())
            .status(RoomStatus.ACTIVE)
            .build();

        LiveRoom saved = liveRoomRepository.save(room);

        log.info("Room created: roomId={}, roomCode={}, ownerId={}", saved.getId(), roomCode, currentUserId);

        return CreateRoomResponse.builder()
            .roomId(saved.getId())
            .roomCode(saved.getRoomCode())
            .roomName(saved.getRoomName())
            .build();
    }

    private String generateUniqueRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom random = new SecureRandom();
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = random.ints(6, 0, chars.length())
                .mapToObj(i -> String.valueOf(chars.charAt(i)))
                .collect(Collectors.joining());
            if (!liveRoomRepository.existsByRoomCode(code)) {
                return code;
            }
        }
        throw new BusinessException("ROOM_CODE_GENERATION_FAILED");
    }
}
```

**Acceptance Criteria**:
- [ ] Tạo phòng với name "Test Room" → trả roomCode 6 chars
- [ ] User không phải PRO → 403 USER_NOT_PRO
- [ ] maxParticipants = 8 → 400 VALIDATION_FAILED
- [ ] roomName = "" → 400 VALIDATION_FAILED
- [ ] Tạo 2 phòng cùng name (case insensitive) → 409 ROOM_NAME_DUPLICATE
- [ ] Log có roomId, roomCode, ownerId

---

## Task 1.2: GET /liverooms/by-code/{code}

**Mục tiêu**: Tìm phòng theo room code (public, không cần auth).

**Doc tham chiếu**:
- `liveroom-api-spec.md` §1.2
- `liveroom-state-machines.md` §1 (Pre-join state)

**Template**:

```java
public GetRoomByCodeResponse getRoomByCode(String roomCode) {
    LiveRoom room = liveRoomRepository.findByRoomCode(roomCode.toUpperCase())
        .orElseThrow(() -> new BusinessException("LIVEROOM_NOT_FOUND"));

    return GetRoomByCodeResponse.builder()
        .roomId(room.getId())
        .roomCode(room.getRoomCode())
        .roomName(room.getRoomName())
        .ownerId(room.getOwnerId())
        .maxParticipants(room.getMaxParticipants())
        .currentParticipants(room.getCurrentParticipantCount())
        .status(room.getStatus())
        .build();
}
```

**Acceptance Criteria**:
- [ ] GET /liverooms/by-code/ABC123 → 200 OK
- [ ] Code không tồn tại → 404 LIVEROOM_NOT_FOUND
- [ ] Code lowercase → upper-case tự động
- [ ] Không cần JWT (public endpoint)

---

## Task 1.3: POST /liverooms/{id}/join-requests (Idempotency)

**Mục tiêu**: User tạo yêu cầu tham gia phòng.

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.1
- `liveroom-business-requirements.md` R-JOIN-01..10
- `liveroom-state-machines.md` §1 (PENDING → APPROVED)

**Business rules**:
- R-JOIN-01: User phải authenticated
- R-JOIN-02: Room phải ACTIVE
- R-JOIN-03: User chưa ACTIVE trong phòng
- R-JOIN-04: Capacity (current + 1 + reserved) ≤ max
- R-JOIN-05: Idempotency (nếu đã có PENDING → trả existing)
- R-JOIN-06: User không bị LOCKED (3 rejects)
- R-JOIN-07: User chưa trong KICKED cooldown (5 min)
- R-JOIN-08: Room không thuộc user (owner không tự gửi request)

**Template Service**:

```java
@Transactional
public JoinRequestResponse createJoinRequest(UUID roomId, UUID currentUserId, Locale locale) {
    // Validate
    LiveRoom room = liveRoomRepository.findById(roomId)
        .orElseThrow(() -> new BusinessException("LIVEROOM_NOT_FOUND"));

    if (room.getStatus() != RoomStatus.ACTIVE) {
        throw new BusinessException("LIVEROOM_ROOM_ENDED");
    }

    if (room.getOwnerId().equals(currentUserId)) {
        throw new BusinessException("LIVEROOM_OWNER_CANNOT_REQUEST");
    }

    // Idempotency check
    Optional<JoinRequest> existing = joinRequestRepository.findByRoomIdAndUserId(roomId, currentUserId);
    if (existing.isPresent()) {
        JoinRequest req = existing.get();
        if (req.getState() == JoinRequestState.PENDING) {
            return JoinRequestResponse.fromEntity(req);
        }
        if (req.getState() == JoinRequestState.APPROVED) {
            throw new BusinessException("LIVEROOM_ALREADY_APPROVED");
        }
    }

    // Capacity check
    int effectiveMax = room.getMaxParticipants() - (room.getReservedOwnerSlot() ? 1 : 0);
    if (room.getCurrentParticipantCount() >= effectiveMax) {
        throw new BusinessException("LIVEROOM_CAPACITY_FULL");
    }

    // LOCKED check (3 rejects)
    Optional<RejectCounter> counter = rejectCounterRepository.findByRoomIdAndUserId(roomId, currentUserId);
    if (counter.isPresent() && counter.get().getRejectCountByOwner() >= 3) {
        throw new BusinessException("LIVEROOM_LOCKED_AFTER_3_REJECTS");
    }

    // KICKED cooldown check
    Optional<Participant> participant = participantRepository.findByRoomIdAndUserId(roomId, currentUserId);
    if (participant.isPresent() && participant.get().getKickedAt() != null) {
        long minutesSinceKick = ChronoUnit.MINUTES.between(participant.get().getKickedAt(), OffsetDateTime.now());
        if (minutesSinceKick < 5) {
            throw new BusinessException("LIVEROOM_KICKED_COOLDOWN");
        }
    }

    // Create
    JoinRequest request = JoinRequest.builder()
        .roomId(roomId)
        .userId(currentUserId)
        .state(JoinRequestState.PENDING)
        .requestedAt(OffsetDateTime.now())
        .build();
    JoinRequest saved = joinRequestRepository.save(request);

    // WS broadcast
    realtimeBroadcaster.broadcast(roomId, "JOIN_REQUEST_CREATED", JoinRequestEvent.fromEntity(saved));

    return JoinRequestResponse.fromEntity(saved);
}
```

**Acceptance Criteria**:
- [ ] User A gửi request → 201 Created
- [ ] User A gửi lại lần 2 → 200 OK (idempotent, return existing)
- [ ] User đã APPROVED → 409 LIVEROOM_ALREADY_APPROVED
- [ ] User đã bị 3 rejects → 403 LIVEROOM_LOCKED_AFTER_3_REJECTS
- [ ] User bị kick 4 phút trước → 403 LIVEROOM_KICKED_COOLDOWN
- [ ] User bị kick 6 phút trước → 201 Created
- [ ] Capacity full → 409 LIVEROOM_CAPACITY_FULL
- [ ] Room ENDED → 409 LIVEROOM_ROOM_ENDED
- [ ] WS event `JOIN_REQUEST_CREATED` broadcast đến owner

---

## Task 1.4: POST /join-requests/{id}/approve (Pessimistic Lock)

**Mục tiêu**: Owner duyệt user → user vào phòng ACTIVE.

**Doc tham chiếu**:
- `liveroom-api-spec.md` §2.2
- `liveroom-concurrency.md` §2.1 (Pessimistic lock)
- `liveroom-state-machines.md` §1 (PENDING → APPROVED → ACTIVE)

**Business rules**:
- R-APPROVE-01: Chỉ owner mới được approve
- R-APPROVE-02: JoinRequest phải PENDING
- R-APPROVE-03: Room phải ACTIVE
- R-APPROVE-04: Capacity check atomic
- R-APPROVE-05: Nếu capacity full → REJECTED với reason ROOM_FULL
- R-APPROVE-06: Update was_approved = TRUE (R-REOPEN-08)

**Template Service với Pessimistic Lock**:

```java
@Transactional
public void approveJoinRequest(UUID requestId, UUID currentUserId, Locale locale) {
    JoinRequest request = joinRequestRepository.findById(requestId)
        .orElseThrow(() -> new BusinessException("JOIN_REQUEST_NOT_FOUND"));

    // ⚠️ PESSIMISTIC_WRITE trên LiveRoom (KHÔNG dùng @Version)
    LiveRoom room = entityManager.find(
        LiveRoom.class,
        request.getRoomId(),
        LockModeType.PESSIMISTIC_WRITE
    );

    if (room.getOwnerId().equals(currentUserId)) {
        throw new BusinessException("LIVEROOM_NOT_OWNER");
    }

    if (room.getStatus() != RoomStatus.ACTIVE) {
        throw new BusinessException("LIVEROOM_ROOM_ENDED");
    }

    if (request.getState() != JoinRequestState.PENDING) {
        throw new BusinessException("JOIN_REQUEST_NOT_PENDING");
    }

    // Capacity check (atomic vì lock)
    int effectiveMax = room.getMaxParticipants() - (room.getReservedOwnerSlot() ? 1 : 0);
    if (room.getCurrentParticipantCount() >= effectiveMax) {
        request.setState(JoinRequestState.REJECTED);
        request.setRejectionReason(RejectionReason.ROOM_FULL);
        request.setResolvedAt(OffsetDateTime.now());
        joinRequestRepository.save(request);

        realtimeBroadcaster.broadcast(room.getId(), "REQUEST_REJECTED_BY_CAPACITY", ...);
        throw new BusinessException("LIVEROOM_CAPACITY_FULL");
    }

    // Approve
    request.setState(JoinRequestState.APPROVED);
    request.setResolvedAt(OffsetDateTime.now());
    joinRequestRepository.save(request);

    // Create participant
    Participant participant = Participant.builder()
        .roomId(room.getId())
        .userId(request.getUserId())
        .state(ParticipantState.ACTIVE)
        .joinedAt(OffsetDateTime.now())
        .rank(computeRank(room.getId())) // oldest first
        .wasApproved(true)
        .build();
    participantRepository.save(participant);

    // Update room
    room.setCurrentParticipantCount(room.getCurrentParticipantCount() + 1);
    liveRoomRepository.save(room);

    if (room.getCurrentParticipantCount() == effectiveMax) {
        realtimeBroadcaster.broadcast(room.getId(), "CAPACITY_REACHED", ...);
    }

    // WS broadcast
    realtimeBroadcaster.broadcast(room.getId(), "REQUEST_APPROVED", ...);
    realtimeBroadcaster.broadcast(room.getId(), "PARTICIPANT_JOINED", ...);

    // Personal notification to user
    realtimeBroadcaster.sendToUser(request.getUserId(), "REQUEST_APPROVED", ...);
}
```

**Acceptance Criteria**:
- [ ] Owner approve user A → 200 OK + user A vào phòng
- [ ] Non-owner approve → 403 LIVEROOM_NOT_OWNER
- [ ] PENDING → APPROVED state
- [ ] current_participant_count + 1
- [ ] Race condition: 2 requests cùng lúc khi còn 1 slot → 1 success, 1 fail với ROOM_FULL
- [ ] WS event `REQUEST_APPROVED` + `PARTICIPANT_JOINED` broadcast

---

## Task 1.5: GET /liverooms/{id}/state

**Mục tiêu**: Trả full state của phòng (cho user mới vào).

**Doc tham chiếu**:
- `liveroom-api-spec.md` §1.3

**Template**:

```java
public RoomStateResponse getRoomState(UUID roomId, UUID currentUserId) {
    LiveRoom room = liveRoomRepository.findById(roomId)
        .orElseThrow(() -> new BusinessException("LIVEROOM_NOT_FOUND"));

    if (!room.getOwnerId().equals(currentUserId)) {
        // Check user đã ACTIVE hoặc có PENDING/APPROVED request
        JoinRequest joinReq = joinRequestRepository.findByRoomIdAndUserId(roomId, currentUserId)
            .orElseThrow(() -> new BusinessException("LIVEROOM_NOT_IN_SESSION"));
        if (joinReq.getState() != JoinRequestState.APPROVED) {
            throw new BusinessException("LIVEROOM_NOT_APPROVED");
        }
    }

    List<Participant> participants = participantRepository.findByRoomIdAndState(roomId, ParticipantState.ACTIVE);

    return RoomStateResponse.builder()
        .roomId(room.getId())
        .roomCode(room.getRoomCode())
        .roomName(room.getRoomName())
        .ownerId(room.getOwnerId())
        .status(room.getStatus())
        .currentParticipants(room.getCurrentParticipantCount())
        .maxParticipants(room.getMaxParticipants())
        .ownerAbsent(room.getOwnerLeftAt() != null)
        .participants(participants.stream().map(ParticipantDto::fromEntity).toList())
        .build();
}
```

**Acceptance Criteria**:
- [ ] Owner GET → 200 OK + full state
- [ ] ACTIVE participant GET → 200 OK
- [ ] PENDING user GET → 403 LIVEROOM_NOT_APPROVED
- [ ] Stranger GET → 403 LIVEROOM_NOT_IN_SESSION
- [ ] Response có đầy đủ participants list

---

## Task 1.6: WS Events (3 events)

**Mục tiêu**: Broadcast 3 WS events cho join flow.

**Doc tham chiếu**:
- `liveroom-ws-protocol.md` §4.1

**3 events cần code**:

| Event | Trigger | Broadcast to | Payload |
|---|---|---|---|
| `JOIN_REQUEST_CREATED` | User gửi request | Owner + ACTIVE participants | `{requestId, userId, userEmail, requestedAt}` |
| `REQUEST_APPROVED` | Owner approve | Request owner + cả phòng | `{requestId, userId, roomState}` |
| `PARTICIPANT_JOINED` | User join room | Tất cả ACTIVE participants | `{userId, userEmail, joinedAt, rank}` |

**Template RealtimeBroadcaster**:

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveRoomRealtimeBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(UUID roomId, String eventType, Object payload) {
        String destination = "/topic/liveroom/" + roomId;
        WsEvent<Object> event = WsEvent.builder()
            .eventType(eventType)
            .timestamp(OffsetDateTime.now())
            .data(payload)
            .build();
        messagingTemplate.convertAndSend(destination, event);
        log.debug("WS broadcast: destination={}, eventType={}", destination, eventType);
    }

    public void sendToUser(UUID userId, String eventType, Object payload) {
        String destination = "/user/" + userId + "/queue/liveroom";
        WsEvent<Object> event = WsEvent.builder()
            .eventType(eventType)
            .timestamp(OffsetDateTime.now())
            .data(payload)
            .build();
        messagingTemplate.convertAndSend(destination, event);
    }
}
```

**Acceptance Criteria**:
- [ ] WS event `JOIN_REQUEST_CREATED` broadcast khi user gửi request
- [ ] WS event `REQUEST_APPROVED` broadcast khi owner approve
- [ ] WS event `PARTICIPANT_JOINED` broadcast khi user join
- [ ] Payload đúng schema trong ws-protocol.md
- [ ] Logger log event broadcasting

---

## Task 1.7-1.10: FE Screens

**Doc tham chiếu**:
- `liveroom-screen-inventory.md` §3-4 (screens)
- `liveroom-state-ui-mapping.md` §2 (state ↔ UI)
- `liveroom-user-flow.md` §5 (flows)

### Task 1.7: SC-01 Create Room

**Files**:
- `frontend/src/app/(live-room)/live-room/create/page.tsx`
- `frontend/src/features/liveroom/components/CreateRoomForm.tsx`

**Components**:
- Input `roomName` (max 100 chars)
- Slider `maxParticipants` (1-7)
- Slider `ownerGraceSeconds` (30-1800s)
- Button "Tạo phòng" → call `POST /liverooms`

**Acceptance Criteria**:
- [ ] Form validate đúng
- [ ] Loading state khi submit
- [ ] Success → redirect to `/live-room/[roomCode]`
- [ ] Error → toast message

---

### Task 1.8: SC-03 Pre-Join

**Files**:
- `frontend/src/app/(live-room)/live-room/page.tsx`
- `frontend/src/features/liveroom/components/PreJoinScreen.tsx`

**Components**:
- Input `roomCode` (6 chars)
- Button "Tìm phòng" → call `GET /liverooms/by-code/{code}`
- Hiển thị room info (name, participants count)
- Button "Xin vào phòng" → call `POST /liverooms/{id}/join-requests`

**Acceptance Criteria**:
- [ ] Code input uppercase tự động
- [ ] Code không tồn tại → toast "Mã phòng không tồn tại"
- [ ] Room full → disabled button + tooltip
- [ ] Click "Xin vào" → chuyển SC-04

---

### Task 1.9: SC-04 Waiting Room

**Files**:
- `frontend/src/features/liveroom/components/WaitingRoomCard.tsx`

**Components**:
- Hiển thị "Đang chờ owner duyệt..."
- Avatar user (nếu có)
- Cancel button → call `POST /join-requests/{id}/cancel`

**Acceptance Criteria**:
- [ ] Click "Xin vào" → hiển thị waiting room
- [ ] WS event `REQUEST_APPROVED` → chuyển SC-06
- [ ] WS event `REQUEST_REJECTED_BY_OWNER` → hiển thị rejected UI
- [ ] Cancel button → về SC-01

---

### Task 1.10: SC-06 In-Room Basic

**Files**:
- `frontend/src/app/(live-room)/live-room/[roomCode]/page.tsx`
- `frontend/src/features/liveroom/components/ImmersiveMeetingRoom.tsx`
- `frontend/src/features/liveroom/components/MediaTile.tsx`

**Components (Phase 1 chỉ cần basic)**:
- Header: room name, room code
- Participant grid (chỉ hiển thị avatar, chưa có video)
- Footer: leave button

**Acceptance Criteria**:
- [ ] WS event `PARTICIPANT_JOINED` → thêm user vào grid
- [ ] WS event `PARTICIPANT_LEFT` → xoá user khỏi grid
- [ ] Render đúng số participants

---

## Task 1.11: WS Subscription (FE)

**Mục tiêu**: Subscribe WS events từ frontend.

**Doc tham chiếu**:
- `liveroom-ws-protocol.md` §3

**Template** `useLiveRoomSocket.ts`:

```typescript
import { useEffect } from "react";
import { useStompClient } from "@/lib/stomp-client";

export function useLiveRoomSocket(roomId: string, handlers: {
  onJoinRequest?: (data: any) => void;
  onApproved?: (data: any) => void;
  onParticipantJoined?: (data: any) => void;
}) {
  const stompClient = useStompClient();

  useEffect(() => {
    if (!stompClient) return;

    const subscription = stompClient.subscribe(
      `/topic/liveroom/${roomId}`,
      (message) => {
        const event = JSON.parse(message.body);
        switch (event.eventType) {
          case "JOIN_REQUEST_CREATED":
            handlers.onJoinRequest?.(event.data);
            break;
          case "REQUEST_APPROVED":
            handlers.onApproved?.(event.data);
            break;
          case "PARTICIPANT_JOINED":
            handlers.onParticipantJoined?.(event.data);
            break;
        }
      }
    );

    return () => subscription.unsubscribe();
  }, [stompClient, roomId]);
}
```

**Acceptance Criteria**:
- [ ] Subscribe `/topic/liveroom/{roomId}` thành công
- [ ] Nhận event `JOIN_REQUEST_CREATED` → trigger handler
- [ ] Unmount component → unsubscribe

---

## Task 1.12: i18n Keys

**Mục tiêu**: Add ~10 keys mới vào messages files.

**Doc tham chiếu**:
- `liveroom-i18n-keys.md`

**Keys cần thêm**:
- `liveroom.create.success`
- `liveroom.create.error`
- `liveroom.create.form.title`
- `liveroom.create.form.room_name`
- `liveroom.create.form.max_participants`
- `liveroom.create.form.grace_seconds`
- `liveroom.create.form.submit`
- `liveroom.prejoin.search.placeholder`
- `liveroom.prejoin.search.not_found`
- `liveroom.waiting.cancel`

**Files cần sửa**:
- `Backend/shared-web/src/main/resources/messages/messages.properties`
- `Backend/shared-web/src/main/resources/messages/messages_en.properties`
- `Backend/shared-web/src/main/resources/messages/messages_vi.properties`
- `frontend/messages/en.json`
- `frontend/messages/vi.json`

**Acceptance Criteria**:
- [ ] Tất cả keys có EN + VI
- [ ] Cả backend + frontend load được

---

## Task 1.13: E2E Test

**Mục tiêu**: Test 2 browsers vào cùng phòng.

**Doc tham chiếu**:
- `liveroom-user-flow.md` UC-01, UC-02

**Test scenario**:
1. Browser A: Login user A (PRO plan)
2. Browser A: Create room "Test Room"
3. Browser A: Lưu roomCode
4. Browser B: Login user B (PRO plan)
5. Browser B: Vào `/live-room`, nhập roomCode
6. Browser B: Click "Xin vào phòng"
7. Browser A: Thấy user B trong owner waiting room
8. Browser A: Click "Duyệt"
9. Browser A: User B vào phòng
10. Browser B: Thấy chính mình + user A trong phòng
11. Browser A: Click "Rời phòng"
12. Browser A: User B thấy participant count giảm

**Acceptance Criteria**:
- [ ] 2 browsers vào cùng phòng thành công
- [ ] WS events realtime
- [ ] Leave không crash app

---

## 🚦 Definition of Done Phase 1

- [ ] Tất cả 13 tasks DONE
- [ ] 5 BE endpoints + 3 WS events hoạt động
- [ ] 4 FE screens render đúng
- [ ] i18n EN + VI load được
- [ ] E2E test 2 browsers pass
- [ ] Pessimistic lock protect race condition
- [ ] Idempotency works (gửi request 2 lần → 1 row)
- [ ] Self-review với `08-self-review-checklist.md` pass

---

**Cập nhật**: 2026-07-26
