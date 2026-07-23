package com.pwb.liveroom.core.service;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveRoomParticipantServiceImpl implements LiveRoomParticipantService {

    private final LiveRoomJpaRepository liveRoomJpaRepository;
    private final LiveRoomParticipantJpaRepository participantJpaRepository;
    private final LiveRoomMapper liveRoomMapper;
    private final LiveRoomParticipantMapper participantMapper;
    private final LiveRoomRealtimeBroadcaster broadcaster;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public LiveRoomParticipant joinPublicRoom(UUID userId, String roomCode, String displayName, String role) {
        log.info("DIAG_JOIN_PUBLIC_ENTER: userId={}, roomCode={}, displayName={}, role={}",
                userId, roomCode, displayName, role);

        LiveRoomJpaEntity roomEntity = liveRoomJpaRepository
                .findByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND));

        if (roomEntity.getStatus() != com.pwb.liveroom.core.model.LiveRoomStatus.ACTIVE) {
            log.info("DIAG_JOIN_PUBLIC_BRANCH: NOT_ACTIVE, userId={}, roomCode={}", userId, roomCode);
            throw new BusinessException(ErrorCode.LIVEROOM_ALREADY_ENDED);
        }
        if (roomEntity.getMode() != com.pwb.liveroom.api.enums.LiveRoomMode.PUBLIC) {
            log.info("DIAG_JOIN_PUBLIC_BRANCH: NOT_PUBLIC, userId={}, roomCode={}", userId, roomCode);
            throw new BusinessException(ErrorCode.LIVEROOM_MODE_NOT_JOINABLE);
        }

        Optional<LiveRoomParticipantJpaEntity> existing =
                participantJpaRepository.findActiveByRoomAndUser(roomCode, userId);
        if (existing.isPresent()) {
            LiveRoomParticipantJpaEntity existingEntity = existing.get();
            log.info("DIAG_JOIN_PUBLIC_BRANCH: ALREADY_ACTIVE, userId={}, roomCode={}, participantId={}, denormCount={}",
                    userId, roomCode, existingEntity.getId(), roomEntity.getCurrentParticipantCount());

            Instant refreshAt = Instant.now();
            existingEntity.setLastSeenAt(refreshAt);
            participantJpaRepository.save(existingEntity);

            LiveRoomParticipant refreshed = participantMapper.toDomain(existingEntity);
            log.info("DIAG_JOIN_PUBLIC_EXIT_NOOP: userId={}, roomCode={}, participantId={}, reason=idempotent_no_broadcast",
                    userId, roomCode, refreshed.getId());
            return refreshed;
        }

        if (roomEntity.getCurrentParticipantCount() >= roomEntity.getMaxParticipants()) {
            log.info("DIAG_JOIN_PUBLIC_BRANCH: FULL, userId={}, roomCode={}, denorm={}, max={}",
                    userId, roomCode, roomEntity.getCurrentParticipantCount(), roomEntity.getMaxParticipants());
            throw new BusinessException(ErrorCode.LIVEROOM_FULL);
        }

        LiveRoomParticipant participant = LiveRoomParticipant.join(
                roomCode, userId, displayName, role, Instant.now());
        LiveRoomParticipantJpaEntity saved = participantJpaRepository.save(
                participantMapper.toEntity(participant));

        LiveRoom liveRoomDomain = liveRoomMapper.toDomain(roomEntity);
        try {
            liveRoomDomain.incrementParticipants();
        } catch (IllegalStateException ex) {
            log.warn("DIAG_JOIN_PUBLIC_INCREMENT_FAIL: userId={}, roomCode={}, reason={}",
                    userId, roomCode, ex.getMessage());
            throw new BusinessException(ErrorCode.LIVEROOM_FULL);
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

        log.info("DIAG_JOIN_PUBLIC_EXIT_NEW: userId={}, roomCode={}, participantId={}, denormAfter={}",
                userId, roomCode, saved.getId(), roomEntity.getCurrentParticipantCount());
        return participantMapper.toDomain(saved);
    }

    @Override
    @Transactional
    public Optional<LiveRoomParticipant> leaveRoom(UUID userId, String roomCode) {
        log.info("DIAG_LEAVE_ENTER: userId={}, roomCode={}", userId, roomCode);

        Optional<LiveRoomParticipantJpaEntity> existing =
                participantJpaRepository.findActiveByRoomAndUser(roomCode, userId);

        if (existing.isEmpty()) {
            log.info("DIAG_LEAVE_BRANCH: NOT_JOINED, userId={}, roomCode={}", userId, roomCode);
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
            long activeBefore = participantJpaRepository.findActiveByRoom(roomCode).size();
            int denormBefore = roomEntity.getCurrentParticipantCount();
            LiveRoom domain = liveRoomMapper.toDomain(roomEntity);

            if (denormBefore > activeBefore) {
                domain.syncParticipantCount((int) activeBefore);
                log.warn("DIAG_LEAVE_SYNC_RECOUNT: userId={}, roomCode={}, denormBefore={}, dbActive={}, denormAfter={}",
                        userId, roomCode, denormBefore, activeBefore, (int) activeBefore);
            }

            domain.decrementParticipants();
            liveRoomMapper.toEntity(domain, roomEntity);
            liveRoomJpaRepository.save(roomEntity);

            log.info("DIAG_LEAVE_DECREMENT: userId={}, roomCode={}, dbActiveBefore={}, denormBefore={}, denormAfter={}",
                    userId, roomCode, activeBefore, denormBefore, roomEntity.getCurrentParticipantCount());

            eventPublisher.publishEvent(new ParticipantLeftEvent(
                    roomCode,
                    roomEntity.getHostUserId(),
                    userId,
                    participantEntity.getDisplayName(),
                    roomEntity.getCurrentParticipantCount(),
                    roomEntity.getMaxParticipants(),
                    Math.max(0, roomEntity.getMaxParticipants() - roomEntity.getCurrentParticipantCount()),
                    now));
        } else {
            log.info("DIAG_LEAVE_BRANCH: NO_ROOM, userId={}, roomCode={}", userId, roomCode);
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
            LiveRoomParticipant rejoined = joinAsHost(userId, roomCode)
                    .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_JOINED));
            participantEntity = participantJpaRepository
                    .findActiveByRoomAndUser(roomCode, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_JOINED));
        }

        LiveRoomParticipant participant = participantMapper.toDomain(participantEntity);
        participant.updateMediaState(micMuted, cameraOff);
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
    @Transactional
    public Optional<LiveRoomParticipant> joinAsHost(UUID userId, String roomCode) {
        log.info("DIAG_JOIN_HOST_ENTER: userId={}, roomCode={}", userId, roomCode);

        Optional<LiveRoomJpaEntity> roomOpt =
                liveRoomJpaRepository.findByRoomCodeForUpdate(roomCode);
        if (roomOpt.isEmpty()) {
            log.info("DIAG_JOIN_HOST_BRANCH: NO_ROOM, userId={}, roomCode={}", userId, roomCode);
            return Optional.empty();
        }
        LiveRoomJpaEntity roomEntity = roomOpt.get();
        if (!roomEntity.getHostUserId().equals(userId)) {
            log.info("DIAG_JOIN_HOST_BRANCH: NOT_HOST, userId={}, roomCode={}, host={}",
                    userId, roomCode, roomEntity.getHostUserId());
            return Optional.empty();
        }
        if (roomEntity.getStatus() != LiveRoomStatus.ACTIVE) {
            log.info("DIAG_JOIN_HOST_BRANCH: NOT_ACTIVE, userId={}, roomCode={}, status={}",
                    userId, roomCode, roomEntity.getStatus());
            return Optional.empty();
        }

        long activeCountInDb = participantJpaRepository.findActiveByRoom(roomCode).size();
        log.info("DIAG_JOIN_HOST_PRE: userId={}, roomCode={}, dbActiveCount={}, denormCount={}, roomId={}",
                userId, roomCode, activeCountInDb,
                roomEntity.getCurrentParticipantCount(), roomEntity.getId());

        Optional<LiveRoomParticipantJpaEntity> existing =
                participantJpaRepository.findActiveByRoomAndUser(roomCode, userId);
        if (existing.isPresent()) {
            LiveRoomParticipantJpaEntity existingEntity = existing.get();
            existingEntity.setLastSeenAt(Instant.now());
            participantJpaRepository.save(existingEntity);
            LiveRoomParticipant refreshed = participantMapper.toDomain(existingEntity);
            log.info("DIAG_JOIN_HOST_BRANCH: ACTIVE_EXISTING, userId={}, roomCode={}, participantId={}, denormCount={}, reason=idempotent_no_broadcast",
                    userId, roomCode, existingEntity.getId(), roomEntity.getCurrentParticipantCount());
            return Optional.of(refreshed);
        }

        Optional<LiveRoomParticipantJpaEntity> softExisting =
                participantJpaRepository.findFirstByRoomCodeAndUserIdIncludeLeft(roomCode, userId);

        LiveRoomParticipantJpaEntity rejoined;
        boolean shouldIncrement;
        if (softExisting.isPresent()) {
            LiveRoomParticipantJpaEntity entity = softExisting.get();
            log.info("DIAG_JOIN_HOST_BRANCH: SOFT_EXISTING, userId={}, roomCode={}, participantId={}, leftAt={}",
                    userId, roomCode, entity.getId(), entity.getLeftAt());
            entity.setLeftAt(null);
            entity.setLastSeenAt(Instant.now());
            rejoined = participantJpaRepository.save(entity);
            shouldIncrement = false;
        } else {
            if (roomEntity.getCurrentParticipantCount() >= roomEntity.getMaxParticipants()) {
                log.warn("DIAG_JOIN_HOST_BRANCH: FULL_REJECTED, userId={}, roomCode={}, denorm={}, max={}",
                        userId, roomCode, roomEntity.getCurrentParticipantCount(),
                        roomEntity.getMaxParticipants());
                throw new BusinessException(ErrorCode.LIVEROOM_FULL);
            }
            LiveRoomParticipant hostParticipant = LiveRoomParticipant.join(
                    roomCode, userId, "Host", "PRO", Instant.now());
            rejoined = participantJpaRepository.save(participantMapper.toEntity(hostParticipant));
            shouldIncrement = true;
            log.info("DIAG_JOIN_HOST_BRANCH: NEW_RECORD, userId={}, roomCode={}, participantId={}",
                    userId, roomCode, rejoined.getId());
        }

        if (shouldIncrement) {
            LiveRoom liveRoomDomain = liveRoomMapper.toDomain(roomEntity);
            int countBefore = liveRoomDomain.getCurrentParticipantCount();
            try {
                liveRoomDomain.incrementParticipants();
            } catch (IllegalStateException ex) {
                log.warn("DIAG_JOIN_HOST_INCREMENT_FAIL: userId={}, roomCode={}, countBefore={}, reason={}",
                        userId, roomCode, countBefore, ex.getMessage());
                throw new BusinessException(ErrorCode.LIVEROOM_FULL);
            }
            int countAfter = liveRoomDomain.getCurrentParticipantCount();
            liveRoomMapper.toEntity(liveRoomDomain, roomEntity);
            liveRoomJpaRepository.save(roomEntity);
            log.info("DIAG_JOIN_HOST_INCREMENTED: userId={}, roomCode={}, countBefore={}, countAfter={}",
                    userId, roomCode, countBefore, countAfter);
        } else {
            log.info("DIAG_JOIN_HOST_NO_INCREMENT: userId={}, roomCode={}, currentDenorm={}",
                    userId, roomCode, roomEntity.getCurrentParticipantCount());
        }

        long activeCountAfter = participantJpaRepository.findActiveByRoom(roomCode).size();
        log.info("DIAG_JOIN_HOST_EXIT: userId={}, roomCode={}, dbActiveCount={}, denormCount={}",
                userId, roomCode, activeCountAfter, roomEntity.getCurrentParticipantCount());

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
}
