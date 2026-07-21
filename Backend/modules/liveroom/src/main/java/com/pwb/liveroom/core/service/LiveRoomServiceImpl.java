package com.pwb.liveroom.core.service;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.liveroom.api.enums.LiveRoomMode;
import com.pwb.liveroom.core.model.LiveRoom;
import com.pwb.liveroom.core.model.LiveRoomStatus;
import com.pwb.liveroom.infrastructure.config.LiveRoomProperties;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.mapper.LiveRoomMapper;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private static final int PASSWORD_MIN_LENGTH = 4;
    private static final int PASSWORD_MAX_LENGTH = 64;

    private final LiveRoomJpaRepository liveRoomJpaRepository;
    private final LiveRoomMapper liveRoomMapper;
    private final LiveRoomProperties liveRoomProperties;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public LiveRoom createRoom(
            UUID hostUserId,
            String title,
            String description,
            LiveRoomMode mode,
            String rawPassword,
            Integer maxParticipants,
            Instant scheduledStartAt) {

        log.info("Creating live room: hostUserId={}, title={}, mode={}", hostUserId, title, mode);

        ensureHostHasNoActiveRoom(hostUserId);

        int capacity = resolveCapacity(maxParticipants);
        String passwordHash = encodePasswordIfRequired(mode, rawPassword);
        String roomCode = generateUniqueRoomCode();

        LiveRoom domain = LiveRoom.create(
                hostUserId,
                roomCode,
                title,
                description,
                mode,
                passwordHash,
                capacity,
                scheduledStartAt
        );

        LiveRoomJpaEntity entity = liveRoomMapper.toEntity(domain);
        LiveRoomJpaEntity saved = liveRoomJpaRepository.save(entity);

        log.info("Live room created: hostUserId={}, roomCode={}, roomId={}",
                hostUserId, saved.getRoomCode(), saved.getId());

        return liveRoomMapper.toDomain(saved);
    }

    @Override
    public Page<LiveRoom> listMyRooms(UUID hostUserId, LiveRoomStatus status, Pageable pageable) {
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
            LiveRoomMode mode,
            String rawPassword,
            Integer maxParticipants) {

        log.info("Updating live room settings: hostUserId={}, roomCode={}", hostUserId, roomCode);

        LiveRoomJpaEntity entity = loadRoomAsHost(hostUserId, roomCode);
        if (entity.getStatus() == LiveRoomStatus.ENDED) {
            throw new BusinessException(ErrorCode.LIVEROOM_ALREADY_ENDED);
        }

        LiveRoom domain = liveRoomMapper.toDomain(entity);
        String encodedPassword = encodePasswordIfRequired(
                mode != null ? mode : domain.getMode(),
                rawPassword);

        try {
            domain.updateSettings(title, description, mode, encodedPassword, maxParticipants);
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
        domain.markEnded();

        LiveRoomJpaEntity merged = liveRoomMapper.toEntity(domain, entity);
        liveRoomJpaRepository.save(merged);

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

    private String encodePasswordIfRequired(LiveRoomMode mode, String rawPassword) {
        if (mode == null || !mode.requiresPassword()) {
            if (rawPassword != null && !rawPassword.isBlank()) {
                throw new BusinessException(ErrorCode.LIVEROOM_INVALID_MODE);
            }
            return null;
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BusinessException(ErrorCode.LIVEROOM_PASSWORD_REQUIRED);
        }
        if (rawPassword.length() < PASSWORD_MIN_LENGTH || rawPassword.length() > PASSWORD_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.LIVEROOM_INVALID_MODE,
                    PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH);
        }
        return passwordEncoder.encode(rawPassword);
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
        if (lower.contains("password")) {
            return new BusinessException(ErrorCode.LIVEROOM_PASSWORD_REQUIRED);
        }
        if (lower.contains("participants") || lower.contains("capacity")) {
            return new BusinessException(ErrorCode.LIVEROOM_INVALID_CAPACITY);
        }
        if (lower.contains("title")) {
            return new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return new BusinessException(ErrorCode.INVALID_INPUT);
    }
}