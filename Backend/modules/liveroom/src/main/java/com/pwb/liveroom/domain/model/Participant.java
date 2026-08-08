package com.pwb.liveroom.domain.model;

import com.pwb.liveroom.domain.enums.MicState;
import com.pwb.liveroom.domain.enums.ParticipantRole;
import com.pwb.liveroom.domain.enums.ParticipantState;
import com.pwb.shared.domain.DomainBaseEntity;

import java.time.Instant;
import java.util.UUID;


public final class Participant extends DomainBaseEntity {

    private final UUID id;
    private final UUID roomId;
    private final UUID cycleId;
    private final UUID userId;
    private final String userEmail;
    private final ParticipantRole roomRole;
    private ParticipantState state;
    private Instant joinedAt;
    private Instant leftAt;
    private boolean cameraOn;
    private boolean micOn;
    private MicState micState;
    private Instant micMutedByOwnerAt;
    private Instant micUnmuteCooldownUntil;
    private Instant lastInteractionAt;

    private Participant(
            UUID id,
            UUID roomId,
            UUID cycleId,
            UUID userId,
            String userEmail,
            ParticipantRole roomRole,
            ParticipantState state,
            Instant joinedAt,
            Instant leftAt,
            boolean cameraOn,
            boolean micOn,
            MicState micState,
            Instant micMutedByOwnerAt,
            Instant micUnmuteCooldownUntil,
            Instant lastInteractionAt
    ) {
        this.id = id;
        this.roomId = roomId;
        this.cycleId = cycleId;
        this.userId = userId;
        this.userEmail = userEmail;
        this.roomRole = roomRole;
        this.state = state;
        this.joinedAt = joinedAt;
        this.leftAt = leftAt;
        this.cameraOn = cameraOn;
        this.micOn = micOn;
        this.micState = micState;
        this.micMutedByOwnerAt = micMutedByOwnerAt;
        this.micUnmuteCooldownUntil = micUnmuteCooldownUntil;
        this.lastInteractionAt = lastInteractionAt;
    }

    public static Participant join(
            UUID roomId,
            UUID cycleId,
            UUID userId,
            String userEmail,
            ParticipantRole roomRole,
            Instant joinedAt
    ) {
        if (roomId == null || cycleId == null || userId == null) {
            throw new IllegalArgumentException("roomId, cycleId and userId must not be null");
        }
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("userEmail must not be blank");
        }
        return new Participant(
                null,
                roomId,
                cycleId,
                userId,
                userEmail,
                roomRole,
                ParticipantState.ACTIVE,
                joinedAt,
                null,
                false,
                false,
                MicState.SELF_MUTED,
                null,
                null,
                joinedAt
        );
    }

    public static Participant rehydrate(
            UUID id,
            UUID roomId,
            UUID cycleId,
            UUID userId,
            String userEmail,
            ParticipantRole roomRole,
            ParticipantState state,
            Instant joinedAt,
            Instant leftAt,
            boolean cameraOn,
            boolean micOn,
            MicState micState,
            Instant micMutedByOwnerAt,
            Instant micUnmuteCooldownUntil,
            Instant lastInteractionAt
    ) {
        return new Participant(id, roomId, cycleId, userId, userEmail, roomRole, state,
                joinedAt, leftAt, cameraOn, micOn, micState,
                micMutedByOwnerAt, micUnmuteCooldownUntil, lastInteractionAt);
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

    public ParticipantRole getRoomRole() {
        return roomRole;
    }

    public ParticipantState getState() {
        return state;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getLeftAt() {
        return leftAt;
    }

    public boolean isCameraOn() {
        return cameraOn;
    }

    public boolean isMicOn() {
        return micOn;
    }

    public MicState getMicState() {
        return micState;
    }

    public Instant getMicMutedByOwnerAt() {
        return micMutedByOwnerAt;
    }

    public Instant getMicUnmuteCooldownUntil() {
        return micUnmuteCooldownUntil;
    }

    public Instant getLastInteractionAt() {
        return lastInteractionAt;
    }

    public boolean isNew() {
        return id == null;
    }

    public boolean isOwner() {
        return roomRole == ParticipantRole.OWNER;
    }

    public boolean isInRoom() {
        return state.occupiesSlot();
    }


    public void rejoin(Instant at) {
        this.state = ParticipantState.ACTIVE;
        this.joinedAt = at;
        this.leftAt = null;
        this.cameraOn = false;
        this.micOn = false;
        this.micState = isMicUnmuteBlocked(at) ? MicState.MUTED_BY_OWNER : MicState.SELF_MUTED;
        this.lastInteractionAt = at;
        touch();
    }


    public boolean isMicUnmuteBlocked(Instant now) {
        return micUnmuteCooldownUntil != null && now.isBefore(micUnmuteCooldownUntil);
    }


    public void applyMediaState(boolean cameraOn, boolean micOn, Instant now) {
        if (micOn && isMicUnmuteBlocked(now)) {
            throw new MediaStateException("Microphone cannot be unmuted yet");
        }

        this.cameraOn = cameraOn;
        this.micOn = micOn;
        if (micOn) {
            this.micState = MicState.UNMUTED;
            this.micMutedByOwnerAt = null;
            this.micUnmuteCooldownUntil = null;
        } else if (!isMicUnmuteBlocked(now)) {
            this.micState = MicState.SELF_MUTED;
        }


        this.lastInteractionAt = now;
        touch();
    }


    public void muteByOwner(Instant at, Instant cooldownUntil) {
        this.micOn = false;
        this.micState = MicState.MUTED_BY_OWNER;
        this.micMutedByOwnerAt = at;
        this.micUnmuteCooldownUntil = cooldownUntil;
        touch();
    }


    public void markInteraction(Instant at) {
        this.lastInteractionAt = at;
        touch();
    }

    public void leave(Instant at) {
        markGone(ParticipantState.LEFT, at);
    }

    public void kick(Instant at) {
        markGone(ParticipantState.KICKED, at);
    }


    public void closeWithRoom(Instant at) {
        markGone(ParticipantState.ENDED, at);
    }


    public void restoreAfterRoomRevived() {
        this.state = ParticipantState.ACTIVE;
        this.leftAt = null;
        touch();
    }

    private void markGone(ParticipantState newState, Instant at) {
        this.state = newState;
        this.leftAt = at;
        this.cameraOn = false;
        this.micOn = false;


        this.micState = MicState.SELF_MUTED;
        touch();
    }

    public static class MediaStateException extends RuntimeException {
        public MediaStateException(String message) {
            super(message);
        }
    }
}