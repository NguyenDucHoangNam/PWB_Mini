package com.pwb.liveroom.domain.service;

import com.pwb.liveroom.domain.repository.RoomSearchCriteria;

import java.util.Optional;

public interface RoomSearchPort {

    Optional<RoomSearchHits> search(RoomSearchCriteria criteria, int from, int size);
}