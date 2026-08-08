package com.pwb.infra.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ElasticsearchSearchGatewayAdapter — what the caller sees when the cluster does not answer")
class ElasticsearchSearchGatewayAdapterTest {

    private ElasticsearchClient client;
    private SearchConfig config;
    private ElasticsearchSearchGatewayAdapter gateway;

    @BeforeEach
    void setUp() {
        client = mock(ElasticsearchClient.class);
        config = new SearchConfig();
        gateway = new ElasticsearchSearchGatewayAdapter(client, new SearchIndexNames(config), config);
    }

    private static SearchQuerySpec anySpec() {
        return SearchQuerySpec.ids("songs", Query.of(q -> q.matchAll(m -> m)), List.of(), 0, 20);
    }

    @Nested
    @DisplayName("when the cluster is unreachable")
    class Unreachable {

        @Test
        @DisplayName("should_answer_empty_rather_than_throw_so_the_caller_can_fall_back")
        void should_answer_empty_rather_than_throw() throws IOException {
            when(client.search(any(SearchRequest.class), any(Class.class)))
                    .thenThrow(new IOException("connection refused"));

            Optional<SearchPage<String>> result = gateway.searchIds(anySpec());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should_answer_empty_from_fetch_as_well")
        void should_answer_empty_from_fetch() throws IOException {
            when(client.search(any(SearchRequest.class), any(Class.class)))
                    .thenThrow(new RuntimeException("socket timeout"));

            Optional<List<String>> result = gateway.fetch(anySpec(), String.class);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("when search is switched off entirely")
    class Disabled {

        @Test
        @DisplayName("should_not_touch_the_cluster_at_all")
        void should_not_touch_the_cluster() {
            config.setEnabled(false);

            assertThat(gateway.searchIds(anySpec())).isEmpty();
            assertThat(gateway.fetch(anySpec(), String.class)).isEmpty();
            verifyNoInteractions(client);
        }
    }
}
