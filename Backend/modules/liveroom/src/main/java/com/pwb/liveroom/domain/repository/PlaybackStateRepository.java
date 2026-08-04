package com.pwb.liveroom.domain.repository;

import com.pwb.liveroom.domain.model.PlaybackState;

import java.util.Optional;
import java.util.UUID;

public interface PlaybackStateRepository {

    Optional<PlaybackState> findByRoomId(UUID roomId);

    PlaybackState save(PlaybackState state);


    void deleteByRoomId(UUID roomId);
}