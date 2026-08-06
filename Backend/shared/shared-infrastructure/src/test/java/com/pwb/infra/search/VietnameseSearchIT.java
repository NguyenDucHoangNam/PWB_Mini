package com.pwb.infra.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the shared analyzer settings against a real cluster.
 *
 * <p>The claim being tested is the one the whole search feature rests on for Vietnamese users: typing
 * without diacritics has to find text that has them. That behaviour lives entirely in the index settings,
 * not in Java, so nothing short of a real Elasticsearch can confirm it.
 */
@Testcontainers
@DisplayName("Vietnamese search — diacritics, typos and search-as-you-type against a real cluster")
class VietnameseSearchIT {

    private static final String INDEX = "pwb_it_songs";
    private static final String USER_A = "11111111-1111-1111-1111-111111111111";
    private static final String USER_B = "22222222-2222-2222-2222-222222222222";

    @Container
    static final ElasticsearchContainer ES = new ElasticsearchContainer(
            DockerImageName.parse("elasticsearch:8.12.0"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    static ElasticsearchClient client;
    static SearchGateway gateway;

    @BeforeAll
    static void setUp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RestClient restClient = RestClient
                .builder(HttpHost.create("http://" + ES.getHttpHostAddress()))
                .build();
        client = new ElasticsearchClient(
                new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper)));

        SearchConfig config = new SearchConfig();
        config.setIndexPrefix("");
        gateway = new ElasticsearchSearchGatewayAdapter(client, new SearchIndexNames(config), config);

        createIndex(objectMapper);
        indexSongs();
    }

    private static void createIndex(ObjectMapper objectMapper) throws IOException {
        String settings = new String(VietnameseSearchIT.class.getClassLoader()
                .getResourceAsStream("search/index-settings.json").readAllBytes());
        String mapping = """
                {
                  "id": { "type": "keyword" },
                  "userId": { "type": "keyword" },
                  "title": {
                    "type": "text",
                    "analyzer": "pwb_text",
                    "fields": {
                      "autocomplete": {
                        "type": "text",
                        "analyzer": "pwb_autocomplete",
                        "search_analyzer": "pwb_text"
                      }
                    }
                  }
                }
                """;
        String body = "{\"settings\":" + settings + ",\"mappings\":{\"properties\":" + mapping + "}}";
        client.indices().create(c -> c.index(INDEX).withJson(new java.io.StringReader(body)));
        assertThat(objectMapper).isNotNull();
    }

    private static void indexSongs() throws IOException {
        index("song-1", USER_A, "Em ơi Hà Nội phố");
        index("song-2", USER_A, "Bản remix nhạc trẻ");
        index("song-3", USER_B, "Nhật ký của mẹ");
        client.indices().refresh(r -> r.index(INDEX));
    }

    private static void index(String id, String userId, String title) throws IOException {
        client.index(i -> i
                .index(INDEX)
                .id(id)
                .document(java.util.Map.of("id", id, "userId", userId, "title", title)));
    }

    private List<String> search(String userId, String keyword) {
        Query query = Query.of(q -> q.bool(new BoolQuery.Builder()
                .filter(SearchQueries.ownedBy("userId", userId))
                .must(SearchQueries.keywordMatch("title", keyword))
                .build()));

        Optional<SearchPage<String>> page = gateway.searchIds(
                SearchQuerySpec.ids(INDEX, query, List.of(), 0, 10));
        return page.orElseThrow().items();
    }

    private List<String> suggest(String userId, String prefix) {
        Query query = Query.of(q -> q.bool(new BoolQuery.Builder()
                .filter(SearchQueries.ownedBy("userId", userId))
                .must(SearchQueries.autocomplete("title", prefix))
                .build()));

        Optional<SearchPage<String>> page = gateway.searchIds(
                SearchQuerySpec.ids(INDEX, query, List.of(), 0, 10));
        return page.orElseThrow().items();
    }

    @Test
    @DisplayName("should_find_accented_title_when_typed_without_diacritics")
    void should_find_accented_title_without_diacritics() {
        assertThat(search(USER_A, "ha noi")).contains("song-1");
    }

    @Test
    @DisplayName("should_match_a_single_word_stripped_of_its_tone_mark")
    void should_match_single_word_without_tone() {
        assertThat(search(USER_A, "nhac")).contains("song-2");
    }

    @Test
    @DisplayName("should_still_find_the_title_when_typed_with_its_diacritics")
    void should_find_with_diacritics() {
        assertThat(search(USER_A, "Hà Nội")).contains("song-1");
    }

    @Test
    @DisplayName("should_tolerate_a_typo")
    void should_tolerate_a_typo() {
        assertThat(search(USER_A, "remixx")).contains("song-2");
    }

    @Test
    @DisplayName("should_suggest_from_a_two_character_prefix")
    void should_suggest_from_two_characters() {
        assertThat(suggest(USER_A, "re")).contains("song-2");
    }

    /**
     * The owner filter is what stands between two users' libraries. If this ever fails, search is leaking
     * data — no ranking or analyzer question matters next to it.
     */
    @Test
    @DisplayName("should_never_return_another_users_song")
    void should_never_return_another_users_song() {
        assertThat(search(USER_A, "nhat ky")).doesNotContain("song-3");
        assertThat(search(USER_B, "nhat ky")).contains("song-3");
        assertThat(search(USER_B, "ha noi")).doesNotContain("song-1");
    }
}
