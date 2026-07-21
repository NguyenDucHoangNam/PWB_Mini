package com.pwb.liveroom.api.enums;

public enum LiveRoomMode {

    PUBLIC;

    public boolean requiresPassword() {
        return false;
    }

    public boolean isPubliclyDiscoverable() {
        return this == PUBLIC;
    }
}
