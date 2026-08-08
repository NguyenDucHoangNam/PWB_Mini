package com.pwb.liveroom.api.dto.response;

import com.pwb.liveroom.application.view.JoinRequestView;
import com.pwb.liveroom.domain.enums.JoinRequestState;
import com.pwb.liveroom.domain.enums.RejectionReason;

import java.time.Instant;
import java.util.UUID;


public record JoinRequestResponse(
        UUID id,
        UUID roomId,
        UUID userId,
        String userEmail,
        String avatarUrl,
        JoinRequestState state,
        RejectionReason rejectionReason,
        Instant createdAt,
        Instant decidedAt,
        UUID decidedBy,
        int rejectCountByOwner,
        int attemptsRemaining
) {

    public static JoinRequestResponse from(JoinRequestView view) {
        return new JoinRequestResponse(
                view.id(),
                view.roomId(),
                view.userId(),
                view.userEmail(),
                view.avatarUrl(),
                view.state(),
                view.rejectionReason(),
                view.createdAt(),
                view.decidedAt(),
                view.decidedBy(),
                view.rejectCountByOwner(),
                view.attemptsRemaining()
        );
    }
}