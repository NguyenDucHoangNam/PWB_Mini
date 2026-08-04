package com.pwb.liveroom.domain.enums;


public enum ParticipantState {

    ACTIVE,


    RECONNECTING,


    OFFLINE,


    LEFT,


    KICKED,


    ENDED;


    public boolean occupiesSlot() {
        return this == ACTIVE || this == RECONNECTING;
    }
}