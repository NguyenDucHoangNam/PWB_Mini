package com.pwb.liveroom.domain.enums;


public enum JoinRequestState {

    PENDING,


    APPROVED,

    REJECTED_BY_OWNER,

    REJECTED_BY_CAPACITY,

    CANCELLED,


    EXPIRED,


    LOCKED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}