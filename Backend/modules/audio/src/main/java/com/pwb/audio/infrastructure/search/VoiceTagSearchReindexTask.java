package com.pwb.audio.infrastructure.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.audio.infrastructure.persistence.mapper.VoiceTagMapper;
import com.pwb.audio.infrastructure.persistence.repository.VoiceTagJpaRepository;
import com.pwb.infra.search.SearchAction;
import com.pwb.infra.search.SearchIndexEvent;
import com.pwb.infra.search.SearchIndexNames;
import com.pwb.infra.search.SearchReindexTask;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Rebuilds the voice tag index from the table. See {@link SongSearchReindexTask} on the ordering. */
@Component
@RequiredArgsConstructor
public class VoiceTagSearchReindexTask implements SearchReindexTask {

    private final VoiceTagJpaRepository voiceTagJpaRepository;
    private final VoiceTagMapper voiceTagMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String indexName() {
        return SearchIndexNames.VOICE_TAGS;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchIndexEvent> readPage(int page, int size) {
        return voiceTagJpaRepository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")))
                .map(voiceTagMapper::toDomain)
                .map(voiceTag -> new SearchIndexEvent(
                        SearchIndexNames.VOICE_TAGS,
                        voiceTag.getId().toString(),
                        SearchAction.UPSERT,
                        objectMapper.valueToTree(VoiceTagSearchDocument.from(voiceTag))))
                .getContent();
    }
}
