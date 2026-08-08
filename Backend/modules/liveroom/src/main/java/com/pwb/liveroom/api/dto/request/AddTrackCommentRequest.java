package com.pwb.liveroom.api.dto.request;

import java.util.UUID;


public record AddTrackCommentRequest(
        UUID songId,
        Double positionSeconds,
        String content
) {
}