package com.pwb.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SysErrorCode — error code enum")
class ErrorCodeTest {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    @Test
    @DisplayName("should_expose_category_for_each_value")
    void should_expose_category_for_each_value() {
        for (SysErrorCode code : SysErrorCode.values()) {
            assertThat(code.category())
                    .as("category for %s", code)
                    .isNotNull()
                    .isInstanceOf(ErrorCategory.class);
        }
    }

    @Test
    @DisplayName("should_have_unique_code_strings")
    void should_have_unique_code_strings() {
        Set<String> codes = new HashSet<>();
        for (SysErrorCode code : SysErrorCode.values()) {
            codes.add(code.code());
        }
        assertThat(codes).hasSize(SysErrorCode.values().length);
    }

    @Test
    @DisplayName("should_have_non_blank_default_message_for_each_value")
    void should_have_non_blank_default_message_for_each_value() {
        for (SysErrorCode code : SysErrorCode.values()) {
            assertThat(code.defaultMessage())
                    .as("defaultMessage for %s", code)
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("should_follow_uppercase_snake_case_naming_for_codes")
    void should_follow_uppercase_snake_case_naming_for_codes() {
        for (SysErrorCode code : SysErrorCode.values()) {
            assertThat(code.code())
                    .as("code format for %s", code)
                    .matches(CODE_PATTERN);
        }
    }

    @Test
    @DisplayName("should_never_return_null_code")
    void should_never_return_null_code() {
        for (SysErrorCode code : SysErrorCode.values()) {
            assertThat(code.code()).isNotNull();
        }
    }

    @Test
    @DisplayName("should_map_category_to_expected_http_status")
    void should_map_category_to_expected_http_status() {
        assertThat(SysErrorCode.FILE_TOO_LARGE.category()).isEqualTo(ErrorCategory.VALIDATION);
        assertThat(SysErrorCode.RESOURCE_NOT_FOUND.category()).isEqualTo(ErrorCategory.NOT_FOUND);
        assertThat(SysErrorCode.INTERNAL_SERVER_ERROR.category()).isEqualTo(ErrorCategory.INTERNAL);
    }

    @Test
    @DisplayName("should_contain_at_least_three_values")
    void should_contain_at_least_three_values() {
        assertThat(SysErrorCode.values()).hasSizeGreaterThanOrEqualTo(3);
    }
}