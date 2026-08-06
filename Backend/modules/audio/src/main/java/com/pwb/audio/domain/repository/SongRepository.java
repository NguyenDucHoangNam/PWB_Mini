package com.pwb.audio.domain.repository;

import com.pwb.audio.domain.enums.SongStatus;
import com.pwb.audio.domain.model.Song;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SongRepository {

    Song save(Song song);

    Optional<Song> findById(UUID id);

    Optional<Song> findByIdAndUserId(UUID id, UUID userId);

    Page<Song> findAllByUserId(UUID userId, Pageable pageable);

    /**
     * Same listing narrowed to a set of statuses; paging is applied after the filter, not before it. A set
     * rather than one value because the UI groups several job states into a single user-facing filter.
     */
    Page<Song> findAllByUserIdAndStatusIn(UUID userId, Collection<SongStatus> statuses, Pageable pageable);

    /**
     * Reads the rows a search matched. Returned in no particular order — the caller holds the relevance
     * ordering and re-applies it, since a database has no notion of how well a row matched.
     */
    List<Song> findAllByIdIn(Collection<UUID> ids);

    /**
     * The fallback for a search when the engine is unavailable. Applies the same filters, matching the
     * title as a plain substring.
     */
    Page<Song> search(SongSearchCriteria criteria, Pageable pageable);

    /** Hard delete: songs carry no soft-delete state, removal is permanent. */
    void deleteById(UUID id);
}
