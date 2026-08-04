package com.pwb.liveroom.application.command;

import com.pwb.liveroom.application.event.LiveroomEventType;

public enum RtcSignalType {

    OFFER(LiveroomEventType.RTC_OFFER),
    ANSWER(LiveroomEventType.RTC_ANSWER),
    ICE_CANDIDATE(LiveroomEventType.RTC_ICE_CANDIDATE);

    private final LiveroomEventType eventType;

    RtcSignalType(LiveroomEventType eventType) {
        this.eventType = eventType;
    }

    public LiveroomEventType eventType() {
        return eventType;
    }

    public boolean isDescription() {
        return this != ICE_CANDIDATE;
    }
}