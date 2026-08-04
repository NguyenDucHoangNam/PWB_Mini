package com.pwb.liveroom.api.dto.request;


public record UpdateMediaStateRequest(
        Boolean cameraOn,
        Boolean micOn
) {
}