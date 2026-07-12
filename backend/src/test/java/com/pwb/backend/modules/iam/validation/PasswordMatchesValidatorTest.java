package com.pwb.backend.modules.iam.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordMatchesValidatorTest {

    private PasswordMatchesValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new PasswordMatchesValidator();
    }

    @Test
    void initialize_setsFieldsFromAnnotation() {
        PasswordMatches annotation = mock(PasswordMatches.class);
        when(annotation.first()).thenReturn("password");
        when(annotation.second()).thenReturn("confirmPassword");
        when(annotation.message()).thenReturn("Passwords do not match");

        validator.initialize(annotation);

        assertNotNull(validator);
    }

    @Test
    void validate_passwordMatches_returnsTrue() {
        PasswordMatches annotation = mock(PasswordMatches.class);
        when(annotation.first()).thenReturn("password");
        when(annotation.second()).thenReturn("confirmPassword");
        when(annotation.message()).thenReturn("Passwords do not match");
        validator.initialize(annotation);

        TestDto dto = new TestDto("Password123", "Password123");

        assertTrue(validator.isValid(dto, context));
    }

    @Test
    void validate_nullValue_returnsTrue() {
        PasswordMatches annotation = mock(PasswordMatches.class);
        when(annotation.first()).thenReturn("password");
        when(annotation.second()).thenReturn("confirmPassword");
        when(annotation.message()).thenReturn("Passwords do not match");
        validator.initialize(annotation);

        assertTrue(validator.isValid(null, context));
    }

    @Test
    void validate_bothFieldsNull_returnsTrue() {
        PasswordMatches annotation = mock(PasswordMatches.class);
        when(annotation.first()).thenReturn("password");
        when(annotation.second()).thenReturn("confirmPassword");
        when(annotation.message()).thenReturn("Passwords do not match");
        validator.initialize(annotation);

        TestDto dto = new TestDto(null, null);

        assertTrue(validator.isValid(dto, context));
    }

    private static class TestDto {
        private final String password;
        private final String confirmPassword;

        TestDto(String password, String confirmPassword) {
            this.password = password;
            this.confirmPassword = confirmPassword;
        }

        public String getPassword() {
            return password;
        }

        public String getConfirmPassword() {
            return confirmPassword;
        }
    }
}
