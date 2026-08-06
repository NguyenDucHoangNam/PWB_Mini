package com.pwb.liveroom.domain.repository;

import com.pwb.liveroom.domain.enums.RoomStatus;

import java.util.UUID;

public record RoomSearchCriteria(
        UUID ownerId,
        String keyword,
        RoomStatus status
) {

    public RoomSearchCriteria {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        keyword = (keyword == null) ? null : keyword.trim();
    }

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }
}