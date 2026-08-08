package com.pwb.infra.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchProperties;
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
    private final ElasticsearchProperties elasticsearchProperties;
    private final ObjectMapper objectMapper;
    private final List<SearchIndexDefinition> definitions;

    @EventListener(ApplicationReadyEvent.class)
    public void createMissingIndices() {
        if (!config.isEnabled()) {
            log.info("SEARCH.bootstrap skipped: pwb.search.enabled=false");
            return;
        }
        logReachability();
        definitions.forEach(this::createIfAbsent);
    }

    /**
     * The one line a deployment can be checked against.
     *
     * <p>Everything downstream of this is designed to survive Elasticsearch being absent, which is the
     * right behaviour and also the reason the misconfiguration is invisible: search keeps answering from
     * Postgres, {@code /actuator/health} stays UP because the Elasticsearch health indicator is switched
     * off on purpose, and the only symptom is results that are ordered by nothing in particular. The most
     * likely cause is the most boring one — {@code SPRING_ELASTICSEARCH_URIS} left unset, so the backend
     * container looks for a cluster on its own localhost — so the configured value is echoed back here
     * rather than described.
     *
     * <p>Failing startup instead was considered and rejected: it would turn a degraded search box into a
     * site that does not come up at all. Logging both outcomes means the check is a grep for one string,
     * and its <em>absence</em> is as meaningful as its presence.
     *
     * <p>{@code ping} rather than {@code cluster().health()}, and reporting only rather than gating the
     * index creation below. Both of those are scars. The first attempt read the cluster status, which
     * looked like the more informative call and instead made this method report a healthy cluster as
     * unreachable: the response deserialiser is generated per client version, this project runs an 8.18
     * client against an 8.12 server, and a field the older server does not send is enough to fail the
     * decode after a perfectly good {@code 200}. {@code ping} is a HEAD whose answer is the status code,
     * so it has no schema to disagree about. The second attempt let a failed probe skip index creation
     * entirely — which turned that false negative into indices that were never created at all. A probe
     * that reports is worth having; a probe that decides is a second thing that can be wrong.
     */
    private void logReachability() {
        String uris = String.join(",", elasticsearchProperties.getUris());
        try {
            if (client.ping().value()) {
                log.info("SEARCH.startup Elasticsearch reachable: uris={}", uris);
                return;
            }
            log.error("SEARCH.startup Elasticsearch UNREACHABLE: uris={} reason=ping returned false", uris);
        } catch (Exception ex) {
            log.error("SEARCH.startup Elasticsearch UNREACHABLE: uris={} reason={} — every search will "
                            + "answer from PostgreSQL without relevance ranking, autocomplete or Vietnamese "
                            + "diacritic matching, and nothing else will report this",
                    uris, ex.getMessage());
        }
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
