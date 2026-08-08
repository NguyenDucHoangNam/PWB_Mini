package com.pwb.infra.search.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.infra.search.SearchIndexEvent;
import com.pwb.infra.search.SearchIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "pwb.search.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SearchIndexKafkaConsumer {

    private final SearchIndexer indexer;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topics.search-index:search.index.v1}",
            groupId = "${pwb.search.consumer.group:pwb-search-indexer}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onIndexEvent(ConsumerRecord<String, String> record) {
        SearchIndexEvent event = parse(record);
        indexer.apply(event);
    }

    /**
     * A payload that cannot be parsed will never parse on a retry, so it is raised as a non-retryable
     * failure and routed straight to the dead-letter topic.
     */
    private SearchIndexEvent parse(ConsumerRecord<String, String> record) {
        try {
            return objectMapper.readValue(record.value(), SearchIndexEvent.class);
        } catch (Exception ex) {
            log.warn("SEARCH.payload parse failed: key={} reason={}", record.key(), ex.getMessage());
            throw new SearchIndexPayloadException("Search index payload parse failed: " + ex.getMessage(), ex);
        }
    }
}
