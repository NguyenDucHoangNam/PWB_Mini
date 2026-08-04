package com.pwb.liveroom.api.dto.request;

import jakarta.validation.constraints.Size;


public record ModerateParticipantRequest(
        @Size(max = 512) String reason
) {
}