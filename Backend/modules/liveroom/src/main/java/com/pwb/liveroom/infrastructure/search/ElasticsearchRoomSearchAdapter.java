package com.pwb.liveroom.infrastructure.search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.pwb.infra.search.SearchGateway;
import com.pwb.infra.search.SearchIndexNames;
import com.pwb.infra.search.SearchPage;
import com.pwb.infra.search.SearchQueries;
import com.pwb.infra.search.SearchQuerySpec;
import com.pwb.liveroom.domain.repository.RoomSearchCriteria;
import com.pwb.liveroom.domain.service.RoomSearchHits;
import com.pwb.liveroom.domain.service.RoomSearchPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ElasticsearchRoomSearchAdapter implements RoomSearchPort {

    private static final String FIELD_ROOM_NAME = "roomName";
    private static final String FIELD_ROOM_CODE = "roomCode";
    private static final String FIELD_OWNER_ID = "ownerId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_CREATED_AT = "createdAt";

    private static final float ROOM_CODE_BOOST = 10.0f;

    private final SearchGateway searchGateway;

    @Override
    public Optional<RoomSearchHits> search(RoomSearchCriteria criteria, int from, int size) {
        SearchQuerySpec spec = SearchQuerySpec.ids(
                SearchIndexNames.ROOMS,
                buildQuery(criteria),
                SearchQueries.byScoreThen(FIELD_CREATED_AT),
                from,
                size);

        return searchGateway.searchIds(spec).map(this::toHits);
    }

    private Query buildQuery(RoomSearchCriteria criteria) {
        BoolQuery.Builder bool = new BoolQuery.Builder()
                .filter(SearchQueries.ownedBy(FIELD_OWNER_ID, criteria.ownerId().toString()));

        if (criteria.status() != null) {
            bool.filter(SearchQueries.term(FIELD_STATUS, criteria.status().name()));
        }
        if (criteria.hasKeyword()) {
            bool.must(Query.of(q -> q.bool(inner -> inner
                    .should(s -> s.term(t -> t
                            .field(FIELD_ROOM_CODE)
                            .value(criteria.keyword())
                            .boost(ROOM_CODE_BOOST)))
                    .should(SearchQueries.keywordMatch(FIELD_ROOM_NAME, criteria.keyword()))
                    .minimumShouldMatch("1"))));
        }
        return Query.of(q -> q.bool(bool.build()));
    }

    private RoomSearchHits toHits(SearchPage<String> page) {
        List<UUID> ids = page.items().stream().map(UUID::fromString).toList();
        return new RoomSearchHits(ids, page.totalHits());
    }
}