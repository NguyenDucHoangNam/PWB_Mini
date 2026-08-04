package com.pwb.audio.domain.repository;

import com.pwb.audio.domain.enums.SongStatus;
import com.pwb.audio.domain.model.Song;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface SongRepository {

    Song save(Song song);

    Optional<Song> findById(UUID id);

    Optional<Song> findByIdAndUserId(UUID id, UUID userId);

    Page<Song> findAllByUserId(UUID userId, Pageable pageable);

    /** Same listing narrowed to one status; paging is applied after the filter, not before it. */
    Page<Song> findAllByUserIdAndStatus(UUID userId, SongStatus status, Pageable pageable);

    /** Hard delete: songs carry no soft-delete state, removal is permanent. */
    void deleteById(UUID id);
}
