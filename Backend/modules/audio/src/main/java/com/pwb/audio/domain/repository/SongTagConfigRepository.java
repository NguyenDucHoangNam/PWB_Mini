package com.pwb.audio.domain.repository;

import com.pwb.audio.domain.model.SongTagConfig;

import java.util.Optional;
import java.util.UUID;

public interface SongTagConfigRepository {

    SongTagConfig save(SongTagConfig config);

    Optional<SongTagConfig> findBySongId(UUID songId);

    void deleteBySongId(UUID songId);
}
