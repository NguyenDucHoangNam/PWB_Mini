package com.pwb.infra.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchSearchGatewayAdapter implements SearchGateway {

    private final ElasticsearchClient client;
    private final SearchIndexNames indexNames;
    private final SearchConfig config;

    @Override
    public Optional<SearchPage<String>> searchIds(SearchQuerySpec spec) {
        if (!config.isEnabled()) {
            return Optional.empty();
        }
        try {
            SearchResponse<Void> response = client.search(toRequest(spec, false), Void.class);
            List<String> ids = response.hits().hits().stream()
                    .map(Hit::id)
                    .filter(Objects::nonNull)
                    .toList();
            return Optional.of(new SearchPage<>(ids, totalHits(response)));
        } catch (Exception ex) {
            logUnavailable(spec, ex);
            return Optional.empty();
        }
    }

    @Override
    public <T> Optional<List<T>> fetch(SearchQuerySpec spec, Class<T> documentType) {
        if (!config.isEnabled()) {
            return Optional.empty();
        }
        try {
            SearchResponse<T> response = client.search(toRequest(spec, true), documentType);
            List<T> documents = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .toList();
            return Optional.of(documents);
        } catch (Exception ex) {
            logUnavailable(spec, ex);
            return Optional.empty();
        }
    }

    private SearchRequest toRequest(SearchQuerySpec spec, boolean withSource) {
        SearchRequest.Builder builder = new SearchRequest.Builder()
                .index(indexNames.physical(spec.index()))
                .query(spec.query())
                .from(Math.max(0, spec.from()))
                .size(Math.max(0, spec.size()))
                // Anything past the requested page is irrelevant to the answer, and counting it exactly
                // costs more the larger the index grows. The cap is well above any page a user reaches.
                .trackTotalHits(t -> t.count(10_000));

        if (!spec.sort().isEmpty()) {
            builder.sort(spec.sort());
        }
        if (withSource && !spec.sourceIncludes().isEmpty()) {
            builder.source(s -> s.filter(f -> f.includes(spec.sourceIncludes())));
        } else if (!withSource) {
            builder.source(s -> s.fetch(false));
        }
        return builder.build();
    }

    private long totalHits(SearchResponse<?> response) {
        return response.hits().total() == null ? 0L : response.hits().total().value();
    }

    /**
     * Debug rather than warn: during an outage this fires on every keystroke, and the fallback means
     * nothing is actually broken from the caller's side.
     */
    private void logUnavailable(SearchQuerySpec spec, Exception ex) {
        log.debug("SEARCH.unavailable, falling back to database: index={} reason={}",
                spec.index(), ex.getMessage());
    }
}
