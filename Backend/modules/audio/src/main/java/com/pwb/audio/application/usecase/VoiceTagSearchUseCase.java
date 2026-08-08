package com.pwb.audio.application.usecase;

import com.pwb.audio.application.view.VoiceTagSuggestionView;
import com.pwb.audio.application.view.VoiceTagView;
import com.pwb.audio.domain.repository.VoiceTagSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface VoiceTagSearchUseCase {

    Page<VoiceTagView> search(VoiceTagSearchCriteria criteria, Pageable pageable);

    /**
     * Search-as-you-type for the picker in the song upload form. Each suggestion carries the voice and
     * language as well as the name, so the caller can render the row without a second request.
     */
    List<VoiceTagSuggestionView> suggest(VoiceTagSearchCriteria criteria, int limit);
}
