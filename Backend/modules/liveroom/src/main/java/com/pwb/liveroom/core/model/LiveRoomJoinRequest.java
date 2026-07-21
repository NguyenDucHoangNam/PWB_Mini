package com.pwb.liveroom.core.model;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class LiveRoomJoinRequest {

    private static final int MIN_DISPLAY_NAME_LENGTH = 1;
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_REASON_LENGTH = 500;

    private final UUID id;
    private final String roomCode;
    private final UUID userId;
    private final String displayName;
    private final String message;
    private final Instant createdAt;
    private JoinRequestStatus status;
    private String decisionReason;
    private UUID decidedByUserId;
    private Instant decidedAt;

    private LiveRoomJoinRequest(
            UUID id,
            String roomCode,
            UUID userId,
            String displayName,
            String message,
            JoinRequestStatus status,
            String decisionReason,
            UUID decidedByUserId,
            Instant decidedAt,
            Instant createdAt
    ) {
        this.id = id;
        this.roomCode = roomCode;
        this.userId = userId;
        this.displayName = displayName;
        this.message = message;
        this.status = status;
        this.decisionReason = decisionReason;
        this.decidedByUserId = decidedByUserId;
        this.decidedAt = decidedAt;
        this.createdAt = createdAt;
    }

    public static LiveRoomJoinRequest create(
            String roomCode,
            UUID userId,
            String displayName,
            String message
    ) {
        validateRoomCode(roomCode);
        validateUserId(userId);
        validateDisplayName(displayName);
        validateMessage(message);

        return new LiveRoomJoinRequest(
                UUID.randomUUID(),
                roomCode,
                userId,
                displayName.trim(),
                message == null || message.isBlank() ? null : message.trim(),
                JoinRequestStatus.PENDING,
                null,
                null,
                null,
                Instant.now()
        );
    }

    public static LiveRoomJoinRequest rehydrate(
            UUID id,
            String roomCode,
            UUID userId,
            String displayName,
            String message,
            JoinRequestStatus status,
            String decisionReason,
            UUID decidedByUserId,
            Instant decidedAt,
            Instant createdAt
    ) {
        return new LiveRoomJoinRequest(
                id,
                roomCode,
                userId,
                displayName,
                message,
                status,
                decisionReason,
                decidedByUserId,
                decidedAt,
                createdAt
        );
    }

    public void approve(UUID decidedBy, String reason) {
        ensurePending();
        validateDecidedBy(decidedBy);
        validateReason(reason);
        this.status = JoinRequestStatus.APPROVED;
        this.decidedByUserId = decidedBy;
        this.decisionReason = reason == null || reason.isBlank() ? null : reason.trim();
        this.decidedAt = Instant.now();
    }

    public void reject(UUID decidedBy, String reason) {
        ensurePending();
        validateDecidedBy(decidedBy);
        validateReason(reason);
        this.status = JoinRequestStatus.REJECTED;
        this.decidedByUserId = decidedBy;
        this.decisionReason = reason == null || reason.isBlank() ? null : reason.trim();
        this.decidedAt = Instant.now();
    }

    public void cancel(UUID ownerId) {
        ensurePending();
        if (!this.userId.equals(ownerId)) {
            throw new IllegalStateException("Only the request owner can cancel this request");
        }
        this.status = JoinRequestStatus.CANCELLED;
        this.decidedAt = Instant.now();
    }

    public boolean isOwnedBy(UUID candidate) {
        return this.userId.equals(candidate);
    }

    private void ensurePending() {
        if (status != JoinRequestStatus.PENDING) {
            throw new IllegalStateException("Join request is no longer pending");
        }
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

    private static void validateMessage(String message) {
        if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException(
                    "Request message must not exceed " + MAX_MESSAGE_LENGTH + " characters");
        }
    }

    private static void validateReason(String reason) {
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "Decision reason must not exceed " + MAX_REASON_LENGTH + " characters");
        }
    }

    private static void validateDecidedBy(UUID decidedBy) {
        if (decidedBy == null) {
            throw new IllegalArgumentException("Decided by user is required");
        }
    }
}
