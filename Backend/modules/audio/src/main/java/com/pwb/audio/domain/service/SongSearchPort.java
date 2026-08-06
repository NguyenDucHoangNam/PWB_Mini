package com.pwb.audio.domain.service;

import com.pwb.audio.domain.repository.SongSearchCriteria;

import java.util.List;
import java.util.Optional;

/**
 * The search engine's view of the song library.
 *
 * <p>Both methods answer {@link Optional#empty()} when the engine is unavailable, which is the signal for
 * the caller to run the equivalent database query instead. Empty is not the same as an empty result.
 */
public interface SongSearchPort {

    Optional<SearchHitIds> search(SongSearchCriteria criteria, int from, int size);

    /**
     * Search-as-you-type. Answers straight from the index rather than returning ids, because a database
     * round-trip on every keystroke would cost more than the seconds of staleness it saves.
     */
    Optional<List<SongSuggestion>> suggest(SongSearchCriteria criteria, int limit);
}
