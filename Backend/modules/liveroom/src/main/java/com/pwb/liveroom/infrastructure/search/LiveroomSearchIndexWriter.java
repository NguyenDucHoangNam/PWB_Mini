package com.pwb.liveroom.infrastructure.search;

import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.infra.search.SearchIndexNames;
import com.pwb.infra.search.SearchIndexPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LiveroomSearchIndexWriter {

    private final SearchIndexPublisher publisher;

    public void roomSaved(LiveRoom room) {
        publisher.upsert(SearchIndexNames.ROOMS, room.getId().toString(), RoomSearchDocument.from(room));
    }
}