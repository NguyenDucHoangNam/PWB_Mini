package com.pwb.audio.domain.repository;

import com.pwb.audio.domain.enums.SongStatus;
import com.pwb.audio.domain.model.Song;

import java.util.Optional;
import java.util.UUID;

public interface SongRepository {

    Song save(Song song);

    Optional<Song> findById(UUID id);

    Optional<Song> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);
}
