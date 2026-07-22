package com.pwb.liveroom.api.enums;

public enum LiveRoomMode {

    PUBLIC,
    PRIVATE;

    public boolean requiresApproval() {
        return this == PRIVATE;
    }

    public boolean isPubliclyDiscoverable() {
        return this == PUBLIC;
    }
}
