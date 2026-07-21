package com.pwb.liveroom.core.service;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.liveroom.api.enums.LiveRoomMode;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveRoomParticipantServiceImpl implements LiveRoomParticipantService {

    private static final String ROOM_TOPIC_BASE = "/topic/room/";
    private static final String PARTICIPANTS_TOPIC_SUFFIX = "/participants";

    private final LiveRoomJpaRepository liveRoomJpaRepository;
    private final LiveRoomParticipantJpaRepository participantJpaRepository;
    private final LiveRoomMapper liveRoomMapper;
    private final LiveRoomParticipantMapper participantMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public LiveRoomParticipant joinRoom(
            UUID userId,
            String displayName,
            String role,
            String roomCode) {

        log.info("Joining live room: userId={}, roomCode={}", userId, roomCode);

        LiveRoomJpaEntity roomEntity = liveRoomJpaRepository
                .findByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND));

        if (roomEntity.getStatus() != LiveRoomStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.LIVEROOM_ALREADY_ENDED);
        }

        if (roomEntity.getMode() != LiveRoomMode.PUBLIC) {
            throw new BusinessException(ErrorCode.LIVEROOM_MODE_NOT_JOINABLE);
        }

        if (roomEntity.getCurrentParticipantCount() >= roomEntity.getMaxParticipants()) {
            throw new BusinessException(ErrorCode.LIVEROOM_FULL);
        }

        Optional<LiveRoomParticipantJpaEntity> existing =
                participantJpaRepository.findActiveByRoomAndUser(roomCode, userId);

        LiveRoomParticipantJpaEntity participantEntity;
        boolean isNewJoin = false;

        if (existing.isPresent()) {
            participantEntity = existing.get();
            log.debug("Participant already joined (idempotent): userId={}, roomCode={}", userId, roomCode);
        } else {
            String effectiveDisplayName = resolveDisplayName(displayName, userId);
            LiveRoomParticipant newParticipant;
            try {
                newParticipant = LiveRoomParticipant.join(
                        roomCode,
                        userId,
                        effectiveDisplayName,
                        role,
                        Instant.now());
            } catch (IllegalArgumentException ex) {
                throw mapJoinFailure(ex);
            }
            participantEntity = participantJpaRepository.save(participantMapper.toEntity(newParticipant));
            isNewJoin = true;

            LiveRoom domain = liveRoomMapper.toDomain(roomEntity);
            try {
                domain.incrementParticipants();
            } catch (IllegalStateException ex) {
                throw new BusinessException(ErrorCode.LIVEROOM_FULL);
            }
            liveRoomMapper.toEntity(domain, roomEntity);
            liveRoomJpaRepository.save(roomEntity);
        }

        LiveRoomParticipant participant = participantMapper.toDomain(participantEntity);
        int currentCount = roomEntity.getCurrentParticipantCount();
        int maxParticipants = roomEntity.getMaxParticipants();
        int availableSlots = Math.max(0, maxParticipants - currentCount);

        if (isNewJoin) {
            eventPublisher.publishEvent(new ParticipantJoinedEvent(
                    roomCode,
                    roomEntity.getHostUserId(),
                    userId,
                    participant.getDisplayName(),
                    participant.getRoleAtJoin(),
                    currentCount,
                    maxParticipants,
                    availableSlots,
                    participant.getJoinedAt()));
        }

        log.info("Participant joined: userId={}, roomCode={}, participantId={}, new={}",
                userId, roomCode, participant.getId(), isNewJoin);

        return participant;
    }

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onParticipantJoined(ParticipantJoinedEvent event) {
        String destination = ROOM_TOPIC_BASE + event.roomCode() + PARTICIPANTS_TOPIC_SUFFIX;
        Object payload = Map.of(
                "type", "PARTICIPANT_JOINED",
                "roomCode", event.roomCode(),
                "userId", event.userId(),
                "displayName", event.displayName(),
                "roleAtJoin", event.roleAtJoin(),
                "currentCount", event.currentCount(),
                "maxParticipants", event.maxParticipants(),
                "availableSlots", event.availableSlots(),
                "timestamp", event.joinedAt().toString()
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Broadcast PARTICIPANT_JOINED: roomCode={}, userId={}",
                event.roomCode(), event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onParticipantLeft(ParticipantLeftEvent event) {
        String destination = ROOM_TOPIC_BASE + event.roomCode() + PARTICIPANTS_TOPIC_SUFFIX;
        Object payload = Map.of(
                "type", "PARTICIPANT_LEFT",
                "roomCode", event.roomCode(),
                "userId", event.userId(),
                "displayName", event.displayName(),
                "currentCount", event.currentCount(),
                "maxParticipants", event.maxParticipants(),
                "availableSlots", event.availableSlots(),
                "timestamp", event.leftAt().toString()
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Broadcast PARTICIPANT_LEFT: roomCode={}, userId={}",
                event.roomCode(), event.userId());
    }

    private String resolveDisplayName(String provided, UUID userId) {
        if (provided == null || provided.isBlank()) {
            return "listener-" + userId.toString().substring(0, 8);
        }
        return provided.trim();
    }

    private BusinessException mapJoinFailure(IllegalArgumentException ex) {
        String message = ex.getMessage();
        if (message != null && message.toLowerCase(Locale.ROOT).contains("display")) {
            return new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return new BusinessException(ErrorCode.INVALID_INPUT);
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
}