package com.pwb.audio.infrastructure.search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.pwb.audio.domain.enums.SongStatus;
import com.pwb.audio.domain.repository.SongSearchCriteria;
import com.pwb.audio.domain.service.SearchHitIds;
import com.pwb.audio.domain.service.SongSearchPort;
import com.pwb.audio.domain.service.SongSuggestion;
import com.pwb.infra.search.SearchGateway;
import com.pwb.infra.search.SearchIndexNames;
import com.pwb.infra.search.SearchPage;
import com.pwb.infra.search.SearchQueries;
import com.pwb.infra.search.SearchQuerySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

@Service
@RequiredArgsConstructor
public class ElasticsearchSongSearchAdapter implements SongSearchPort {

    private static final String FIELD_TITLE = "title";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_FORMAT = "format";
    private static final String FIELD_DURATION = "durationSeconds";
    private static final String FIELD_CREATED_AT = "createdAt";

    private final SearchGateway searchGateway;

    @Override
    public Optional<SearchHitIds> search(SongSearchCriteria criteria, int from, int size) {
        Query query = buildQuery(criteria, SearchQueries::keywordMatch);
        SearchQuerySpec spec = SearchQuerySpec.ids(
                SearchIndexNames.SONGS, query, SearchQueries.byScoreThen(FIELD_CREATED_AT), from, size);

        return searchGateway.searchIds(spec).map(this::toHitIds);
    }

    @Override
    public Optional<List<SongSuggestion>> suggest(SongSearchCriteria criteria, int limit) {
        Query query = buildQuery(criteria, SearchQueries::autocomplete);
        SearchQuerySpec spec = SearchQuerySpec.sources(
                SearchIndexNames.SONGS, query, limit, List.of("id", FIELD_TITLE));

        return searchGateway.fetch(spec, SongSuggestDocument.class)
                .map(documents -> documents.stream()
                        .map(document -> new SongSuggestion(document.id(), document.title()))
                        .toList());
    }

    /**
     * The owner term and every narrowing condition go in {@code filter}, not {@code must}: they are yes/no
     * questions that must not influence the ranking, and Elasticsearch caches them. Only the text match
     * scores.
     *
     * <p>{@code matcher} is applied rather than passed in already built, because a search with filters and
     * no keyword is a legitimate request and building a match query against a null value throws.
     */
    private Query buildQuery(SongSearchCriteria criteria, BiFunction<String, String, Query> matcher) {
        BoolQuery.Builder bool = new BoolQuery.Builder()
                .filter(SearchQueries.ownedBy(FIELD_USER_ID, criteria.userId().toString()));

        if (!criteria.statuses().isEmpty()) {
            bool.filter(SearchQueries.terms(FIELD_STATUS, statusNames(criteria)));
        }
        if (criteria.format() != null && !criteria.format().isBlank()) {
            bool.filter(SearchQueries.term(FIELD_FORMAT, criteria.format().toLowerCase()));
        }
        if (criteria.minDurationSeconds() != null || criteria.maxDurationSeconds() != null) {
            bool.filter(SearchQueries.range(
                    FIELD_DURATION, criteria.minDurationSeconds(), criteria.maxDurationSeconds()));
        }
        if (criteria.hasKeyword()) {
            bool.must(matcher.apply(FIELD_TITLE, criteria.keyword()));
        }
        return Query.of(q -> q.bool(bool.build()));
    }

    private List<String> statusNames(SongSearchCriteria criteria) {
        return criteria.statuses().stream().map(SongStatus::name).toList();
    }

    private SearchHitIds toHitIds(SearchPage<String> page) {
        List<UUID> ids = page.items().stream().map(UUID::fromString).toList();
        return new SearchHitIds(ids, page.totalHits());
    }

    /** Shape of the trimmed {@code _source} an autocomplete request asks for. */
    record SongSuggestDocument(UUID id, String title) {
    }
}
