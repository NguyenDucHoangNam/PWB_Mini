package com.pwb.backend.modules.liveroom.dto.ws;

import java.util.List;
import java.util.UUID;

public record DelegationChangedMessage(
        String event,
        Data data) {

    public record Data(
            boolean globalDelegation,
            List<UUID> delegatedUserIds) {
    }
}