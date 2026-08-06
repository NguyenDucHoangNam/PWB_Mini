package com.pwb.infra.search;

import java.util.List;

/**
 * @param items      hits in relevance order
 * @param totalHits  total matches, which may exceed {@code items.size()} because the request was paged
 */
public record SearchPage<T>(List<T> items, long totalHits) {

    public static <T> SearchPage<T> empty() {
        return new SearchPage<>(List.of(), 0L);
    }
}
