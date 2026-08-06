package com.pwb.audio.application.usecase.impl;

import com.pwb.audio.application.support.SongViewFactory;
import com.pwb.audio.application.view.SongSuggestionView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.domain.enums.SongStatus;
import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.repository.SongRepository;
import com.pwb.audio.domain.repository.SongSearchCriteria;
import com.pwb.audio.domain.repository.SongTagConfigRepository;
import com.pwb.audio.domain.service.SearchHitIds;
import com.pwb.audio.domain.service.SongSearchPort;
import com.pwb.audio.domain.service.SongSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SongSearchUseCaseImpl — ranking from the engine, rows from the database")
class SongSearchUseCaseImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Pageable PAGE = PageRequest.of(0, 20);

    private SongSearchPort searchPort;
    private SongRepository songRepository;
    private SongSearchUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        searchPort = mock(SongSearchPort.class);
        songRepository = mock(SongRepository.class);

        SongTagConfigRepository tagConfigRepository = mock(SongTagConfigRepository.class);
        when(tagConfigRepository.findAllBySongIdIn(anyCollection())).thenReturn(List.of());

        useCase = new SongSearchUseCaseImpl(
                searchPort, songRepository, new SongViewFactory(tagConfigRepository));
    }

    private static SongSearchCriteria criteria(String keyword) {
        return new SongSearchCriteria(USER_ID, keyword, List.of(), null, null, null);
    }

    private static Song song(UUID id, String title) {
        return Song.rehydrate(
                id, USER_ID, title, null, null, "audio/originals/x.mp3", null,
                1000L, 120, "mp3", SongStatus.PROCESSED, null, null);
    }

    @Nested
    @DisplayName("when the engine answers")
    class EngineAnswers {

        @Test
        @DisplayName("should_return_rows_in_the_order_the_engine_ranked_them")
        void should_preserve_relevance_order() {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            when(searchPort.search(any(), any(Integer.class), any(Integer.class)))
                    .thenReturn(Optional.of(new SearchHitIds(List.of(first, second), 2L)));
            // The database is free to answer in any order; the ranking must survive it.
            when(songRepository.findAllByIdIn(anyCollection()))
                    .thenReturn(List.of(song(second, "second"), song(first, "first")));

            Page<SongView> result = useCase.search(criteria("nhac"), PAGE);

            assertThat(result.getContent()).extracting(SongView::title)
                    .containsExactly("first", "second");
            assertThat(result.getTotalElements()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should_drop_a_hit_whose_row_has_already_been_deleted")
        void should_drop_a_hit_with_no_row() {
            UUID present = UUID.randomUUID();
            UUID vanished = UUID.randomUUID();
            when(searchPort.search(any(), any(Integer.class), any(Integer.class)))
                    .thenReturn(Optional.of(new SearchHitIds(List.of(vanished, present), 2L)));
            when(songRepository.findAllByIdIn(anyCollection()))
                    .thenReturn(List.of(song(present, "still here")));

            Page<SongView> result = useCase.search(criteria("nhac"), PAGE);

            assertThat(result.getContent()).extracting(SongView::title).containsExactly("still here");
        }

        @Test
        @DisplayName("should_not_run_the_database_search_at_all")
        void should_not_run_the_database_search() {
            when(searchPort.search(any(), any(Integer.class), any(Integer.class)))
                    .thenReturn(Optional.of(new SearchHitIds(List.of(), 0L)));
            when(songRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());

            useCase.search(criteria("nhac"), PAGE);

            verify(songRepository, never()).search(any(), any());
        }
    }

    @Nested
    @DisplayName("when the engine is unavailable")
    class EngineUnavailable {

        @Test
        @DisplayName("should_fall_back_to_the_database_instead_of_returning_nothing")
        void should_fall_back_to_the_database() {
            when(searchPort.search(any(), any(Integer.class), any(Integer.class)))
                    .thenReturn(Optional.empty());
            when(songRepository.search(any(), any()))
                    .thenReturn(new PageImpl<>(List.of(song(UUID.randomUUID(), "from postgres")), PAGE, 1));

            Page<SongView> result = useCase.search(criteria("nhac"), PAGE);

            assertThat(result.getContent()).extracting(SongView::title).containsExactly("from postgres");
            verify(songRepository).search(any(), any());
        }

        @Test
        @DisplayName("should_fall_back_for_suggestions_too")
        void should_fall_back_for_suggestions() {
            when(searchPort.suggest(any(), any(Integer.class))).thenReturn(Optional.empty());
            when(songRepository.search(any(), any()))
                    .thenReturn(new PageImpl<>(List.of(song(UUID.randomUUID(), "from postgres"))));

            List<SongSuggestionView> result = useCase.suggest(criteria("nh"), 8);

            assertThat(result).extracting(SongSuggestionView::title).containsExactly("from postgres");
        }
    }

    @Nested
    @DisplayName("suggestions")
    class Suggestions {

        @Test
        @DisplayName("should_answer_nothing_without_asking_when_the_keyword_is_blank")
        void should_answer_nothing_when_keyword_blank() {
            List<SongSuggestionView> result = useCase.suggest(criteria("  "), 8);

            assertThat(result).isEmpty();
            verify(searchPort, never()).suggest(any(), any(Integer.class));
        }

        @Test
        @DisplayName("should_cap_an_oversized_limit_rather_than_pass_it_through")
        void should_cap_an_oversized_limit() {
            when(searchPort.suggest(any(), any(Integer.class)))
                    .thenReturn(Optional.of(List.of(new SongSuggestion(UUID.randomUUID(), "a"))));

            useCase.suggest(criteria("nh"), 5000);

            verify(searchPort).suggest(any(), org.mockito.ArgumentMatchers.eq(20));
        }
    }
}
