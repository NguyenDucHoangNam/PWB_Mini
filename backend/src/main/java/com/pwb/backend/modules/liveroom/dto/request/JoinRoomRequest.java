package com.pwb.backend.modules.liveroom.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinRoomRequest(
        @NotBlank(message = "{validation.displayName.required}")
        @Size(max = 30, message = "{validation.displayName.length}")
        String displayName) {
}