package com.pwb.audio.application.usecase.impl;

import com.pwb.audio.application.support.VoiceTagViews;
import com.pwb.audio.application.usecase.VoiceTagSearchUseCase;
import com.pwb.audio.application.view.VoiceTagSuggestionView;
import com.pwb.audio.application.view.VoiceTagView;
import com.pwb.audio.domain.model.VoiceTag;
import com.pwb.audio.domain.repository.VoiceTagRepository;
import com.pwb.audio.domain.repository.VoiceTagSearchCriteria;
import com.pwb.audio.domain.service.SearchHitIds;
import com.pwb.audio.domain.service.VoiceTagSearchPort;
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
public class VoiceTagSearchUseCaseImpl implements VoiceTagSearchUseCase {

    private static final int MAX_SUGGESTIONS = 20;

    private final VoiceTagSearchPort voiceTagSearchPort;
    private final VoiceTagRepository voiceTagRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<VoiceTagView> search(VoiceTagSearchCriteria criteria, Pageable pageable) {
        Optional<SearchHitIds> hits = voiceTagSearchPort.search(
                criteria, (int) pageable.getOffset(), pageable.getPageSize());

        if (hits.isEmpty()) {
            log.debug("SEARCH.voiceTags fallback to database: userId={}", criteria.userId());
            Page<VoiceTag> page = voiceTagRepository.search(criteria, pageable);
            return new PageImpl<>(page.getContent().stream().map(VoiceTagViews::toView).toList(),
                    pageable, page.getTotalElements());
        }
        return toPage(hits.get(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoiceTagSuggestionView> suggest(VoiceTagSearchCriteria criteria, int limit) {
        if (!criteria.hasKeyword()) {
            return List.of();
        }
        int capped = Math.clamp(limit, 1, MAX_SUGGESTIONS);

        return voiceTagSearchPort.suggest(criteria, capped)
                .map(suggestions -> suggestions.stream()
                        .map(s -> new VoiceTagSuggestionView(
                                s.id(), s.name(), s.voiceName(), s.languageCode(), s.tagType()))
                        .toList())
                .orElseGet(() -> suggestFromDatabase(criteria, capped));
    }

    /** See {@link SongSearchUseCaseImpl} — the engine ranks, the database supplies the rows. */
    private Page<VoiceTagView> toPage(SearchHitIds hits, Pageable pageable) {
        Map<UUID, VoiceTag> byId = voiceTagRepository.findAllByIdIn(hits.ids()).stream()
                .collect(Collectors.toMap(VoiceTag::getId, Function.identity()));

        List<VoiceTagView> ordered = hits.ids().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(VoiceTagViews::toView)
                .toList();

        return new PageImpl<>(ordered, pageable, hits.total());
    }

    private List<VoiceTagSuggestionView> suggestFromDatabase(VoiceTagSearchCriteria criteria, int limit) {
        return voiceTagRepository.search(criteria, PageRequest.of(0, limit)).getContent().stream()
                .map(tag -> new VoiceTagSuggestionView(
                        tag.getId(), tag.getName(), tag.getVoiceName(),
                        tag.getLanguageCode(), tag.getTagType()))
                .toList();
    }
}
