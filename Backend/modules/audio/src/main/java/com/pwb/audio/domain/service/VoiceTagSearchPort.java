package com.pwb.audio.domain.service;

import com.pwb.audio.domain.repository.VoiceTagSearchCriteria;

import java.util.List;
import java.util.Optional;

/**
 * The search engine's view of the voice tag library. Same empty-means-unavailable contract as
 * {@link SongSearchPort}.
 */
public interface VoiceTagSearchPort {

    Optional<SearchHitIds> search(VoiceTagSearchCriteria criteria, int from, int size);

    Optional<List<VoiceTagSuggestion>> suggest(VoiceTagSearchCriteria criteria, int limit);
}
