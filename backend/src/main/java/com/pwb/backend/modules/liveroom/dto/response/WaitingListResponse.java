package com.pwb.backend.modules.liveroom.dto.response;

import java.util.List;

public record WaitingListResponse(
        List<WaitingMemberResponse> members,
        int totalCount,
        int activeParticipants,
        int maxParticipants) {
}