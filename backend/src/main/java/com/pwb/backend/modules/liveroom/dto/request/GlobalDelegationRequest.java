package com.pwb.backend.modules.liveroom.dto.request;

import jakarta.validation.constraints.NotNull;

public record GlobalDelegationRequest(
        @NotNull Boolean enabled) {
}