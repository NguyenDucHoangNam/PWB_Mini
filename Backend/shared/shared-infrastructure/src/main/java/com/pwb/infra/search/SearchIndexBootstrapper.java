package com.pwb.infra.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.StringReader;
import java.util.List;

/**
 * Creates every declared index on startup, pairing the module's mappings with the analyzer settings that
 * are shared cluster-wide.
 *
 * <p>Nothing here may stop the application. Elasticsearch being absent has to be survivable — every read
 * path falls back to Postgres — so a failure is logged and the boot continues. An existing index is left
 * untouched, since Elasticsearch cannot change the mapping of a live field; a mapping change is a new
 * index plus a reindex, which is a deliberate operation rather than a startup side effect.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexBootstrapper {

    static final String SHARED_SETTINGS_RESOURCE = "search/index-settings.json";

    private final ElasticsearchClient client;
    private final SearchIndexNames indexNames;
    private final SearchConfig config;
    private final ObjectMapper objectMapper;
    private final List<SearchIndexDefinition> definitions;

    @EventListener(ApplicationReadyEvent.class)
    public void createMissingIndices() {
        if (!config.isEnabled()) {
            log.info("SEARCH.bootstrap skipped: pwb.search.enabled=false");
            return;
        }
        definitions.forEach(this::createIfAbsent);
    }

    private void createIfAbsent(SearchIndexDefinition definition) {
        String index = indexNames.physical(definition.indexName());
        try {
            boolean exists = client.indices().exists(e -> e.index(index)).value();
            if (exists) {
                log.debug("SEARCH.bootstrap index already present: {}", index);
                return;
            }

            client.indices().create(new CreateIndexRequest.Builder()
                    .index(index)
                    .withJson(new StringReader(buildBody(definition).toString()))
                    .build());
            log.info("SEARCH.bootstrap created index: {}", index);
        } catch (Exception ex) {
            log.warn("SEARCH.bootstrap failed for index={} reason={} — search will fall back to the database",
                    index, ex.getMessage());
        }
    }

    private ObjectNode buildBody(SearchIndexDefinition definition) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("settings", readJson(SHARED_SETTINGS_RESOURCE));

        ObjectNode mappings = objectMapper.createObjectNode();
        mappings.set("properties", readJson(definition.mappingResource()));
        body.set("mappings", mappings);
        return body;
    }

    private JsonNode readJson(String resourcePath) throws Exception {
        try (InputStream stream = new ClassPathResource(resourcePath).getInputStream()) {
            return objectMapper.readTree(stream);
        }
    }
}
