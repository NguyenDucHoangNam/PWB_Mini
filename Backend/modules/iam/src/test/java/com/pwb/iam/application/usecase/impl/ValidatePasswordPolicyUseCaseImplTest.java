package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.service.PasswordPolicyResult;
import com.pwb.iam.domain.service.PasswordPolicyService;
import com.pwb.shared.exception.BusinessException;
import com.pwb.iam.testsupport.StubPasswordPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidatePasswordPolicyUseCaseImplTest {

    private StubPasswordPolicyService service;
    private ValidatePasswordPolicyUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        service = new StubPasswordPolicyService();
        useCase = new ValidatePasswordPolicyUseCaseImpl((PasswordPolicyService) service);
    }

    @Test
    @DisplayName("should not throw when password is valid")
    void should_pass_for_valid_password() {
        service.alwaysValid();

        assertThatCode(() -> useCase.validate("StrongP@ss123!")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should throw WEAK_PASSWORD with violations list when invalid")
    void should_throw_weak_password() {
        service.presetViolations(List.of("too short", "missing digit"));

        assertThatThrownBy(() -> useCase.validate("weak"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("WEAK_PASSWORD");
    }

    @Test
    @DisplayName("should use PasswordPolicyResult.valid() to decide throw vs pass")
    void should_respect_valid_flag() {
        PasswordPolicyResult result = PasswordPolicyResult.failure(List.of("bad"));
        StubPasswordPolicyService stub = new StubPasswordPolicyService() {
            @Override
            public PasswordPolicyResult validate(String rawPassword) {
                return result;
            }
        };
        ValidatePasswordPolicyUseCaseImpl direct = new ValidatePasswordPolicyUseCaseImpl(stub);

        assertThatThrownBy(() -> direct.validate("x"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("should expose default constructor chain behavior")
    void should_expose_service_behavior() {
        PasswordPolicyResult result = PasswordPolicyResult.ok();
        StubPasswordPolicyService stub = new StubPasswordPolicyService() {
            @Override
            public PasswordPolicyResult validate(String rawPassword) {
                return result;
            }
        };
        ValidatePasswordPolicyUseCaseImpl direct = new ValidatePasswordPolicyUseCaseImpl(stub);

        assertThatCode(() -> direct.validate("x")).doesNotThrowAnyException();
    }
}