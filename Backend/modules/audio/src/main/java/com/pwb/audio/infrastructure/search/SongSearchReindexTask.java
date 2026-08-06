package com.pwb.audio.infrastructure.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.audio.infrastructure.persistence.mapper.SongMapper;
import com.pwb.audio.infrastructure.persistence.repository.SongJpaRepository;
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

/**
 * Rebuilds the song index from the table. Ordered by primary key rather than by a timestamp, so a row
 * written mid-run cannot shift a later page and cause another row to be skipped.
 */
@Component
@RequiredArgsConstructor
public class SongSearchReindexTask implements SearchReindexTask {

    private final SongJpaRepository songJpaRepository;
    private final SongMapper songMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String indexName() {
        return SearchIndexNames.SONGS;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchIndexEvent> readPage(int page, int size) {
        return songJpaRepository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")))
                .map(songMapper::toDomain)
                .map(song -> new SearchIndexEvent(
                        SearchIndexNames.SONGS,
                        song.getId().toString(),
                        SearchAction.UPSERT,
                        objectMapper.valueToTree(SongSearchDocument.from(song))))
                .getContent();
    }
}
