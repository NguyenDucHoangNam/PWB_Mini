package com.pwb.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ValidationException — bean validation exception")
class ValidationExceptionTest {

    @Test
    @DisplayName("should_carry_field_errors")
    void should_carry_field_errors() {
        Map<String, List<String>> errors = Map.of(
                "email", List.of("must not be blank", "must be valid"),
                "password", List.of("must be at least 12 characters"));

        ValidationException ex = new ValidationException(errors);

        assertThat(ex.getFieldErrors())
                .containsKeys("email", "password")
                .containsEntry("email", List.of("must not be blank", "must be valid"));
    }

    @Test
    @DisplayName("should_use_validation_failed_as_message")
    void should_use_validation_failed_as_message() {
        ValidationException ex = new ValidationException("email", "must not be blank");

        assertThat(ex.getMessage()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("should_build_field_error_from_single_field_and_message")
    void should_build_field_error_from_single_field_and_message() {
        ValidationException ex = new ValidationException("fullName", "must not be blank");

        assertThat(ex.getFieldErrors())
                .containsEntry("fullName", List.of("must not be blank"));
    }

    @Test
    @DisplayName("should_have_empty_field_errors_when_constructed_with_empty_map")
    void should_have_empty_field_errors_when_constructed_with_empty_map() {
        ValidationException ex = new ValidationException(Map.of());

        assertThat(ex.getFieldErrors()).isEmpty();
    }

    @Test
    @DisplayName("should_keep_all_values_when_multiple_fields_have_multiple_errors")
    void should_keep_all_values_when_multiple_fields_have_multiple_errors() {
        Map<String, List<String>> errors = Map.of(
                "email", List.of("err1", "err2", "err3"),
                "password", List.of("err4"));

        ValidationException ex = new ValidationException(errors);

        assertThat(ex.getFieldErrors().get("email")).hasSize(3);
        assertThat(ex.getFieldErrors().get("password")).hasSize(1);
    }
}