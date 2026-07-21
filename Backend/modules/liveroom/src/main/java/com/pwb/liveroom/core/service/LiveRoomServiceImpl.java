package com.pwb.liveroom.core.service;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.liveroom.core.model.LiveRoom;
import com.pwb.liveroom.core.model.LiveRoomStatus;
import com.pwb.liveroom.infrastructure.config.LiveRoomProperties;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.mapper.LiveRoomMapper;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomJpaRepository;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomParticipantJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveRoomServiceImpl implements LiveRoomService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final LiveRoomJpaRepository liveRoomJpaRepository;
    private final LiveRoomParticipantJpaRepository participantJpaRepository;
    private final LiveRoomMapper liveRoomMapper;
    private final LiveRoomProperties liveRoomProperties;

    @Override
    @Transactional
    public LiveRoom createRoom(
            UUID hostUserId,
            String title,
            String description,
            Instant scheduledStartAt,
            Integer maxParticipants) {

        log.info("Creating live room: hostUserId={}, title={}", hostUserId, title);

        ensureHostHasNoActiveRoom(hostUserId);

        int capacity = resolveCapacity(maxParticipants);
        String roomCode = generateUniqueRoomCode();

        LiveRoom domain = LiveRoom.create(
                hostUserId,
                roomCode,
                title,
                description,
                scheduledStartAt,
                capacity
        );

        LiveRoomJpaEntity entity = liveRoomMapper.toEntity(domain);
        LiveRoomJpaEntity saved = liveRoomJpaRepository.save(entity);

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
    public LiveRoom getRoomByCode(UUID hostUserId, String roomCode) {
        LiveRoomJpaEntity entity = liveRoomJpaRepository
                .findByRoomCodeAndDeletedFalse(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND));
        return liveRoomMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public LiveRoom updateRoomSettings(
            UUID hostUserId,
            String roomCode,
            String title,
            String description,
            Integer maxParticipants) {

        log.info("Updating live room settings: hostUserId={}, roomCode={}", hostUserId, roomCode);

        LiveRoomJpaEntity entity = loadRoomAsHost(hostUserId, roomCode);
        if (entity.getStatus() == LiveRoomStatus.ENDED) {
            throw new BusinessException(ErrorCode.LIVEROOM_ALREADY_ENDED);
        }

        LiveRoom domain = liveRoomMapper.toDomain(entity);

        try {
            domain.updateSettings(title, description, maxParticipants);
        } catch (IllegalArgumentException ex) {
            throw mapValidationFailure(ex);
        } catch (IllegalStateException ex) {
            throw new BusinessException(ErrorCode.LIVEROOM_ALREADY_ENDED);
        }

        LiveRoomJpaEntity merged = liveRoomMapper.toEntity(domain, entity);
        LiveRoomJpaEntity saved = liveRoomJpaRepository.save(merged);

        log.info("Live room updated: hostUserId={}, roomCode={}", hostUserId, roomCode);

        return liveRoomMapper.toDomain(saved);
    }

    @Override
    @Transactional
    public void endRoom(UUID hostUserId, String roomCode) {
        log.info("Ending live room: hostUserId={}, roomCode={}", hostUserId, roomCode);

        LiveRoomJpaEntity entity = liveRoomJpaRepository
                .findByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND));

        if (!entity.getHostUserId().equals(hostUserId)) {
            throw new BusinessException(ErrorCode.LIVEROOM_NOT_HOST);
        }

        if (entity.getStatus() == LiveRoomStatus.ENDED) {
            throw new BusinessException(ErrorCode.LIVEROOM_ALREADY_ENDED);
        }

        LiveRoom domain = liveRoomMapper.toDomain(entity);
        Instant endedAt = Instant.now();
        domain.markEnded();

        LiveRoomJpaEntity merged = liveRoomMapper.toEntity(domain, entity);
        liveRoomJpaRepository.save(merged);

        int evicted = participantJpaRepository.markAllLeftByRoom(roomCode, endedAt);
        if (evicted > 0) {
            log.info("Evicted {} active participants on room end: roomCode={}", evicted, roomCode);
        }

        log.info("Live room ended: hostUserId={}, roomCode={}", hostUserId, roomCode);
    }

    @Override
    public boolean existsActiveRoomByCode(String roomCode) {
        return liveRoomJpaRepository.findByRoomCodeAndDeletedFalse(roomCode)
                .filter(r -> r.getStatus() == LiveRoomStatus.ACTIVE)
                .isPresent();
    }

    @Override
    public LiveRoom getRoomAsParticipant(String roomCode) {
        LiveRoomJpaEntity entity = liveRoomJpaRepository.findByRoomCodeAndDeletedFalse(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIVEROOM_NOT_FOUND));
        return liveRoomMapper.toDomain(entity);
    }

    @Override
    public LiveRoomJpaEntity loadRoomEntityAsHost(UUID hostUserId, String roomCode) {
        return loadRoomAsHost(hostUserId, roomCode);
    }

    private void ensureHostHasNoActiveRoom(UUID hostUserId) {
        if (liveRoomJpaRepository.existsByHostUserIdAndStatusAndDeletedFalse(
                hostUserId, LiveRoomStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.LIVEROOM_HOST_ALREADY_ACTIVE);
        }
    }

    private LiveRoomJpaEntity loadRoomAsHost(UUID hostUserId, String roomCode) {
        LiveRoomJpaEntity entity = liveRoomJpaRepository
                .findByRoomCodeAndDeletedFalse(roomCode)
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

    private BusinessException mapValidationFailure(IllegalArgumentException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("participants") || lower.contains("capacity")) {
            return new BusinessException(ErrorCode.LIVEROOM_INVALID_CAPACITY);
        }
        if (lower.contains("title")) {
            return new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return new BusinessException(ErrorCode.INVALID_INPUT);
    }
}
