package com.pwb.backend.modules.liveroom.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ApproveRejectRequest(
        @NotNull(message = "{validation.listenerId.required}")
        UUID listenerId) {
}