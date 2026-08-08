package com.pwb.infra.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Rebuilds an index from Postgres, page by page.
 *
 * <p>Writes straight to Elasticsearch instead of going through the outbox: this is a bulk catch-up, not a
 * business event, and pushing tens of thousands of rows through the relay would drown the queue that
 * ordinary writes depend on. Documents are upserted by id, so a run overwrites rather than duplicates and
 * is safe to repeat. It does not remove documents whose rows are already gone — for that, delete the
 * index and let startup recreate it, then run this.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchReindexService {

    private final SearchIndexer indexer;
    private final SearchConfig config;
    private final List<SearchReindexTask> tasks;

    public List<String> availableIndices() {
        return tasks.stream().map(SearchReindexTask::indexName).toList();
    }

    /**
     * @return how many documents were sent to the cluster
     * @throws IllegalArgumentException if no module owns an index by that name
     */
    public int reindex(String indexName) {
        SearchReindexTask task = findTask(indexName);
        int pageSize = Math.max(1, config.getReindexPageSize());
        int page = 0;
        int total = 0;

        while (true) {
            List<SearchIndexEvent> events = task.readPage(page, pageSize);
            if (events.isEmpty()) {
                break;
            }
            for (int start = 0; start < events.size(); start += config.getBulkSize()) {
                int end = Math.min(events.size(), start + config.getBulkSize());
                indexer.bulk(events.subList(start, end));
            }
            total += events.size();
            page++;
        }

        log.info("SEARCH.reindex finished: index={} documents={}", indexName, total);
        return total;
    }

    private SearchReindexTask findTask(String indexName) {
        Optional<SearchReindexTask> match = tasks.stream()
                .filter(task -> task.indexName().equals(indexName))
                .findFirst();
        return match.orElseThrow(() -> new IllegalArgumentException(
                "No reindex task for index '" + indexName + "'. Known indices: " + availableIndices()));
    }
}
