package com.pwb.liveroom.domain.model;

import com.pwb.liveroom.domain.enums.JoinRequestState;
import com.pwb.liveroom.domain.enums.RejectionReason;
import com.pwb.shared.domain.DomainBaseEntity;

import java.time.Instant;
import java.util.UUID;


public final class JoinRequest extends DomainBaseEntity {

    private final UUID id;
    private final UUID roomId;
    private final UUID cycleId;
    private final UUID userId;
    private final String userEmail;
    private final String idempotencyKey;
    private JoinRequestState state;
    private RejectionReason rejectionReason;
    private Instant decidedAt;
    private UUID decidedBy;

    private JoinRequest(
            UUID id,
            UUID roomId,
            UUID cycleId,
            UUID userId,
            String userEmail,
            String idempotencyKey,
            JoinRequestState state,
            RejectionReason rejectionReason,
            Instant decidedAt,
            UUID decidedBy
    ) {
        this.id = id;
        this.roomId = roomId;
        this.cycleId = cycleId;
        this.userId = userId;
        this.userEmail = userEmail;
        this.idempotencyKey = idempotencyKey;
        this.state = state;
        this.rejectionReason = rejectionReason;
        this.decidedAt = decidedAt;
        this.decidedBy = decidedBy;
    }

    public static JoinRequest raise(
            UUID roomId,
            UUID cycleId,
            UUID userId,
            String userEmail,
            String idempotencyKey
    ) {
        if (roomId == null || cycleId == null || userId == null) {
            throw new IllegalArgumentException("roomId, cycleId and userId must not be null");
        }
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("userEmail must not be blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        return new JoinRequest(null, roomId, cycleId, userId, userEmail, idempotencyKey,
                JoinRequestState.PENDING, null, null, null);
    }

    public static JoinRequest rehydrate(
            UUID id,
            UUID roomId,
            UUID cycleId,
            UUID userId,
            String userEmail,
            String idempotencyKey,
            JoinRequestState state,
            RejectionReason rejectionReason,
            Instant decidedAt,
            UUID decidedBy
    ) {
        return new JoinRequest(id, roomId, cycleId, userId, userEmail, idempotencyKey,
                state, rejectionReason, decidedAt, decidedBy);
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getCycleId() {
        return cycleId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public JoinRequestState getState() {
        return state;
    }

    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public boolean isNew() {
        return id == null;
    }

    public boolean isPending() {
        return state == JoinRequestState.PENDING;
    }

    public void approve(UUID ownerId, Instant at) {
        settle(JoinRequestState.APPROVED, null, ownerId, at);
    }

    public void rejectByOwner(UUID ownerId, Instant at) {
        settle(JoinRequestState.REJECTED_BY_OWNER, RejectionReason.OWNER_REJECT, ownerId, at);
    }


    public void rejectByCapacity(UUID ownerId, Instant at) {
        settle(JoinRequestState.REJECTED_BY_CAPACITY, RejectionReason.CAPACITY_FULL, ownerId, at);
    }

    public void cancel(Instant at) {
        settle(JoinRequestState.CANCELLED, RejectionReason.USER_CANCELLED, userId, at);
    }

    public void expire(Instant at) {
        settle(JoinRequestState.EXPIRED, RejectionReason.ROOM_ENDED, null, at);
    }

    private void settle(JoinRequestState newState, RejectionReason reason, UUID actorId, Instant at) {
        if (state.isTerminal()) {
            throw new JoinRequestStateException("Join request has already been settled as " + state);
        }
        this.state = newState;
        this.rejectionReason = reason;
        this.decidedBy = actorId;
        this.decidedAt = at;
        touch();
    }

    public static class JoinRequestStateException extends RuntimeException {
        public JoinRequestStateException(String message) {
            super(message);
        }
    }
}