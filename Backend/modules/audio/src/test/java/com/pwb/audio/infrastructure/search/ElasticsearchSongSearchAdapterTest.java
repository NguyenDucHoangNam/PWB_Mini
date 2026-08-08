package com.pwb.audio.infrastructure.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.pwb.audio.domain.enums.SongStatus;
import com.pwb.audio.domain.repository.SongSearchCriteria;
import com.pwb.audio.domain.service.SearchHitIds;
import com.pwb.infra.search.SearchGateway;
import com.pwb.infra.search.SearchPage;
import com.pwb.infra.search.SearchQuerySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ElasticsearchSongSearchAdapter — how a song search reaches the cluster")
class ElasticsearchSongSearchAdapterTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private SearchGateway gateway;
    private ElasticsearchSongSearchAdapter adapter;

    @BeforeEach
    void setUp() {
        gateway = mock(SearchGateway.class);
        adapter = new ElasticsearchSongSearchAdapter(gateway);
    }

    private SongSearchCriteria criteria(String keyword, SongStatus... statuses) {
        return new SongSearchCriteria(USER_ID, keyword, List.of(statuses), null, null, null);
    }

    private SearchQuerySpec captureSearchSpec(SongSearchCriteria criteria) {
        when(gateway.searchIds(any())).thenReturn(Optional.of(new SearchPage<>(List.of(), 0L)));
        adapter.search(criteria, 0, 20);

        ArgumentCaptor<SearchQuerySpec> captor = ArgumentCaptor.forClass(SearchQuerySpec.class);
        verify(gateway).searchIds(captor.capture());
        return captor.getValue();
    }

    private static List<Query> filtersOf(SearchQuerySpec spec) {
        return spec.query().bool().filter();
    }

    /**
     * Elasticsearch knows nothing about who is asking. The owner term in the filter clause is the only
     * thing keeping one user's library out of another's results, so it is asserted on its own rather than
     * folded into a broader query-shape test.
     */
    @Nested
    @DisplayName("owner scoping — the security boundary")
    class OwnerScoping {

        @Test
        @DisplayName("should_always_filter_by_the_calling_user")
        void should_always_filter_by_the_calling_user() {
            SearchQuerySpec spec = captureSearchSpec(criteria("remix"));

            assertThat(filtersOf(spec))
                    .filteredOn(Query::isTerm)
                    .anySatisfy(filter -> {
                        assertThat(filter.term().field()).isEqualTo("userId");
                        assertThat(filter.term().value().stringValue()).isEqualTo(USER_ID.toString());
                    });
        }

        @Test
        @DisplayName("should_filter_by_the_calling_user_even_with_no_keyword")
        void should_filter_by_the_calling_user_with_no_keyword() {
            SearchQuerySpec spec = captureSearchSpec(criteria(null));

            assertThat(filtersOf(spec))
                    .filteredOn(Query::isTerm)
                    .anySatisfy(filter -> assertThat(filter.term().field()).isEqualTo("userId"));
        }

        @Test
        @DisplayName("should_filter_by_the_calling_user_on_a_suggest_request_too")
        void should_filter_by_the_calling_user_on_suggest() {
            when(gateway.fetch(any(), any())).thenReturn(Optional.of(List.of()));
            adapter.suggest(criteria("re"), 8);

            ArgumentCaptor<SearchQuerySpec> captor = ArgumentCaptor.forClass(SearchQuerySpec.class);
            verify(gateway).fetch(captor.capture(), any());

            assertThat(filtersOf(captor.getValue()))
                    .anySatisfy(filter -> assertThat(filter.term().field()).isEqualTo("userId"));
        }
    }

    @Nested
    @DisplayName("narrowing filters")
    class Filters {

        @Test
        @DisplayName("should_not_score_the_status_filter")
        void should_put_status_in_the_filter_clause() {
            SearchQuerySpec spec = captureSearchSpec(
                    criteria("remix", SongStatus.UPLOADED, SongStatus.PROCESSED));

            assertThat(filtersOf(spec))
                    .filteredOn(Query::isTerms)
                    .anySatisfy(filter -> assertThat(filter.terms().field()).isEqualTo("status"));
        }

        @Test
        @DisplayName("should_leave_out_the_text_match_when_no_keyword_was_given")
        void should_leave_out_the_text_match_when_no_keyword() {
            SearchQuerySpec spec = captureSearchSpec(criteria("   "));

            assertThat(spec.query().bool().must()).isEmpty();
        }
    }

    @Nested
    @DisplayName("when the cluster does not answer")
    class Unavailable {

        @Test
        @DisplayName("should_pass_the_empty_answer_through_so_the_use_case_falls_back")
        void should_pass_the_empty_answer_through() {
            when(gateway.searchIds(any())).thenReturn(Optional.empty());

            Optional<SearchHitIds> result = adapter.search(criteria("remix"), 0, 20);

            assertThat(result).isEmpty();
        }
    }
}
