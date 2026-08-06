package com.pwb.liveroom.infrastructure.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.infra.search.SearchAction;
import com.pwb.infra.search.SearchIndexEvent;
import com.pwb.infra.search.SearchIndexNames;
import com.pwb.infra.search.SearchReindexTask;
import com.pwb.liveroom.infrastructure.persistence.mapper.LiveRoomMapper;
import com.pwb.liveroom.infrastructure.persistence.repository.LiveRoomJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RoomSearchReindexTask implements SearchReindexTask {

    private final LiveRoomJpaRepository liveRoomJpaRepository;
    private final LiveRoomMapper liveRoomMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String indexName() {
        return SearchIndexNames.ROOMS;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchIndexEvent> readPage(int page, int size) {
        return liveRoomJpaRepository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")))
                .map(liveRoomMapper::toDomain)
                .map(room -> new SearchIndexEvent(
                        SearchIndexNames.ROOMS,
                        room.getId().toString(),
                        SearchAction.UPSERT,
                        objectMapper.valueToTree(RoomSearchDocument.from(room))))
                .getContent();
    }
}