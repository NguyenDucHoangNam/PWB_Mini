package com.pwb.liveroom.core.service;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.liveroom.core.model.LiveroomDomainException;
import com.pwb.liveroom.core.model.LiveRoom;
import com.pwb.liveroom.core.model.LiveRoomParticipant;
import com.pwb.liveroom.core.model.LiveRoomStatus;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomParticipantJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.mapper.LiveRoomMapper;
import com.pwb.liveroom.infrastructure.persistence.mapper.LiveRoomParticipantMapper;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomJpaRepository;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomParticipantJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static java.util.Map.entry;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveRoomParticipantServiceImpl implements LiveRoomParticipantService {

    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final String HOST_DISPLAY_NAME_FALLBACK = "Host";

    private final LiveRoomJpaRepository liveRoomJpaRepository;
    private final LiveRoomParticipantJpaRepository participantJpaRepository;
    private final LiveRoomMapper liveRoomMapper;
    private final LiveRoomParticipantMapper participantMapper;
    private final LiveRoomRealtimeBroadcaster broadcaster;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public LiveRoomParticipant joinPublicRoom(UUID userId, String roomCode, String displayName, String role) {
        log.debug("Joining public room: userId={}, roomCode={}", userId, roomCode);

        LiveRoomJpaEntity roomEntity = liveRoomJpaRepository
                .findByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND));

        if (roomEntity.getStatus() != com.pwb.liveroom.core.model.LiveRoomStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.LIVEROOM_ALREADY_ENDED);
        }
        if (roomEntity.getMode() != com.pwb.liveroom.api.enums.LiveRoomMode.PUBLIC) {
            throw new BusinessException(ErrorCode.LIVEROOM_MODE_NOT_JOINABLE);
        }

        Optional<LiveRoomParticipantJpaEntity> existing =
                participantJpaRepository.findActiveByRoomAndUser(roomCode, userId);
        if (existing.isPresent()) {
            LiveRoomParticipantJpaEntity existingEntity = existing.get();
            Instant refreshAt = Instant.now();
            existingEntity.setLastSeenAt(refreshAt);
            participantJpaRepository.save(existingEntity);
            return participantMapper.toDomain(existingEntity);
        }

        if (roomEntity.getCurrentParticipantCount() >= roomEntity.getMaxParticipants()) {
            throw new BusinessException(ErrorCode.LIVEROOM_FULL);
        }

        LiveRoomParticipant participant;
        try {
            participant = LiveRoomParticipant.join(
                    roomCode, userId, displayName, role, Instant.now());
        } catch (LiveroomDomainException ex) {
            throw mapDomainException(ex);
        }
        LiveRoomParticipantJpaEntity saved = participantJpaRepository.save(
                participantMapper.toEntity(participant));

        LiveRoom liveRoomDomain = liveRoomMapper.toDomain(roomEntity);
        try {
            liveRoomDomain.incrementParticipants();
        } catch (LiveroomDomainException ex) {
            throw mapDomainException(ex);
        }
        liveRoomMapper.toEntity(liveRoomDomain, roomEntity);
        liveRoomJpaRepository.save(roomEntity);

        eventPublisher.publishEvent(new ParticipantJoinedEvent(
                saved.getRoomCode(),
                roomEntity.getHostUserId(),
                saved.getUserId(),
                saved.getDisplayName(),
                saved.getRoleAtJoin(),
                roomEntity.getCurrentParticipantCount(),
                roomEntity.getMaxParticipants(),
                Math.max(0, roomEntity.getMaxParticipants() - roomEntity.getCurrentParticipantCount()),
                saved.getJoinedAt()));

        return participantMapper.toDomain(saved);
    }

    @Override
    @Transactional
    public Optional<LiveRoomParticipant> joinAsHost(UUID userId, String hostDisplayName, String roomCode) {
        log.debug("Host joining room: userId={}, roomCode={}", userId, roomCode);

        Optional<LiveRoomJpaEntity> roomOpt =
                liveRoomJpaRepository.findByRoomCodeForUpdate(roomCode);
        if (roomOpt.isEmpty()) {
            return Optional.empty();
        }
        LiveRoomJpaEntity roomEntity = roomOpt.get();
        if (!roomEntity.getHostUserId().equals(userId)) {
            return Optional.empty();
        }
        if (roomEntity.getStatus() != LiveRoomStatus.ACTIVE) {
            return Optional.empty();
        }

        String resolvedDisplayName = resolveHostDisplayName(hostDisplayName);
        if (resolvedDisplayName.equals(HOST_DISPLAY_NAME_FALLBACK)) {
            log.warn("Host display name missing on join, fallback applied: userId={}, roomCode={}",
                    userId, roomCode);
        }

        Optional<LiveRoomParticipantJpaEntity> existing =
                participantJpaRepository.findActiveByRoomAndUser(roomCode, userId);
        if (existing.isPresent()) {
            LiveRoomParticipantJpaEntity existingEntity = existing.get();
            existingEntity.setDisplayName(resolvedDisplayName);
            existingEntity.setLastSeenAt(Instant.now());
            participantJpaRepository.save(existingEntity);
            LiveRoomParticipant refreshed = participantMapper.toDomain(existingEntity);
            eventPublisher.publishEvent(new ParticipantJoinedEvent(
                    refreshed.getRoomCode(),
                    roomEntity.getHostUserId(),
                    refreshed.getUserId(),
                    refreshed.getDisplayName(),
                    refreshed.getRoleAtJoin(),
                    roomEntity.getCurrentParticipantCount(),
                    roomEntity.getMaxParticipants(),
                    Math.max(0, roomEntity.getMaxParticipants() - roomEntity.getCurrentParticipantCount()),
                    refreshed.getJoinedAt() != null ? refreshed.getJoinedAt() : Instant.now()));
            return Optional.of(refreshed);
        }

        Optional<LiveRoomParticipantJpaEntity> softExisting =
                participantJpaRepository.findFirstByRoomCodeAndUserIdIncludeLeft(roomCode, userId);

        LiveRoomParticipantJpaEntity rejoined;
        boolean shouldIncrement;
        if (softExisting.isPresent()) {
            LiveRoomParticipantJpaEntity entity = softExisting.get();
            Instant previousLeftAt = entity.getLeftAt();
            entity.setLeftAt(null);
            entity.setDisplayName(resolvedDisplayName);
            entity.setLastSeenAt(Instant.now());
            rejoined = participantJpaRepository.save(entity);
            shouldIncrement = previousLeftAt != null;
        } else {
            if (roomEntity.getCurrentParticipantCount() >= roomEntity.getMaxParticipants()) {
                throw new BusinessException(ErrorCode.LIVEROOM_FULL);
            }
            LiveRoomParticipant hostParticipant = LiveRoomParticipant.join(
                    roomCode, userId, resolvedDisplayName, "PRO", Instant.now());
            rejoined = participantJpaRepository.save(participantMapper.toEntity(hostParticipant));
            shouldIncrement = true;
        }

        if (shouldIncrement) {
            LiveRoom liveRoomDomain = liveRoomMapper.toDomain(roomEntity);
            try {
                liveRoomDomain.incrementParticipants();
            } catch (LiveroomDomainException ex) {
                throw mapDomainException(ex);
            }
            liveRoomMapper.toEntity(liveRoomDomain, roomEntity);
            liveRoomJpaRepository.save(roomEntity);
        }

        LiveRoomParticipant rejoinedDomain = participantMapper.toDomain(rejoined);
        eventPublisher.publishEvent(new ParticipantJoinedEvent(
                rejoined.getRoomCode(),
                roomEntity.getHostUserId(),
                rejoined.getUserId(),
                rejoinedDomain.getDisplayName(),
                rejoinedDomain.getRoleAtJoin(),
                roomEntity.getCurrentParticipantCount(),
                roomEntity.getMaxParticipants(),
                Math.max(0, roomEntity.getMaxParticipants() - roomEntity.getCurrentParticipantCount()),
                rejoinedDomain.getJoinedAt() != null ? rejoinedDomain.getJoinedAt() : Instant.now()));

        return Optional.of(rejoinedDomain);
    }

    private static String resolveHostDisplayName(String provided) {
        if (provided == null) {
            return HOST_DISPLAY_NAME_FALLBACK;
        }
        String trimmed = provided.trim();
        if (trimmed.isEmpty()) {
            return HOST_DISPLAY_NAME_FALLBACK;
        }
        if (trimmed.length() > MAX_DISPLAY_NAME_LENGTH) {
            return trimmed.substring(0, MAX_DISPLAY_NAME_LENGTH);
        }
        return trimmed;
    }

    @Override
    @Transactional
    public Optional<LiveRoomParticipant> leaveRoom(UUID userId, String roomCode) {
        log.info("Leaving live room: userId={}, roomCode={}", userId, roomCode);

        Optional<LiveRoomParticipantJpaEntity> existing =
                participantJpaRepository.findActiveByRoomAndUser(roomCode, userId);

        if (existing.isEmpty()) {
            log.debug("Leave ignored: user not joined, userId={}, roomCode={}", userId, roomCode);
            return Optional.empty();
        }

        LiveRoomParticipantJpaEntity participantEntity = existing.get();
        Instant now = Instant.now();
        participantEntity.setLeftAt(now);
        participantJpaRepository.save(participantEntity);

        Optional<LiveRoomJpaEntity> roomEntityOpt =
                liveRoomJpaRepository.findByRoomCodeForUpdate(roomCode);

        if (roomEntityOpt.isPresent()) {
            LiveRoomJpaEntity roomEntity = roomEntityOpt.get();
            long activeAfterLeave = participantJpaRepository.countActiveByRoom(roomCode);
            int denormBefore = roomEntity.getCurrentParticipantCount();

            LiveRoom domain = liveRoomMapper.toDomain(roomEntity);
            domain.syncParticipantCount((int) activeAfterLeave);

            if (denormBefore != (int) activeAfterLeave) {
                log.warn("Participant count drift corrected on leave: userId={}, roomCode={}, denormBefore={}, dbActiveAfterLeave={}",
                        userId, roomCode, denormBefore, activeAfterLeave);
            }

            liveRoomMapper.toEntity(domain, roomEntity);
            liveRoomJpaRepository.save(roomEntity);

            eventPublisher.publishEvent(new ParticipantLeftEvent(
                    roomCode,
                    roomEntity.getHostUserId(),
                    userId,
                    participantEntity.getDisplayName(),
                    roomEntity.getCurrentParticipantCount(),
                    roomEntity.getMaxParticipants(),
                    Math.max(0, roomEntity.getMaxParticipants() - roomEntity.getCurrentParticipantCount()),
                    now));
        }

        LiveRoomParticipant participant = participantMapper.toDomain(participantEntity);
        log.info("Participant left: userId={}, roomCode={}, participantId={}",
                userId, roomCode, participant.getId());

        return Optional.of(participant);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LiveRoomParticipant> listActiveParticipants(String roomCode) {
        if (!liveRoomJpaRepository.existsByRoomCodeAndDeletedFalse(roomCode)) {
            throw new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND);
        }
        return participantJpaRepository.findActiveByRoom(roomCode).stream()
                .map(participantMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public LiveRoomParticipant updateMediaState(UUID userId, String roomCode, boolean micMuted, boolean cameraOff) {
        log.info("Updating media state: userId={}, roomCode={}, micMuted={}, cameraOff={}",
                userId, roomCode, micMuted, cameraOff);

        LiveRoomParticipantJpaEntity participantEntity =
                participantJpaRepository.findActiveByRoomAndUser(roomCode, userId)
                        .orElse(null);

        if (participantEntity == null) {
            throw new BusinessException(ErrorCode.LIVEROOM_NOT_JOINED);
        }

        LiveRoomParticipant participant = participantMapper.toDomain(participantEntity);
        try {
            participant.updateMediaState(micMuted, cameraOff);
        } catch (LiveroomDomainException ex) {
            throw mapDomainException(ex);
        }
        participantMapper.toEntity(participant, participantEntity);
        participantJpaRepository.save(participantEntity);

        LiveRoomParticipant updated = participantMapper.toDomain(participantEntity);
        eventPublisher.publishEvent(new MediaStateChangedEvent(
                roomCode,
                userId,
                updated.getDisplayName(),
                updated.isMicMuted(),
                updated.isCameraOff(),
                updated.getLastSeenAt()));

        return updated;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveParticipant(String roomCode, UUID userId) {
        if (roomCode == null || userId == null) {
            return false;
        }
        return participantJpaRepository.findActiveByRoomAndUser(roomCode, userId).isPresent();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onParticipantJoined(ParticipantJoinedEvent event) {
        broadcaster.broadcastParticipantJoined(
                event.roomCode(),
                event.userId(),
                event.displayName(),
                event.roleAtJoin(),
                event.currentCount(),
                event.maxParticipants(),
                event.availableSlots(),
                event.joinedAt().toString());

        broadcaster.broadcastPeerJoined(
                event.roomCode(),
                event.userId(),
                event.displayName(),
                event.joinedAt().toString());

        pushRoomStateToNewcomer(event.roomCode(), event.userId());
    }

    private void pushRoomStateToNewcomer(String roomCode, UUID newcomerUserId) {
        List<Map<String, String>> existingPeers = participantJpaRepository.findActiveByRoom(roomCode).stream()
                .filter(p -> !p.getUserId().equals(newcomerUserId))
                .map(p -> Map.of(
                        "userId", p.getUserId().toString(),
                        "displayName", p.getDisplayName() == null ? "" : p.getDisplayName()))
                .toList();
        broadcaster.pushRoomStateToUser(roomCode, newcomerUserId, existingPeers);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onParticipantLeft(ParticipantLeftEvent event) {
        broadcaster.broadcastParticipantLeft(
                event.roomCode(),
                event.userId(),
                event.displayName(),
                event.currentCount(),
                event.maxParticipants(),
                event.availableSlots(),
                event.leftAt().toString());

        broadcaster.broadcastPeerLeft(
                event.roomCode(),
                event.userId(),
                event.leftAt().toString());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMediaStateChanged(MediaStateChangedEvent event) {
        broadcaster.broadcastMediaStateChanged(
                event.roomCode(),
                event.userId(),
                event.displayName(),
                event.micMuted(),
                event.cameraOff(),
                event.lastSeenAt().toString());
    }

    @Override
    @Transactional(readOnly = true)
    public void requestRoomState(UUID userId, String roomCode) {
        if (!liveRoomJpaRepository.existsByRoomCodeAndDeletedFalse(roomCode)) {
            log.warn("requestRoomState ignored: room not found, roomCode={}, userId={}", roomCode, userId);
            return;
        }
        Optional<LiveRoomParticipantJpaEntity> requester =
                participantJpaRepository.findActiveByRoomAndUser(roomCode, userId);
        if (requester.isEmpty()) {
            log.warn("requestRoomState ignored: user not joined, roomCode={}, userId={}", roomCode, userId);
            return;
        }
        log.info("requestRoomState: roomCode={}, userId={}", roomCode, userId);
        pushRoomStateToNewcomer(roomCode, userId);
    }

    public record ParticipantJoinedEvent(
            String roomCode,
            UUID hostUserId,
            UUID userId,
            String displayName,
            String roleAtJoin,
            int currentCount,
            int maxParticipants,
            int availableSlots,
            Instant joinedAt
    ) {}

    public record ParticipantLeftEvent(
            String roomCode,
            UUID hostUserId,
            UUID userId,
            String displayName,
            int currentCount,
            int maxParticipants,
            int availableSlots,
            Instant leftAt
    ) {}

    public record MediaStateChangedEvent(
            String roomCode,
            UUID userId,
            String displayName,
            boolean micMuted,
            boolean cameraOff,
            Instant lastSeenAt
    ) {}

    private static final Map<String, ErrorCode> DOMAIN_ERROR_CODE_MAP = Map.ofEntries(
            entry("LIVEROOM_FULL", ErrorCode.LIVEROOM_FULL),
            entry("LIVEROOM_NOT_ACTIVE", ErrorCode.LIVEROOM_ALREADY_ENDED),
            entry("LIVEROOM_PAUSED", ErrorCode.LIVEROOM_ALREADY_ENDED),
            entry("LIVEROOM_ALREADY_ENDED", ErrorCode.LIVEROOM_ALREADY_ENDED),
            entry("LIVEROOM_CAPACITY_INVALID", ErrorCode.LIVEROOM_INVALID_CAPACITY),
            entry("LIVEROOM_CODE_INVALID_LENGTH", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_CODE_INVALID_CHARS", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_DISPLAY_NAME_REQUIRED", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_DISPLAY_NAME_TOO_LONG", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_ROLE_REQUIRED", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_ROLE_INVALID", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_LEFT_BEFORE_JOIN", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_ALREADY_LEFT_MEDIA", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_USER_ID_REQUIRED", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_CAPACITY_LOWER_THAN_CURRENT", ErrorCode.LIVEROOM_INVALID_CAPACITY),
            entry("LIVEROOM_DECIDED_BY_REQUIRED", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_JOIN_REQUEST_NOT_OWNER", ErrorCode.LIVEROOM_JOIN_REQUEST_NOT_OWNER),
            entry("LIVEROOM_JOIN_REQUEST_NOT_PENDING", ErrorCode.LIVEROOM_JOIN_REQUEST_NOT_PENDING),
            entry("LIVEROOM_JOIN_REQUEST_MESSAGE_TOO_LONG", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_JOIN_REQUEST_REASON_TOO_LONG", ErrorCode.INVALID_INPUT)
    );

    private BusinessException mapDomainException(LiveroomDomainException ex) {
        ErrorCode ec = DOMAIN_ERROR_CODE_MAP.getOrDefault(ex.getErrorKey(), ErrorCode.INVALID_INPUT);
        return new BusinessException(ec);
    }
}
