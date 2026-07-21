package com.pwb.liveroom.core.model;

public enum JoinRequestStatus {

    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    public boolean isTerminal() {
        return this != PENDING;
    }

    public boolean isPending() {
        return this == PENDING;
    }
}
