package com.pwb.liveroom.domain.service;

import com.pwb.liveroom.domain.model.TrackComment;

import java.util.List;
import java.util.UUID;


public interface TrackCommentStore {

    TrackComment add(TrackComment comment);

    List<TrackComment> findBySong(UUID cycleId, UUID songId);

    void clearCycle(UUID cycleId);
}