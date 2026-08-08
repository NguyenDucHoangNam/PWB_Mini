package com.pwb.infra.search;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import java.util.List;

/**
 * One request to the cluster, assembled by the module that owns the index.
 *
 * @param index          logical index name, without the configured prefix
 * @param query          the assembled query; the owner-scoping term belongs in its filter clause
 * @param sort           applied before the implicit tie-break on {@code _score}; empty means score order
 * @param from           offset of the first hit
 * @param size           maximum hits to return
 * @param sourceIncludes fields to read back; empty means the document id is all the caller needs
 */
public record SearchQuerySpec(
        String index,
        Query query,
        List<SortOptions> sort,
        int from,
        int size,
        List<String> sourceIncludes
) {

    public static SearchQuerySpec ids(String index, Query query, List<SortOptions> sort, int from, int size) {
        return new SearchQuerySpec(index, query, sort, from, size, List.of());
    }

    public static SearchQuerySpec sources(String index, Query query, int size, List<String> sourceIncludes) {
        return new SearchQuerySpec(index, query, List.of(), 0, size, sourceIncludes);
    }
}
