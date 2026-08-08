package com.pwb.iam.api.exception;

import com.pwb.shared.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IamExceptionHandlerTest {

    @Mock private MessageSource messageSource;

    private IamExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new IamExceptionHandler(messageSource);
        // The overload that carries a default, which is the only one the handler uses — a missing
        // translation must not turn a 409 into a 500.
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenReturn("DATA_INTEGRITY_VIOLATION");
    }

    @Test
    @DisplayName("should return 409 with field=email when constraint mentions email")
    void should_extract_email_field_from_constraint() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"uk_iam_users_email\"");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo("CONFLICT");
        assertThat((Map<String, Object>) response.getBody().getError()).containsEntry("field", "email");
    }

    @Test
    @DisplayName("should return 409 with field=unknown when constraint cannot be extracted")
    void should_return_unknown_field_when_no_constraint() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("some random error");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((Map<String, Object>) response.getBody().getError()).containsEntry("field", "unknown");
    }

    @Test
    @DisplayName("should map a phone constraint to the request field, not the constraint name")
    void should_return_constraint_name_as_field() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "constraint [uk_iam_users_phone] violated");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(ex);

        // The client is told which field of the body collided, in the name it sent. The constraint
        // identifier stays server-side: it is an internal schema detail and naming it back at an
        // anonymous caller describes the table layout for free.
        assertThat((Map<String, Object>) response.getBody().getError()).containsEntry("field", "phone");
    }

    @Test
    @DisplayName("should return field=unknown when message is null")
    void should_handle_null_message() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException((String) null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((Map<String, Object>) response.getBody().getError()).containsEntry("field", "unknown");
    }
}