package com.pwb.infra.search;

import java.util.List;
import java.util.Optional;

/**
 * Read side of the search stack.
 *
 * <p>Every method answers {@link Optional#empty()} rather than throwing when the cluster is unreachable,
 * slow or switched off. That is the whole contract: an empty answer means "search is unavailable, use the
 * database", and is deliberately distinct from a present-but-empty {@link SearchPage}, which means "the
 * cluster answered and nothing matched". Callers that confuse the two will show an empty result list
 * during an outage instead of falling back.
 */
public interface SearchGateway {

    /**
     * Runs the query and returns only document ids. The caller re-reads the rows from Postgres, so a
     * result can never render a document that the index has not caught up on.
     */
    Optional<SearchPage<String>> searchIds(SearchQuerySpec spec);

    /**
     * Runs the query and deserialises {@code _source} straight into {@code documentType}. Used by
     * autocomplete, where a database round-trip per keystroke would cost more than the few seconds of
     * staleness it avoids.
     */
    <T> Optional<List<T>> fetch(SearchQuerySpec spec, Class<T> documentType);
}
