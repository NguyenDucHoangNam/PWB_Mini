package com.pwb.liveroom.core.service;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.liveroom.api.enums.LiveRoomMode;
import com.pwb.liveroom.core.model.LiveRoom;
import com.pwb.liveroom.core.model.LiveroomDomainException;
import com.pwb.liveroom.core.model.LiveRoomParticipant;
import com.pwb.liveroom.core.model.LiveRoomStatus;
import com.pwb.liveroom.infrastructure.config.LiveRoomProperties;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.mapper.LiveRoomMapper;
import com.pwb.liveroom.infrastructure.persistence.mapper.LiveRoomParticipantMapper;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomJpaRepository;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomParticipantJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static java.util.Map.entry;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveRoomServiceImpl implements LiveRoomService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HOST_DISPLAY_NAME_FALLBACK = "Host";

    private final LiveRoomJpaRepository liveRoomJpaRepository;
    private final LiveRoomParticipantJpaRepository participantJpaRepository;
    private final LiveRoomMapper liveRoomMapper;
    private final LiveRoomParticipantMapper participantMapper;
    private final LiveRoomProperties liveRoomProperties;
    private final LiveRoomRealtimeBroadcaster broadcaster;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public LiveRoom createRoom(
            UUID hostUserId,
            String hostDisplayName,
            String title,
            String description,
            LiveRoomMode mode,
            Integer maxParticipants) {

        log.info("Creating live room: hostUserId={}, title={}, mode={}", hostUserId, title, mode);

        ensureHostHasNoActiveRoom(hostUserId);

        int capacity = resolveCapacity(maxParticipants);
        String roomCode = generateUniqueRoomCode();

        LiveRoom domain;
        try {
            domain = LiveRoom.create(
                    hostUserId,
                    roomCode,
                    title,
                    description,
                    mode,
                    capacity
            );
        } catch (LiveroomDomainException ex) {
            throw mapDomainException(ex);
        }

        LiveRoomJpaEntity entity = liveRoomMapper.toEntity(domain);
        LiveRoomJpaEntity saved = liveRoomJpaRepository.save(entity);

        String resolvedHostDisplayName = (hostDisplayName == null || hostDisplayName.isBlank())
                ? HOST_DISPLAY_NAME_FALLBACK
                : hostDisplayName.trim();
        if (resolvedHostDisplayName.equals(HOST_DISPLAY_NAME_FALLBACK)) {
            log.warn("Host display name missing, fallback applied: hostUserId={}, roomCode={}",
                    hostUserId, saved.getRoomCode());
        }

        Instant now = Instant.now();
        LiveRoomParticipant hostParticipant = LiveRoomParticipant.join(
                roomCode, hostUserId, resolvedHostDisplayName, "PRO", now);
        participantJpaRepository.save(participantMapper.toEntity(hostParticipant));

        domain.incrementParticipants();
        liveRoomMapper.toEntity(domain, saved);
        liveRoomJpaRepository.save(saved);

        log.info("Live room created: hostUserId={}, roomCode={}, roomId={}",
                hostUserId, saved.getRoomCode(), saved.getId());

        return liveRoomMapper.toDomain(saved);
    }

    @Override
    public Page<LiveRoom> listMyRooms(UUID hostUserId, LiveRoomStatus status, Pageable pageable) {
        log.debug("Listing my rooms: hostUserId={}, status={}", hostUserId, status);
        if (status == null) {
            return liveRoomJpaRepository
                    .findByHostUserIdAndDeletedFalse(hostUserId, pageable)
                    .map(liveRoomMapper::toDomain);
        }
        return liveRoomJpaRepository
                .findByHostUserIdAndStatusAndDeletedFalse(hostUserId, status, pageable)
                .map(liveRoomMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public LiveRoom getRoomAsHost(UUID hostUserId, String roomCode) {
        LiveRoomJpaEntity entity = liveRoomJpaRepository
                .findByRoomCodeAndDeletedFalse(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND));
        if (!entity.getHostUserId().equals(hostUserId)) {
            throw new BusinessException(ErrorCode.LIVEROOM_NOT_HOST);
        }
        return liveRoomMapper.toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public LiveRoom getRoomPublicInfo(String roomCode) {
        LiveRoomJpaEntity entity = liveRoomJpaRepository
                .findByRoomCodeAndDeletedFalse(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND));
        return liveRoomMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void endRoom(UUID hostUserId, String roomCode) {
        log.info("Ending live room: hostUserId={}, roomCode={}", hostUserId, roomCode);

        LiveRoomJpaEntity entity = loadActiveRoomForHostForUpdate(roomCode, hostUserId);

        if (entity.getStatus() == LiveRoomStatus.ENDED) {
            throw new BusinessException(ErrorCode.LIVEROOM_ALREADY_ENDED);
        }

        LiveRoom domain = liveRoomMapper.toDomain(entity);
        Instant endedAt = Instant.now();
        try {
            domain.markEnded();
        } catch (LiveroomDomainException ex) {
            throw mapDomainException(ex);
        }

        LiveRoomJpaEntity merged = liveRoomMapper.toEntity(domain, entity);
        liveRoomJpaRepository.save(merged);

        int evicted = participantJpaRepository.markAllLeftByRoom(roomCode, endedAt);
        if (evicted > 0) {
            log.info("Evicted {} active participants on room end: roomCode={}", evicted, roomCode);
        }

        eventPublisher.publishEvent(new RoomEndedEvent(roomCode, hostUserId, endedAt));

        log.info("Live room ended: hostUserId={}, roomCode={}", hostUserId, roomCode);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomEnded(RoomEndedEvent event) {
        broadcaster.broadcastRoomEnded(event.roomCode(), event.hostUserId(), event.endedAt().toString());
    }

    public record RoomEndedEvent(String roomCode, UUID hostUserId, Instant endedAt) {
    }

    @Override
    public boolean existsActiveRoomByCode(String roomCode) {
        return liveRoomJpaRepository.findByRoomCodeAndDeletedFalse(roomCode)
                .filter(r -> r.getStatus() == LiveRoomStatus.ACTIVE)
                .isPresent();
    }

    private void ensureHostHasNoActiveRoom(UUID hostUserId) {
        if (liveRoomJpaRepository.existsByHostUserIdAndStatusAndDeletedFalse(
                hostUserId, LiveRoomStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.LIVEROOM_HOST_ALREADY_ACTIVE);
        }
    }

    private LiveRoomJpaEntity loadActiveRoomForHost(String roomCode, UUID hostUserId) {
        LiveRoomJpaEntity entity = liveRoomJpaRepository
                .findByRoomCodeAndDeletedFalse(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND));
        if (!entity.getHostUserId().equals(hostUserId)) {
            throw new BusinessException(ErrorCode.LIVEROOM_NOT_HOST);
        }
        return entity;
    }

    private LiveRoomJpaEntity loadActiveRoomForHostForUpdate(String roomCode, UUID hostUserId) {
        LiveRoomJpaEntity entity = liveRoomJpaRepository
                .findByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND));
        if (!entity.getHostUserId().equals(hostUserId)) {
            throw new BusinessException(ErrorCode.LIVEROOM_NOT_HOST);
        }
        return entity;
    }

    private int resolveCapacity(Integer requested) {
        int absoluteMax = liveRoomProperties.getCapacity().getAbsoluteMaxParticipants();
        int defaultMax = liveRoomProperties.getCapacity().getDefaultMaxParticipants();
        if (requested == null) {
            return defaultMax;
        }
        if (requested < 2 || requested > absoluteMax) {
            throw new BusinessException(ErrorCode.LIVEROOM_INVALID_CAPACITY);
        }
        return requested;
    }

    private String generateUniqueRoomCode() {
        LiveRoomProperties.Code codeConfig = liveRoomProperties.getCode();
        String charset = codeConfig.getCharset();
        int length = codeConfig.getLength();
        int maxAttempts = codeConfig.getMaxGenerationAttempts();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String candidate = generateCode(charset, length);
            if (!liveRoomJpaRepository.existsByRoomCodeAndDeletedFalse(candidate)) {
                return candidate;
            }
            log.warn("Room code collision on attempt {}: code={}", attempt, candidate);
        }

        log.error("Failed to generate unique room code after {} attempts", maxAttempts);
        throw new BusinessException(ErrorCode.LIVEROOM_CODE_GENERATION_FAILED);
    }

    private String generateCode(String charset, int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(charset.charAt(RANDOM.nextInt(charset.length())));
        }
        return builder.toString();
    }

    private static final Map<String, ErrorCode> DOMAIN_ERROR_CODE_MAP = Map.ofEntries(
            entry("LIVEROOM_TITLE_REQUIRED", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_TITLE_TOO_LONG", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_CAPACITY_INVALID", ErrorCode.LIVEROOM_INVALID_CAPACITY),
            entry("LIVEROOM_CODE_INVALID_LENGTH", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_CODE_INVALID_CHARS", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_FULL", ErrorCode.LIVEROOM_FULL),
            entry("LIVEROOM_NOT_ACTIVE", ErrorCode.LIVEROOM_ALREADY_ENDED),
            entry("LIVEROOM_PAUSED", ErrorCode.LIVEROOM_ALREADY_ENDED),
            entry("LIVEROOM_ALREADY_ENDED", ErrorCode.LIVEROOM_ALREADY_ENDED),
            entry("LIVEROOM_CAPACITY_LOWER_THAN_CURRENT", ErrorCode.LIVEROOM_INVALID_CAPACITY)
    );

    private BusinessException mapDomainException(LiveroomDomainException ex) {
        ErrorCode ec = DOMAIN_ERROR_CODE_MAP.getOrDefault(ex.getErrorKey(), ErrorCode.INVALID_INPUT);
        return new BusinessException(ec);
    }
}
