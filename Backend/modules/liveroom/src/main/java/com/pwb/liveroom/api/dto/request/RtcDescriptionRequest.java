package com.pwb.liveroom.api.dto.request;

import java.util.UUID;

public record RtcDescriptionRequest(UUID targetUserId, String sdp) {
}