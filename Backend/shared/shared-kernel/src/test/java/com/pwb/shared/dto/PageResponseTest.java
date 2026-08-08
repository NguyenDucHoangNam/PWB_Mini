package com.pwb.shared.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageResponse — paginated response wrapper")
class PageResponseTest {

    @Test
    @DisplayName("should_compute_total_pages_when_total_evenly_divisible")
    void should_compute_total_pages_when_total_evenly_divisible() {
        PageResponse<String> response = PageResponse.of(List.of("a", "b"), 0, 10, 100L);

        assertThat(response.getTotalPages()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(100L);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getPage()).isZero();
        assertThat(response.isFirst()).isTrue();
        assertThat(response.isLast()).isFalse();
    }

    @Test
    @DisplayName("should_compute_total_pages_with_remainder")
    void should_compute_total_pages_with_remainder() {
        PageResponse<String> response = PageResponse.of(List.of(), 0, 10, 101L);

        assertThat(response.getTotalPages()).isEqualTo(11);
        assertThat(response.isLast()).isFalse();
    }

    @Test
    @DisplayName("should_return_zero_pages_when_empty")
    void should_return_zero_pages_when_empty() {
        PageResponse<String> response = PageResponse.of(List.of(), 0, 10, 0L);

        assertThat(response.getTotalPages()).isZero();
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    @DisplayName("should_treat_first_page_when_page_zero")
    void should_treat_first_page_when_page_zero() {
        PageResponse<String> response = PageResponse.of(List.of("a"), 0, 10, 100L);

        assertThat(response.isFirst()).isTrue();
        assertThat(response.isLast()).isFalse();
    }

    @Test
    @DisplayName("should_mark_last_true_when_page_equals_last_index")
    void should_mark_last_true_when_page_equals_last_index() {
        PageResponse<String> response = PageResponse.of(List.of(), 9, 10, 100L);

        assertThat(response.isLast()).isTrue();
        assertThat(response.isFirst()).isFalse();
    }

    @Test
    @DisplayName("should_use_empty_content_when_page_out_of_range")
    void should_use_empty_content_when_page_out_of_range() {
        PageResponse<String> response = PageResponse.of(List.of(), 99, 10, 100L);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.isFirst()).isFalse();
        assertThat(response.isLast()).isTrue();
    }

    @Test
    @DisplayName("should_keep_inconsistent_size_and_content_length")
    void should_keep_inconsistent_size_and_content_length() {
        List<String> content = List.of("only-one");
        PageResponse<String> response = PageResponse.of(content, 0, 50, 1L);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getSize()).isEqualTo(50);
        assertThat(response.getTotalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should_set_total_pages_to_zero_when_size_zero")
    void should_set_total_pages_to_zero_when_size_zero() {
        PageResponse<String> response = PageResponse.of(List.of(), 0, 0, 100L);

        assertThat(response.getTotalPages()).isZero();
    }

    @Test
    @DisplayName("should_create_with_no_args_constructor_for_deserialization")
    void should_create_with_no_args_constructor_for_deserialization() {
        PageResponse<String> response = new PageResponse<>();

        assertThat(response.getContent()).isNull();
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isZero();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
    }
}