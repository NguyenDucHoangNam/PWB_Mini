package com.pwb.web.exception;

import com.pwb.shared.exception.ErrorCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebErrorMapper — error category to HTTP status")
class WebErrorMapperTest {

    @Test
    @DisplayName("should_map_validation_to_bad_request")
    void should_map_validation_to_bad_request() {
        assertThat(WebErrorMapper.toHttpStatus(ErrorCategory.VALIDATION))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("should_map_unauthorized_to_unauthorized")
    void should_map_unauthorized_to_unauthorized() {
        assertThat(WebErrorMapper.toHttpStatus(ErrorCategory.UNAUTHORIZED))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("should_map_forbidden_to_forbidden")
    void should_map_forbidden_to_forbidden() {
        assertThat(WebErrorMapper.toHttpStatus(ErrorCategory.FORBIDDEN))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("should_map_not_found_to_not_found")
    void should_map_not_found_to_not_found() {
        assertThat(WebErrorMapper.toHttpStatus(ErrorCategory.NOT_FOUND))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("should_map_conflict_to_conflict")
    void should_map_conflict_to_conflict() {
        assertThat(WebErrorMapper.toHttpStatus(ErrorCategory.CONFLICT))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("should_map_too_many_requests_to_too_many_requests")
    void should_map_too_many_requests_to_too_many_requests() {
        assertThat(WebErrorMapper.toHttpStatus(ErrorCategory.TOO_MANY_REQUESTS))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("should_map_business_to_unprocessable_entity")
    void should_map_business_to_unprocessable_entity() {
        assertThat(WebErrorMapper.toHttpStatus(ErrorCategory.BUSINESS))
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("should_map_internal_to_internal_server_error")
    void should_map_internal_to_internal_server_error() {
        assertThat(WebErrorMapper.toHttpStatus(ErrorCategory.INTERNAL))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("should_return_internal_server_error_when_category_null")
    void should_return_internal_server_error_when_category_null() {
        assertThat(WebErrorMapper.toHttpStatus(null))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("should_not_allow_instantiation")
    void should_not_allow_instantiation() throws Exception {
        java.lang.reflect.Constructor<WebErrorMapper> constructor =
                WebErrorMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThat(constructor.getModifiers())
                .matches(m -> (m & java.lang.reflect.Modifier.PRIVATE) != 0);
    }
}