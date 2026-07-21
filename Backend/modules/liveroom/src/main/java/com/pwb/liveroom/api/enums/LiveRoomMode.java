package com.pwb.liveroom.api.enums;

public enum LiveRoomMode {

    PUBLIC,
    PRIVATE,
    INVITE_ONLY,
    PASSWORD;

    public boolean requiresPassword() {
        return this == PASSWORD;
    }

    public boolean isPubliclyDiscoverable() {
        return this == PUBLIC;
    }
}