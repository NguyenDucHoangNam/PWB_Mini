package com.pwb.infra.search;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The wire payload carried from a writer, through the outbox and Kafka, to the indexer.
 *
 * @param index    logical index name, without the configured prefix
 * @param docId    document id, always the aggregate's primary key
 * @param action   whether the document is being written or removed
 * @param document the document body; null for a {@link SearchAction#DELETE}
 */
public record SearchIndexEvent(
        String index,
        String docId,
        SearchAction action,
        JsonNode document
) {
}
