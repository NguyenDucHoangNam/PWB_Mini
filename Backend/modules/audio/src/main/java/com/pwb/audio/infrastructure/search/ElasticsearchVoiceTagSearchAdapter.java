package com.pwb.audio.infrastructure.search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.pwb.audio.domain.enums.VoiceTagType;
import com.pwb.audio.domain.repository.VoiceTagSearchCriteria;
import com.pwb.audio.domain.service.SearchHitIds;
import com.pwb.audio.domain.service.VoiceTagSearchPort;
import com.pwb.audio.domain.service.VoiceTagSuggestion;
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
public class ElasticsearchVoiceTagSearchAdapter implements VoiceTagSearchPort {

    private static final String FIELD_NAME = "name";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_TAG_TYPE = "tagType";
    private static final String FIELD_LANGUAGE_CODE = "languageCode";
    private static final String FIELD_VOICE_NAME = "voiceName";
    private static final String FIELD_CREATED_AT = "createdAt";

    private final SearchGateway searchGateway;

    @Override
    public Optional<SearchHitIds> search(VoiceTagSearchCriteria criteria, int from, int size) {
        Query query = buildQuery(criteria, SearchQueries::keywordMatch);
        SearchQuerySpec spec = SearchQuerySpec.ids(
                SearchIndexNames.VOICE_TAGS, query, SearchQueries.byScoreThen(FIELD_CREATED_AT), from, size);

        return searchGateway.searchIds(spec).map(this::toHitIds);
    }

    @Override
    public Optional<List<VoiceTagSuggestion>> suggest(VoiceTagSearchCriteria criteria, int limit) {
        Query query = buildQuery(criteria, SearchQueries::autocomplete);
        SearchQuerySpec spec = SearchQuerySpec.sources(
                SearchIndexNames.VOICE_TAGS,
                query,
                limit,
                List.of("id", FIELD_NAME, FIELD_VOICE_NAME, FIELD_LANGUAGE_CODE, FIELD_TAG_TYPE));

        return searchGateway.fetch(spec, VoiceTagSuggestDocument.class)
                .map(documents -> documents.stream()
                        .map(VoiceTagSuggestDocument::toSuggestion)
                        .toList());
    }

    /**
     * {@code matcher} is applied rather than passed in already built: filtering by type or language with
     * no keyword is a legitimate request, and building a match query against a null value throws.
     */
    private Query buildQuery(VoiceTagSearchCriteria criteria, BiFunction<String, String, Query> matcher) {
        BoolQuery.Builder bool = new BoolQuery.Builder()
                .filter(SearchQueries.ownedBy(FIELD_USER_ID, criteria.userId().toString()));

        if (criteria.tagType() != null) {
            bool.filter(SearchQueries.term(FIELD_TAG_TYPE, criteria.tagType().name()));
        }
        if (criteria.languageCode() != null && !criteria.languageCode().isBlank()) {
            bool.filter(SearchQueries.term(FIELD_LANGUAGE_CODE, criteria.languageCode()));
        }
        if (criteria.hasKeyword()) {
            bool.must(matcher.apply(FIELD_NAME, criteria.keyword()));
        }
        return Query.of(q -> q.bool(bool.build()));
    }

    private SearchHitIds toHitIds(SearchPage<String> page) {
        List<UUID> ids = page.items().stream().map(UUID::fromString).toList();
        return new SearchHitIds(ids, page.totalHits());
    }

    record VoiceTagSuggestDocument(
            UUID id,
            String name,
            String voiceName,
            String languageCode,
            String tagType
    ) {
        VoiceTagSuggestion toSuggestion() {
            return new VoiceTagSuggestion(
                    id,
                    name,
                    voiceName,
                    languageCode,
                    tagType == null ? null : VoiceTagType.valueOf(tagType));
        }
    }
}
