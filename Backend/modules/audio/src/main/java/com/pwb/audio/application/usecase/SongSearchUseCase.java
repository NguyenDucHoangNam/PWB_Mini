package com.pwb.audio.application.usecase;

import com.pwb.audio.application.view.SongSuggestionView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.domain.repository.SongSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SongSearchUseCase {

    /** Relevance-ordered, paged. Falls back to a database query when the search engine is unavailable. */
    Page<SongView> search(SongSearchCriteria criteria, Pageable pageable);

    /** Search-as-you-type. Answers with at most {@code limit} titles. */
    List<SongSuggestionView> suggest(SongSearchCriteria criteria, int limit);
}
