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
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchSearchGatewayAdapter implements SearchGateway {

    private final ElasticsearchClient client;
    private final SearchIndexNames indexNames;
    private final SearchConfig config;

    /**
     * Whether the last query reached the cluster. Only used to decide the log level, so a lost race
     * between two threads costs one duplicated line and nothing else.
     */
    private final AtomicBoolean reachable = new AtomicBoolean(true);

    @Override
    public Optional<SearchPage<String>> searchIds(SearchQuerySpec spec) {
        if (!config.isEnabled()) {
            return Optional.empty();
        }
        try {
            SearchResponse<Void> response = client.search(toRequest(spec, false), Void.class);
            markReachable();
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
            markReachable();
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
     * Logged on the transition, not on the request.
     *
     * <p>This used to be unconditional {@code debug}, on the reasoning that an outage fires it on every
     * keystroke while the fallback keeps the caller working. The first half is right and the second is
     * the problem: results silently stop being ranked, autocomplete silently stops matching Vietnamese
     * diacritics, and at the default {@code INFO} level production emits no evidence at all. The
     * deployment checklist asked readers to look for a line that could never be printed.
     *
     * <p>So the first failure after a healthy period warns once, naming the reason, and every failure
     * behind it drops back to debug. One line per outage instead of one per keystroke — and, paired
     * with {@link #markReachable()}, an interval in the log rather than a single ambiguous moment.
     */
    private void logUnavailable(SearchQuerySpec spec, Exception ex) {
        if (reachable.compareAndSet(true, false)) {
            log.warn("SEARCH.unavailable: index={} reason={} — queries now answer from PostgreSQL, "
                            + "without relevance ranking or Vietnamese diacritic matching",
                    spec.index(), ex.getMessage());
            return;
        }
        log.debug("SEARCH.unavailable, falling back to database: index={} reason={}",
                spec.index(), ex.getMessage());
    }

    private void markReachable() {
        if (reachable.compareAndSet(false, true)) {
            log.info("SEARCH.recovered: Elasticsearch is answering again");
        }
    }
}
