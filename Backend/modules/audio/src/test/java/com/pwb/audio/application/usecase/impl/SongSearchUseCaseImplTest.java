package com.pwb.audio.application.usecase.impl;

import com.pwb.audio.application.support.SongViewFactory;
import com.pwb.audio.application.view.SongSuggestionView;
import com.pwb.audio.application.view.SongView;
import com.pwb.audio.domain.enums.SongStatus;
import com.pwb.audio.domain.model.Song;
import com.pwb.audio.domain.repository.SongRepository;
import com.pwb.audio.domain.repository.SongSearchCriteria;
import com.pwb.audio.domain.repository.SongTagConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SongSearchUseCaseImpl — searching the song library in Postgres")
class SongSearchUseCaseImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Pageable PAGE = PageRequest.of(0, 20);

    private SongRepository songRepository;
    private SongSearchUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        songRepository = mock(SongRepository.class);

        SongTagConfigRepository tagConfigRepository = mock(SongTagConfigRepository.class);
        when(tagConfigRepository.findAllBySongIdIn(anyCollection())).thenReturn(List.of());

        useCase = new SongSearchUseCaseImpl(songRepository, new SongViewFactory(tagConfigRepository));
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
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("should_return_the_rows_the_repository_matched")
        void should_return_matched_rows() {
            when(songRepository.search(any(), any()))
                    .thenReturn(new PageImpl<>(List.of(song(UUID.randomUUID(), "from postgres")), PAGE, 1));

            Page<SongView> result = useCase.search(criteria("nhac"), PAGE);

            assertThat(result.getContent()).extracting(SongView::title).containsExactly("from postgres");
            assertThat(result.getTotalElements()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should_keep_the_total_from_the_repository_not_the_page_size")
        void should_keep_the_repository_total() {
            when(songRepository.search(any(), any()))
                    .thenReturn(new PageImpl<>(List.of(song(UUID.randomUUID(), "one")), PAGE, 137));

            Page<SongView> result = useCase.search(criteria("nhac"), PAGE);

            assertThat(result.getTotalElements()).isEqualTo(137L);
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
            verify(songRepository, never()).search(any(), any());
        }

        @Test
        @DisplayName("should_return_a_title_per_match")
        void should_return_a_title_per_match() {
            when(songRepository.search(any(), any()))
                    .thenReturn(new PageImpl<>(List.of(song(UUID.randomUUID(), "from postgres"))));

            List<SongSuggestionView> result = useCase.suggest(criteria("nh"), 8);

            assertThat(result).extracting(SongSuggestionView::title).containsExactly("from postgres");
        }

        @Test
        @DisplayName("should_cap_an_oversized_limit_rather_than_pass_it_through")
        void should_cap_an_oversized_limit() {
            when(songRepository.search(any(), any())).thenReturn(new PageImpl<>(List.of()));

            useCase.suggest(criteria("nh"), 5000);

            var pageable = forClass(Pageable.class);
            verify(songRepository).search(any(), pageable.capture());
            assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        }
    }
}
