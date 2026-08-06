package com.pwb.audio.application.usecase.impl;

import com.pwb.audio.application.support.SongViewFactory;
import com.pwb.audio.application.usecase.SongSearchUseCase;
import com.pwb.audio.application.view.SongSuggestionView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.repository.SongRepository;
import com.pwb.audio.domain.repository.SongSearchCriteria;
import com.pwb.audio.domain.service.SearchHitIds;
import com.pwb.audio.domain.service.SongSearchPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongSearchUseCaseImpl implements SongSearchUseCase {

    private static final int MAX_SUGGESTIONS = 20;

    private final SongSearchPort songSearchPort;
    private final SongRepository songRepository;
    private final SongViewFactory songViewFactory;

    @Override
    @Transactional(readOnly = true)
    public Page<SongView> search(SongSearchCriteria criteria, Pageable pageable) {
        Optional<SearchHitIds> hits = songSearchPort.search(
                criteria, (int) pageable.getOffset(), pageable.getPageSize());

        if (hits.isEmpty()) {
            return searchInDatabase(criteria, pageable);
        }
        return toPage(hits.get(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SongSuggestionView> suggest(SongSearchCriteria criteria, int limit) {
        if (!criteria.hasKeyword()) {
            return List.of();
        }
        int capped = Math.clamp(limit, 1, MAX_SUGGESTIONS);

        return songSearchPort.suggest(criteria, capped)
                .map(suggestions -> suggestions.stream()
                        .map(s -> new SongSuggestionView(s.id(), s.title()))
                        .toList())
                .orElseGet(() -> suggestFromDatabase(criteria, capped));
    }

    /**
     * The engine ranked the ids; the rows come from Postgres so nothing renders from a stale copy. The
     * database returns them in its own order, so the ranking is re-applied here — losing it would leave
     * a "most relevant first" list sorted by nothing in particular.
     */
    private Page<SongView> toPage(SearchHitIds hits, Pageable pageable) {
        List<Song> songs = songRepository.findAllByIdIn(hits.ids());
        Map<UUID, Song> byId = songs.stream()
                .collect(Collectors.toMap(Song::getId, Function.identity()));

        // A hit whose row has since been deleted is dropped rather than rendered as a gap: the index is
        // eventually consistent, so it can briefly point at something that is already gone.
        List<Song> ordered = hits.ids().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(songViewFactory.toViews(ordered), pageable, hits.total());
    }

    private Page<SongView> searchInDatabase(SongSearchCriteria criteria, Pageable pageable) {
        log.debug("SEARCH.songs fallback to database: userId={}", criteria.userId());
        Page<Song> page = songRepository.search(criteria, pageable);
        List<SongView> views = songViewFactory.toViews(page.getContent());
        return new PageImpl<>(views, pageable, page.getTotalElements());
    }

    private List<SongSuggestionView> suggestFromDatabase(SongSearchCriteria criteria, int limit) {
        Pageable page = PageRequest.of(0, limit);
        return songRepository.search(criteria, page).getContent().stream()
                .map(song -> new SongSuggestionView(song.getId(), song.getTitle()))
                .toList();
    }
}
