package com.pwb.web.exception;

import com.pwb.shared.dto.ApiResponse;
import com.pwb.shared.exception.BusinessException;
import com.pwb.shared.exception.ErrorCategory;
import com.pwb.shared.exception.ErrorCode;
import com.pwb.shared.exception.SysErrorCode;
import com.pwb.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GlobalExceptionHandler — exception to ApiResponse mapping")
class GlobalExceptionHandlerTest {

    private MessageSource messageSource;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        messageSource = mock(MessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(2));
        handler = new GlobalExceptionHandler(messageSource);
    }

    @Test
    @DisplayName("should_return_500_when_internal_server_error_business_exception")
    void should_return_500_when_internal_server_error_business_exception() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusiness(new BusinessException(SysErrorCode.INTERNAL_SERVER_ERROR));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    @DisplayName("should_map_validation_category_to_bad_request")
    void should_map_validation_category_to_bad_request() {
        BusinessException ex = new BusinessException(testCode(ErrorCategory.VALIDATION));

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("should_map_unauthorized_category_to_401")
    void should_map_unauthorized_category_to_401() {
        BusinessException ex = new BusinessException(testCode(ErrorCategory.UNAUTHORIZED));

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("should_map_forbidden_category_to_403")
    void should_map_forbidden_category_to_403() {
        BusinessException ex = new BusinessException(testCode(ErrorCategory.FORBIDDEN));

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("should_map_not_found_category_to_404")
    void should_map_not_found_category_to_404() {
        BusinessException ex = new BusinessException(testCode(ErrorCategory.NOT_FOUND));

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("should_map_conflict_category_to_409")
    void should_map_conflict_category_to_409() {
        BusinessException ex = new BusinessException(testCode(ErrorCategory.CONFLICT));

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("should_map_too_many_requests_category_to_429")
    void should_map_too_many_requests_category_to_429() {
        BusinessException ex = new BusinessException(testCode(ErrorCategory.TOO_MANY_REQUESTS));

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("should_map_business_category_to_422")
    void should_map_business_category_to_422() {
        BusinessException ex = new BusinessException(testCode(ErrorCategory.BUSINESS));

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("should_return_400_for_validation_exception")
    void should_return_400_for_validation_exception() {
        ValidationException ex = new ValidationException("email", "must not be blank");

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("should_carry_field_errors_in_validation_response")
    void should_carry_field_errors_in_validation_response() {
        Map<String, List<String>> fieldErrors = Map.of("email", List.of("must not be blank"));
        ValidationException ex = new ValidationException(fieldErrors);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isSameAs(fieldErrors);
    }

    @Test
    @DisplayName("should_fall_back_to_default_message_when_message_source_returns_default")
    void should_fall_back_to_default_message_when_message_source_returns_default() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusiness(new BusinessException(SysErrorCode.RESOURCE_NOT_FOUND));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo(SysErrorCode.RESOURCE_NOT_FOUND.defaultMessage());
    }

    @Test
    @DisplayName("should_use_default_message_when_message_source_throws")
    void should_use_default_message_when_message_source_throws() {
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenThrow(new RuntimeException("message source failure"));

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusiness(new BusinessException(SysErrorCode.RESOURCE_NOT_FOUND));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo(SysErrorCode.RESOURCE_NOT_FOUND.defaultMessage());
    }

    @Test
    @DisplayName("should_carry_trace_id_in_error_response")
    void should_carry_trace_id_in_error_response() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusiness(new BusinessException(SysErrorCode.RESOURCE_NOT_FOUND));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTraceId()).isNotBlank();
    }

    @Test
    @DisplayName("should_carry_timestamp_in_error_response")
    void should_carry_timestamp_in_error_response() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusiness(new BusinessException(SysErrorCode.RESOURCE_NOT_FOUND));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTimestamp()).isNotNull().isPositive();
    }

    @Test
    @DisplayName("should_return_500_for_generic_exception")
    void should_return_500_for_generic_exception() {
        Exception generic = new RuntimeException("boom");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneric(generic);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    @DisplayName("should_not_leak_internal_exception_message_in_500_response")
    void should_not_leak_internal_exception_message_in_500_response() {
        Exception generic = new RuntimeException("sensitive internal stacktrace info");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneric(generic);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .doesNotContain("sensitive internal stacktrace info");
    }

    @Test
    @DisplayName("should_return_400_for_max_upload_size_exceeded_exception")
    void should_return_400_for_max_upload_size_exceeded_exception() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(1024L * 1024L);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMaxUpload(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    @DisplayName("should_return_404_for_no_resource_found_exception")
    void should_return_404_for_no_resource_found_exception() {
        NoResourceFoundException ex =
                new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "missing/path");

        ResponseEntity<ApiResponse<Void>> response = handler.handleNoResource(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    private static ErrorCode testCode(ErrorCategory category) {
        return new ErrorCode() {
            @Override public String code() { return category.name(); }
            @Override public String defaultMessage() { return category.name(); }
            @Override public ErrorCategory category() { return category; }
        };
    }
}