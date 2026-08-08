package com.pwb.infra.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.infra.kafka.properties.KafkaTopicProperties;
import com.pwb.infra.outbox.api.OutboxEnqueueHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Entry point for keeping an index in step with Postgres.
 *
 * <p>Callers are repository adapters, which is the one place every write to an aggregate passes through —
 * hooking use cases instead would mean finding all of them and finding them again each time one is added.
 * The enqueue joins the caller's transaction, so a rolled-back write emits nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexPublisher {

    private static final String AGGREGATE_TYPE = "SearchIndex";

    private final OutboxEnqueueHelper outboxEnqueueHelper;
    private final ObjectMapper objectMapper;
    private final KafkaTopicProperties topicProperties;
    private final SearchConfig config;

    public void upsert(String index, String docId, Object document) {
        enqueue(new SearchIndexEvent(index, docId, SearchAction.UPSERT, objectMapper.valueToTree(document)));
    }

    public void delete(String index, String docId) {
        enqueue(new SearchIndexEvent(index, docId, SearchAction.DELETE, null));
    }

    /**
     * A search index that falls behind is a degraded search, not a failed write. Serialisation problems
     * are therefore logged rather than thrown — letting one bubble up would abort the transaction of the
     * business operation that triggered it.
     */
    private void enqueue(SearchIndexEvent event) {
        if (!config.isEnabled()) {
            return;
        }
        try {
            outboxEnqueueHelper.enqueue(
                    topicProperties.getSearchIndex(),
                    AGGREGATE_TYPE,
                    event.docId(),
                    objectMapper.writeValueAsString(event)
            );
        } catch (Exception ex) {
            log.warn("SEARCH.enqueue failed: index={} id={} action={} reason={}",
                    event.index(), event.docId(), event.action(), ex.getMessage());
        }
    }
}
