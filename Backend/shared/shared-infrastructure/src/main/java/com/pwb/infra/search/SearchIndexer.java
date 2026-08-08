package com.pwb.infra.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Write side of the search stack. Failures are logged and swallowed rather than propagated: the index is
 * a derived copy, and a document that fails to land can be replayed from the outbox or rebuilt by a
 * reindex. Throwing here would let a search outage break the write it was derived from.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexer {

    private final ElasticsearchClient client;
    private final SearchIndexNames indexNames;
    private final SearchConfig config;

    public void apply(SearchIndexEvent event) {
        if (!config.isEnabled()) {
            return;
        }
        try {
            String index = indexNames.physical(event.index());
            if (event.action() == SearchAction.DELETE) {
                client.delete(new DeleteRequest.Builder().index(index).id(event.docId()).build());
            } else {
                client.index(new IndexRequest.Builder<JsonNode>()
                        .index(index)
                        .id(event.docId())
                        .document(requireDocument(event))
                        .build());
            }
            log.debug("SEARCH.indexed: index={} id={} action={}",
                    event.index(), event.docId(), event.action());
        } catch (Exception ex) {
            log.warn("SEARCH.index failed: index={} id={} action={} reason={}",
                    event.index(), event.docId(), event.action(), ex.getMessage());
        }
    }

    public void bulk(List<SearchIndexEvent> events) {
        if (!config.isEnabled() || events.isEmpty()) {
            return;
        }
        try {
            BulkRequest.Builder request = new BulkRequest.Builder();
            events.forEach(event -> request.operations(toOperation(event)));

            BulkResponse response = client.bulk(request.build());
            if (response.errors()) {
                logItemFailures(response);
            }
        } catch (Exception ex) {
            log.warn("SEARCH.bulk failed: size={} reason={}", events.size(), ex.getMessage());
        }
    }

    private BulkOperation toOperation(SearchIndexEvent event) {
        String index = indexNames.physical(event.index());
        if (event.action() == SearchAction.DELETE) {
            return BulkOperation.of(op -> op.delete(
                    new DeleteOperation.Builder().index(index).id(event.docId()).build()));
        }
        IndexOperation<JsonNode> operation = new IndexOperation.Builder<JsonNode>()
                .index(index)
                .id(event.docId())
                .document(requireDocument(event))
                .build();
        return BulkOperation.of(op -> op.index(operation));
    }

    private JsonNode requireDocument(SearchIndexEvent event) {
        if (event.document() == null) {
            throw new IllegalArgumentException(
                    "UPSERT event carries no document: index=" + event.index() + " id=" + event.docId());
        }
        return event.document();
    }

    private void logItemFailures(BulkResponse response) {
        List<BulkResponseItem> failed = response.items().stream()
                .filter(item -> item.error() != null)
                .toList();
        failed.forEach(item -> log.warn("SEARCH.bulk item failed: index={} id={} reason={}",
                item.index(), item.id(), item.error().reason()));
    }
}
