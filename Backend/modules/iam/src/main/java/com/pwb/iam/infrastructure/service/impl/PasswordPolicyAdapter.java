package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.PasswordPolicyResult;
import com.pwb.iam.domain.service.PasswordPolicyService;
import com.pwb.iam.domain.service.PasswordPolicyViolation;
import com.pwb.iam.infrastructure.config.PasswordPolicyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordPolicyAdapter implements PasswordPolicyService {

    private final PasswordPolicyProperties policyProperties;
    private final MessageSource messageSource;

    @Override
    public PasswordPolicyResult validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return PasswordPolicyResult.failure(List.of(
                    messageSource.getMessage(PasswordPolicyViolation.NULL_PASSWORD.messageKey(),
                            null, Locale.getDefault())
            ));
        }

        List<PasswordPolicyViolation> violations = new ArrayList<>();

        if (rawPassword.length() < policyProperties.getMinLength()) {
            violations.add(PasswordPolicyViolation.TOO_SHORT);
        }
        if (rawPassword.length() > policyProperties.getMaxLength()) {
            violations.add(PasswordPolicyViolation.TOO_LONG);
        }
        if (!rawPassword.matches(".*[A-Z].*")) {
            violations.add(PasswordPolicyViolation.MISSING_UPPER);
        }
        if (!rawPassword.matches(".*[a-z].*")) {
            violations.add(PasswordPolicyViolation.MISSING_LOWER);
        }
        if (!rawPassword.matches(".*\\d.*")) {
            violations.add(PasswordPolicyViolation.MISSING_DIGIT);
        }
        if (!rawPassword.matches(".*[^a-zA-Z0-9].*")) {
            violations.add(PasswordPolicyViolation.MISSING_SPECIAL);
        }
        if (rawPassword.contains(" ")) {
            violations.add(PasswordPolicyViolation.CONTAINS_WHITESPACE);
        }

        if (violations.isEmpty()) {
            return PasswordPolicyResult.ok();
        }

        List<String> messages = violations.stream()
                .map(this::resolveMessage)
                .toList();
        return PasswordPolicyResult.failure(messages);
    }

    private String resolveMessage(PasswordPolicyViolation violation) {
        return switch (violation) {
            case TOO_SHORT -> messageSource.getMessage(violation.messageKey(),
                    new Object[]{policyProperties.getMinLength()}, Locale.getDefault());
            case TOO_LONG -> messageSource.getMessage(violation.messageKey(),
                    new Object[]{policyProperties.getMaxLength()}, Locale.getDefault());
            default -> messageSource.getMessage(violation.messageKey(), null, Locale.getDefault());
        };
    }
}
