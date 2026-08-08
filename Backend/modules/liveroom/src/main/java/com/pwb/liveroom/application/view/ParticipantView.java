package com.pwb.liveroom.application.view;

import com.pwb.liveroom.domain.enums.MicState;
import com.pwb.liveroom.domain.enums.ParticipantRole;
import com.pwb.liveroom.domain.enums.ParticipantState;

import java.time.Instant;
import java.util.UUID;


public record ParticipantView(
        UUID id,
        UUID userId,
        String userEmail,
        String avatarUrl,
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
}