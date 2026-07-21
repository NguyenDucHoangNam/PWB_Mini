package com.pwb.liveroom.core.model;

public enum LiveRoomStatus {

    ACTIVE,
    PAUSED,
    ENDED;

    public boolean isTerminal() {
        return this == ENDED;
    }

    public boolean isJoinable() {
        return this == ACTIVE;
    }
}