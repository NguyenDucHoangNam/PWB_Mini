package com.pwb.liveroom.core.service;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.liveroom.core.model.LiveRoom;
import com.pwb.liveroom.core.model.LiveRoomParticipant;
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
    public Optional<LiveRoomParticipant> leaveRoom(UUID userId, String roomCode) {
        log.info("Leaving live room: userId={}, roomCode={}", userId, roomCode);

        Optional<LiveRoomParticipantJpaEntity> existing =
                participantJpaRepository.findActiveByRoomAndUser(roomCode, userId);

        if (existing.isEmpty()) {
            log.info("Leave no-op (not joined): userId={}, roomCode={}", userId, roomCode);
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
            LiveRoom domain = liveRoomMapper.toDomain(roomEntity);
            domain.decrementParticipants();
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
                        .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_JOINED));

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
