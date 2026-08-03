package com.pwb.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BusinessException — domain exception")
class BusinessExceptionTest {

    @Test
    @DisplayName("should_carry_error_code_in_message_when_constructed_with_code_only")
    void should_carry_error_code_in_message_when_constructed_with_code_only() {
        BusinessException ex = new BusinessException(SysErrorCode.RESOURCE_NOT_FOUND);

        assertThat(ex.getErrorCode()).isSameAs(SysErrorCode.RESOURCE_NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo(SysErrorCode.RESOURCE_NOT_FOUND.defaultMessage());
    }

    @Test
    @DisplayName("should_use_error_code_default_message_when_no_override_provided")
    void should_use_error_code_default_message_when_no_override_provided() {
        BusinessException ex = new BusinessException(SysErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(ex.getMessage()).isEqualTo("An unexpected error occurred");
    }

    @Test
    @DisplayName("should_use_override_message_when_provided")
    void should_use_override_message_when_provided() {
        BusinessException ex = new BusinessException(
                SysErrorCode.RESOURCE_NOT_FOUND, "User not found: 42");

        assertThat(ex.getMessage()).isEqualTo("User not found: 42");
        assertThat(ex.getErrorCode()).isSameAs(SysErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("should_carry_cause_when_constructed_with_throwable")
    void should_carry_cause_when_constructed_with_throwable() {
        IllegalStateException cause = new IllegalStateException("underlying failure");
        BusinessException ex = new BusinessException(SysErrorCode.INTERNAL_SERVER_ERROR, cause);

        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("should_have_empty_details_when_no_metadata_provided")
    void should_have_empty_details_when_no_metadata_provided() {
        BusinessException ex = new BusinessException(SysErrorCode.RESOURCE_NOT_FOUND);

        assertThat(ex.getDetails()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("should_carry_details_when_metadata_provided")
    void should_carry_details_when_metadata_provided() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("field", "email");
        details.put("attempt", 3);

        BusinessException ex = new BusinessException(SysErrorCode.INTERNAL_SERVER_ERROR, details);

        assertThat(ex.getDetails())
                .containsEntry("field", "email")
                .containsEntry("attempt", 3)
                .hasSize(2);
    }

    @Test
    @DisplayName("should_use_empty_map_when_metadata_is_null")
    void should_use_empty_map_when_metadata_is_null() {
        BusinessException ex = new BusinessException(SysErrorCode.INTERNAL_SERVER_ERROR, (Map<String, Object>) null);

        assertThat(ex.getDetails()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("should_preserve_insertion_order_in_details_map")
    void should_preserve_insertion_order_in_details_map() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("first", 1);
        details.put("second", 2);
        details.put("third", 3);

        BusinessException ex = new BusinessException(SysErrorCode.INTERNAL_SERVER_ERROR, details);

        assertThat(ex.getDetails().keySet())
                .containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("should_expose_category_from_error_code")
    void should_expose_category_from_error_code() {
        BusinessException notFound = new BusinessException(SysErrorCode.RESOURCE_NOT_FOUND);
        BusinessException validation = new BusinessException(SysErrorCode.FILE_TOO_LARGE);

        assertThat(notFound.getCategory()).isEqualTo(ErrorCategory.NOT_FOUND);
        assertThat(validation.getCategory()).isEqualTo(ErrorCategory.VALIDATION);
    }

    @Test
    @DisplayName("should_throw_npe_when_error_code_null")
    void should_throw_npe_when_error_code_null() {
        assertThatThrownBy(() -> new BusinessException(null))
                .isInstanceOf(NullPointerException.class);
    }
}