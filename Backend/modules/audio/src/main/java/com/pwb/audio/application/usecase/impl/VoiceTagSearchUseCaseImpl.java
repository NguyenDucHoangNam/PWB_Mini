package com.pwb.audio.application.usecase.impl;

import com.pwb.audio.application.support.VoiceTagViews;
import com.pwb.audio.application.usecase.VoiceTagSearchUseCase;
import com.pwb.audio.application.view.VoiceTagSuggestionView;
import com.pwb.audio.application.view.VoiceTagView;
import com.pwb.audio.domain.model.VoiceTag;
import com.pwb.audio.domain.repository.VoiceTagRepository;
import com.pwb.audio.domain.repository.VoiceTagSearchCriteria;
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
public class VoiceTagSearchUseCaseImpl implements VoiceTagSearchUseCase {

    private static final int MAX_SUGGESTIONS = 20;

    private final VoiceTagRepository voiceTagRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<VoiceTagView> search(VoiceTagSearchCriteria criteria, Pageable pageable) {
        Page<VoiceTag> page = voiceTagRepository.search(criteria, pageable);
        return new PageImpl<>(page.getContent().stream().map(VoiceTagViews::toView).toList(),
                pageable, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoiceTagSuggestionView> suggest(VoiceTagSearchCriteria criteria, int limit) {
        if (!criteria.hasKeyword()) {
            return List.of();
        }
        int capped = Math.clamp(limit, 1, MAX_SUGGESTIONS);

        return voiceTagRepository.search(criteria, PageRequest.of(0, capped)).getContent().stream()
                .map(tag -> new VoiceTagSuggestionView(
                        tag.getId(), tag.getName(), tag.getVoiceName(),
                        tag.getLanguageCode(), tag.getTagType()))
                .toList();
    }
}
