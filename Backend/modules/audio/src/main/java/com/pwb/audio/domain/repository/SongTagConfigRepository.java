package com.pwb.audio.domain.repository;

import com.pwb.audio.domain.model.SongTagConfig;

import java.util.Optional;
import java.util.UUID;

public interface SongTagConfigRepository {

    SongTagConfig save(SongTagConfig config);

    Optional<SongTagConfig> findBySongId(UUID songId);

    /** Guards voice tag deletion: a tag still wired into a song must not disappear underneath it. */
    boolean existsByVoiceTagId(UUID voiceTagId);

    void deleteBySongId(UUID songId);
}
