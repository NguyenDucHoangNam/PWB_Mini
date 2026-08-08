package com.pwb.liveroom.domain.service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;


public interface SongCatalogPort {

    Optional<PlayableSong> findById(UUID songId);

    Optional<PlayableSongAudio> presignPlayback(UUID songId, Duration expiration);
}