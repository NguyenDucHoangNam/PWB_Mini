package com.pwb.liveroom.core.model;

import com.pwb.liveroom.api.enums.LiveRoomMode;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class LiveRoom {

    private static final int MIN_PARTICIPANTS = 2;
    private static final int MAX_PARTICIPANTS = 500;

    private final UUID id;
    private final UUID hostUserId;
    private final String roomCode;
    private final Instant createdAt;
    private String title;
    private String description;
    private LiveRoomMode mode;
    private String passwordHash;
    private int maxParticipants;
    private LiveRoomStatus status;
    private int currentParticipantCount;
    private Instant scheduledStartAt;
    private Instant startedAt;
    private Instant endedAt;

    private LiveRoom(
            UUID id,
            UUID hostUserId,
            String roomCode,
            String title,
            String description,
            LiveRoomMode mode,
            String passwordHash,
            int maxParticipants,
            LiveRoomStatus status,
            int currentParticipantCount,
            Instant scheduledStartAt,
            Instant startedAt,
            Instant endedAt,
            Instant createdAt
    ) {
        this.id = id;
        this.hostUserId = hostUserId;
        this.roomCode = roomCode;
        this.title = title;
        this.description = description;
        this.mode = mode;
        this.passwordHash = passwordHash;
        this.maxParticipants = maxParticipants;
        this.status = status;
        this.currentParticipantCount = currentParticipantCount;
        this.scheduledStartAt = scheduledStartAt;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.createdAt = createdAt;
    }

    public static LiveRoom create(
            UUID hostUserId,
            String roomCode,
            String title,
            String description,
            LiveRoomMode mode,
            String passwordHash,
            int maxParticipants,
            Instant scheduledStartAt
    ) {
        validateTitle(title);
        validateMode(mode, passwordHash);
        validateCapacity(maxParticipants);
        validateRoomCode(roomCode);

        return new LiveRoom(
                UUID.randomUUID(),
                hostUserId,
                roomCode,
                title.trim(),
                description == null ? null : description.trim(),
                mode,
                passwordHash,
                maxParticipants,
                LiveRoomStatus.ACTIVE,
                0,
                scheduledStartAt,
                null,
                null,
                Instant.now()
        );
    }

    public static LiveRoom rehydrate(
            UUID id,
            UUID hostUserId,
            String roomCode,
            String title,
            String description,
            LiveRoomMode mode,
            String passwordHash,
            int maxParticipants,
            LiveRoomStatus status,
            int currentParticipantCount,
            Instant scheduledStartAt,
            Instant startedAt,
            Instant endedAt,
            Instant createdAt
    ) {
        return new LiveRoom(
                id,
                hostUserId,
                roomCode,
                title,
                description,
                mode,
                passwordHash,
                maxParticipants,
                status,
                currentParticipantCount,
                scheduledStartAt,
                startedAt,
                endedAt,
                createdAt
        );
    }

    public void updateSettings(
            String title,
            String description,
            LiveRoomMode mode,
            String passwordHash,
            Integer maxParticipants
    ) {
        ensureNotEnded();

        if (title != null && !title.isBlank()) {
            validateTitle(title);
            this.title = title.trim();
        }
        if (description != null) {
            this.description = description.isBlank() ? null : description.trim();
        }
        if (mode != null) {
            String effectivePassword = passwordHash != null ? passwordHash : this.passwordHash;
            validateMode(mode, effectivePassword);
            this.mode = mode;
            this.passwordHash = mode.requiresPassword() ? effectivePassword : null;
        } else if (passwordHash != null) {
            validateMode(this.mode, passwordHash);
            this.passwordHash = passwordHash;
        }
        if (maxParticipants != null) {
            validateCapacity(maxParticipants);
            if (maxParticipants < this.currentParticipantCount) {
                throw new IllegalArgumentException(
                        "Max participants cannot be lower than current participant count");
            }
            this.maxParticipants = maxParticipants;
        }
    }

    public void markStarted() {
        if (status != LiveRoomStatus.ACTIVE) {
            throw new IllegalStateException("Room is not active");
        }
        if (startedAt == null) {
            this.startedAt = Instant.now();
        }
    }

    public void markEnded() {
        ensureNotEnded();
        this.status = LiveRoomStatus.ENDED;
        this.endedAt = Instant.now();
        this.currentParticipantCount = 0;
    }

    public void incrementParticipants() {
        ensureNotEnded();
        ensureNotPaused();
        if (currentParticipantCount >= maxParticipants) {
            throw new IllegalStateException("Room is at maximum capacity");
        }
        this.currentParticipantCount++;
        if (startedAt == null) {
            this.startedAt = Instant.now();
        }
    }

    public void decrementParticipants() {
        if (currentParticipantCount > 0) {
            this.currentParticipantCount--;
        }
    }

    public boolean isHost(UUID userId) {
        return hostUserId.equals(userId);
    }

    public boolean hasPassword() {
        return mode.requiresPassword() && passwordHash != null;
    }

    private void ensureNotEnded() {
        if (status == LiveRoomStatus.ENDED) {
            throw new IllegalStateException("Room has already ended");
        }
    }

    private void ensureNotPaused() {
        if (status == LiveRoomStatus.PAUSED) {
            throw new IllegalStateException("Room is paused");
        }
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("Title must not exceed 200 characters");
        }
    }

    private static void validateMode(LiveRoomMode mode, String passwordHash) {
        if (mode == null) {
            throw new IllegalArgumentException("Room mode is required");
        }
        if (mode.requiresPassword() && (passwordHash == null || passwordHash.isBlank())) {
            throw new IllegalArgumentException("Password is required for PASSWORD mode");
        }
        if (!mode.requiresPassword() && passwordHash != null && !passwordHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Password can only be set when mode is PASSWORD");
        }
    }

    private static void validateCapacity(int maxParticipants) {
        if (maxParticipants < MIN_PARTICIPANTS || maxParticipants > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException(
                    "Max participants must be between " + MIN_PARTICIPANTS + " and " + MAX_PARTICIPANTS);
        }
    }

    private static void validateRoomCode(String roomCode) {
        if (roomCode == null || roomCode.length() != 6) {
            throw new IllegalArgumentException("Room code must be exactly 6 characters");
        }
        for (int i = 0; i < roomCode.length(); i++) {
            char c = roomCode.charAt(i);
            boolean isUpperAlpha = c >= 'A' && c <= 'Z';
            boolean isDigit = c >= '2' && c <= '9';
            if (!isUpperAlpha && !isDigit) {
                throw new IllegalArgumentException(
                        "Room code must contain only uppercase letters and digits (2-9)");
            }
        }
    }
}