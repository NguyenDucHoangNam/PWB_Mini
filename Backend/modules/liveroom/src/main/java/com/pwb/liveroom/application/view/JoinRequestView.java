package com.pwb.liveroom.application.view;

import com.pwb.liveroom.domain.enums.JoinRequestState;
import com.pwb.liveroom.domain.enums.RejectionReason;

import java.time.Instant;
import java.util.UUID;


public record JoinRequestView(
        UUID id,
        UUID roomId,
        UUID userId,
        String userEmail,
        JoinRequestState state,
        RejectionReason rejectionReason,
        Instant createdAt,
        Instant decidedAt,
        UUID decidedBy,
        int rejectCountByOwner,
        int attemptsRemaining
) {
}