package com.pwb.liveroom.api.dto.response;

import com.pwb.liveroom.application.view.ParticipantView;
import com.pwb.liveroom.domain.enums.MicState;
import com.pwb.liveroom.domain.enums.ParticipantRole;
import com.pwb.liveroom.domain.enums.ParticipantState;

import java.time.Instant;
import java.util.UUID;

public record ParticipantResponse(
        UUID id,
        UUID userId,
        String userEmail,
        ParticipantRole roomRole,
        ParticipantState state,
        Instant joinedAt,
        Instant leftAt,
        boolean cameraOn,
        boolean micOn,
        MicState micState,
        Instant micUnmuteCooldownUntil,
        Instant lastInteractionAt,
        boolean oldest,
        boolean newest
) {

    public static ParticipantResponse from(ParticipantView view) {
        return new ParticipantResponse(
                view.id(),
                view.userId(),
                view.userEmail(),
                view.roomRole(),
                view.state(),
                view.joinedAt(),
                view.leftAt(),
                view.cameraOn(),
                view.micOn(),
                view.micState(),
                view.micUnmuteCooldownUntil(),
                view.lastInteractionAt(),
                view.oldest(),
                view.newest()
        );
    }
}