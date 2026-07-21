package com.pwb.iam.infrastructure.security.annotation;

import com.pwb.iam.core.service.PasswordPolicyResult;
import com.pwb.iam.core.service.PasswordPolicyService;
import com.pwb.iam.core.service.PasswordPolicyViolation;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

import java.util.stream.Collectors;

@RequiredArgsConstructor
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final String MESSAGE_KEY = "validation.password.weak.with-reason";

    private final PasswordPolicyService passwordPolicyService;

    @Override
    public boolean isValid(String rawPassword, ConstraintValidatorContext context) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return true;
        }
        PasswordPolicyResult result = passwordPolicyService.validate(rawPassword);
        if (result.valid()) {
            return true;
        }
        String reasons = result.violations().stream()
                .map(PasswordPolicyViolation::name)
                .collect(Collectors.joining(", "));
        HibernateConstraintValidatorContext hibernateContext =
                context.unwrap(HibernateConstraintValidatorContext.class);
        hibernateContext.disableDefaultConstraintViolation();
        hibernateContext.addMessageParameter("reasons", reasons);
        hibernateContext.buildConstraintViolationWithTemplate("{" + MESSAGE_KEY + "}")
                .addConstraintViolation();
        return false;
    }
}