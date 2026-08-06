package com.pwb.iam.infrastructure.search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.pwb.iam.domain.repository.UserSearchCriteria;
import com.pwb.iam.domain.service.UserSearchHits;
import com.pwb.iam.domain.service.UserSearchPort;
import com.pwb.iam.domain.service.UserSuggestion;
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

@Service
@RequiredArgsConstructor
public class ElasticsearchUserSearchAdapter implements UserSearchPort {

    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_FULL_NAME = "fullName";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_ROLE_NAME = "roleName";
    private static final String FIELD_OAUTH_PROVIDER = "oauthProvider";
    private static final String FIELD_DELETED = "deleted";
    private static final String FIELD_CREATED_AT = "createdAt";

    private final SearchGateway searchGateway;

    @Override
    public Optional<UserSearchHits> search(UserSearchCriteria criteria, int from, int size) {
        SearchQuerySpec spec = SearchQuerySpec.ids(
                SearchIndexNames.USERS,
                buildQuery(criteria, false),
                SearchQueries.byScoreThen(FIELD_CREATED_AT),
                from,
                size);

        return searchGateway.searchIds(spec).map(this::toHits);
    }

    @Override
    public Optional<List<UserSuggestion>> suggest(UserSearchCriteria criteria, int limit) {
        SearchQuerySpec spec = SearchQuerySpec.sources(
                SearchIndexNames.USERS,
                buildQuery(criteria, true),
                limit,
                List.of("id", FIELD_EMAIL, FIELD_FULL_NAME));

        return searchGateway.fetch(spec, UserSuggestDocument.class)
                .map(documents -> documents.stream()
                        .map(d -> new UserSuggestion(d.id(), d.email(), d.fullName()))
                        .toList());
    }

    /**
     * A soft-deleted account stays in the index — the write path has no delete event to send — so every
     * query has to exclude it here. Omitting this filter would resurrect deleted users in admin search.
     */
    private Query buildQuery(UserSearchCriteria criteria, boolean prefixMatch) {
        BoolQuery.Builder bool = new BoolQuery.Builder()
                .filter(SearchQueries.term(FIELD_DELETED, false));

        if (criteria.status() != null) {
            bool.filter(SearchQueries.term(FIELD_STATUS, criteria.status().name()));
        }
        if (criteria.role() != null) {
            bool.filter(SearchQueries.term(FIELD_ROLE_NAME, criteria.role().name()));
        }
        if (criteria.provider() != null) {
            bool.filter(SearchQueries.term(FIELD_OAUTH_PROVIDER, criteria.provider().name()));
        }
        if (hasKeyword(criteria)) {
            String keyword = criteria.keyword().trim();
            bool.must(Query.of(q -> q.bool(inner -> inner
                    .should(match(FIELD_EMAIL, keyword, prefixMatch))
                    .should(match(FIELD_FULL_NAME, keyword, prefixMatch))
                    .minimumShouldMatch("1"))));
        }
        return Query.of(q -> q.bool(bool.build()));
    }

    private Query match(String field, String keyword, boolean prefixMatch) {
        return prefixMatch
                ? SearchQueries.autocomplete(field, keyword)
                : SearchQueries.keywordMatch(field, keyword);
    }

    private boolean hasKeyword(UserSearchCriteria criteria) {
        return criteria.keyword() != null && !criteria.keyword().isBlank();
    }

    private UserSearchHits toHits(SearchPage<String> page) {
        List<UUID> ids = page.items().stream().map(UUID::fromString).toList();
        return new UserSearchHits(ids, page.totalHits());
    }

    record UserSuggestDocument(UUID id, String email, String fullName) {
    }
}
