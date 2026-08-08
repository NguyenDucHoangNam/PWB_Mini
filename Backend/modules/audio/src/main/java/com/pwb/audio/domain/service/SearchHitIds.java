package com.pwb.audio.domain.service;

import java.util.List;
import java.util.UUID;

/**
 * Ids in relevance order plus the total match count.
 *
 * <p>The search engine returns identity, not content: the rows are then read from Postgres, so a result
 * can never render a title the index has not caught up on yet.
 *
 * @param total may exceed {@code ids.size()} because the query was paged
 */
public record SearchHitIds(List<UUID> ids, long total) {
}
