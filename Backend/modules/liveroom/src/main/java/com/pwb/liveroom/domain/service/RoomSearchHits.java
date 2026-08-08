package com.pwb.liveroom.domain.service;

import java.util.List;
import java.util.UUID;

public record RoomSearchHits(List<UUID> ids, long total) {
}