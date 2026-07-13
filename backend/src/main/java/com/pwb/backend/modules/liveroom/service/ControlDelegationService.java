package com.pwb.backend.modules.liveroom.service;

import java.util.List;
import java.util.UUID;

public interface ControlDelegationService {

    void delegateControl(String roomCode, UUID hostId, UUID listenerId, String action);

    void setGlobalDelegation(String roomCode, UUID hostId, boolean enabled);

    DelegationState getDelegationState(String roomCode);

    record DelegationState(boolean globalDelegation, List<UUID> delegatedUserIds) {
    }
}