package com.pwb.backend.modules.liveroom.dto.request;

import com.pwb.backend.modules.liveroom.enums.RoomMode;
import jakarta.validation.constraints.NotNull;

public record CreateRoomRequest(
        @NotNull(message = "{validation.roomMode.required}")
        RoomMode mode) {
}