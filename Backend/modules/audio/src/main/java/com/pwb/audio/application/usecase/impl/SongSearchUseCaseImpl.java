package com.pwb.audio.application.usecase.impl;

import com.pwb.audio.application.support.SongViewFactory;
import com.pwb.audio.application.usecase.SongSearchUseCase;
import com.pwb.audio.application.view.SongSuggestionView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.repository.SongRepository;
import com.pwb.audio.domain.repository.SongSearchCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SongSearchUseCaseImpl implements SongSearchUseCase {

    private static final int MAX_SUGGESTIONS = 20;

    private final SongRepository songRepository;
    private final SongViewFactory songViewFactory;

    @Override
    @Transactional(readOnly = true)
    public Page<SongView> search(SongSearchCriteria criteria, Pageable pageable) {
        Page<Song> page = songRepository.search(criteria, pageable);
        List<SongView> views = songViewFactory.toViews(page.getContent());
        return new PageImpl<>(views, pageable, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SongSuggestionView> suggest(SongSearchCriteria criteria, int limit) {
        if (!criteria.hasKeyword()) {
            return List.of();
        }
        int capped = Math.clamp(limit, 1, MAX_SUGGESTIONS);

        return songRepository.search(criteria, PageRequest.of(0, capped)).getContent().stream()
                .map(song -> new SongSuggestionView(song.getId(), song.getTitle()))
                .toList();
    }
}
