package com.pwb.liveroom.core.model;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class LiveRoomParticipant {

    private static final int MIN_DISPLAY_NAME_LENGTH = 1;
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;

    private final UUID id;
    private final String roomCode;
    private final UUID userId;
    private final String displayName;
    private final String roleAtJoin;
    private final Instant joinedAt;
    private Instant leftAt;

    private LiveRoomParticipant(
            UUID id,
            String roomCode,
            UUID userId,
            String displayName,
            String roleAtJoin,
            Instant joinedAt,
            Instant leftAt
    ) {
        this.id = id;
        this.roomCode = roomCode;
        this.userId = userId;
        this.displayName = displayName;
        this.roleAtJoin = roleAtJoin;
        this.joinedAt = joinedAt;
        this.leftAt = leftAt;
    }

    public static LiveRoomParticipant join(
            String roomCode,
            UUID userId,
            String displayName,
            String roleAtJoin,
            Instant joinedAt
    ) {
        validateRoomCode(roomCode);
        validateUserId(userId);
        validateDisplayName(displayName);
        validateRole(roleAtJoin);

        return new LiveRoomParticipant(
                UUID.randomUUID(),
                roomCode,
                userId,
                displayName.trim(),
                roleAtJoin,
                joinedAt == null ? Instant.now() : joinedAt,
                null
        );
    }

    public static LiveRoomParticipant rehydrate(
            UUID id,
            String roomCode,
            UUID userId,
            String displayName,
            String roleAtJoin,
            Instant joinedAt,
            Instant leftAt
    ) {
        return new LiveRoomParticipant(
                id,
                roomCode,
                userId,
                displayName,
                roleAtJoin,
                joinedAt,
                leftAt
        );
    }

    public void markLeft(Instant leftAt) {
        if (this.leftAt != null) {
            return;
        }
        Instant now = leftAt == null ? Instant.now() : leftAt;
        if (now.isBefore(this.joinedAt)) {
            throw new IllegalArgumentException("Left time cannot be before joined time");
        }
        this.leftAt = now;
    }

    public boolean isActive() {
        return leftAt == null;
    }

    public boolean belongsTo(UUID userId) {
        return this.userId.equals(userId);
    }

    private static void validateRoomCode(String roomCode) {
        if (roomCode == null || roomCode.length() != 6) {
            throw new IllegalArgumentException("Room code must be exactly 6 characters");
        }
    }

    private static void validateUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
    }

    private static void validateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }
        int length = displayName.length();
        if (length < MIN_DISPLAY_NAME_LENGTH || length > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Display name must be between " + MIN_DISPLAY_NAME_LENGTH
                            + " and " + MAX_DISPLAY_NAME_LENGTH + " characters");
        }
    }

    private static void validateRole(String roleAtJoin) {
        if (roleAtJoin == null || roleAtJoin.isBlank()) {
            throw new IllegalArgumentException("Role at join is required");
        }
        if (!"USER".equals(roleAtJoin) && !"PRO".equals(roleAtJoin) && !"ADMIN".equals(roleAtJoin)) {
            throw new IllegalArgumentException(
                    "Role at join must be one of USER, PRO, ADMIN");
        }
    }
}