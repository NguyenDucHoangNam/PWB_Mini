package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.PasswordPolicyResult;
import com.pwb.iam.domain.service.PasswordPolicyService;
import com.pwb.iam.domain.service.PasswordPolicyViolation;
import com.pwb.iam.infrastructure.config.PasswordPolicyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
            return PasswordPolicyResult.failure(
                    List.of(resolveMessage(PasswordPolicyViolation.NULL_PASSWORD)));
        }

        List<PasswordPolicyViolation> violations = collectViolations(rawPassword);
        if (violations.isEmpty()) {
            return PasswordPolicyResult.ok();
        }
        return PasswordPolicyResult.failure(violations.stream().map(this::resolveMessage).toList());
    }

    /**
     * Single pass over the characters instead of four {@code String.matches} calls — each of those
     * compiles a fresh {@link java.util.regex.Pattern}, and this runs on every registration,
     * password change and reset.
     */
    private List<PasswordPolicyViolation> collectViolations(String rawPassword) {
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        boolean hasWhitespace = false;

        for (int i = 0; i < rawPassword.length(); i++) {
            char c = rawPassword.charAt(i);
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
            if (Character.isWhitespace(c)) {
                hasWhitespace = true;
            } else if (!Character.isLetterOrDigit(c)) {
                hasSpecial = true;
            }
        }

        List<PasswordPolicyViolation> violations = new ArrayList<>();
        if (rawPassword.length() < policyProperties.getMinLength()) {
            violations.add(PasswordPolicyViolation.TOO_SHORT);
        }
        if (rawPassword.length() > policyProperties.getMaxLength()) {
            violations.add(PasswordPolicyViolation.TOO_LONG);
        }
        if (!hasUpper) {
            violations.add(PasswordPolicyViolation.MISSING_UPPER);
        }
        if (!hasLower) {
            violations.add(PasswordPolicyViolation.MISSING_LOWER);
        }
        if (!hasDigit) {
            violations.add(PasswordPolicyViolation.MISSING_DIGIT);
        }
        if (!hasSpecial) {
            violations.add(PasswordPolicyViolation.MISSING_SPECIAL);
        }
        if (hasWhitespace) {
            violations.add(PasswordPolicyViolation.CONTAINS_WHITESPACE);
        }
        return violations;
    }

    /**
     * Resolves against the caller's locale, not the server's default — these strings are shown to
     * the user, so {@code Locale.getDefault()} would pin every response to whatever locale the
     * JVM happens to start with.
     */
    private String resolveMessage(PasswordPolicyViolation violation) {
        Locale locale = LocaleContextHolder.getLocale();
        Object[] args = switch (violation) {
            case TOO_SHORT -> new Object[]{policyProperties.getMinLength()};
            case TOO_LONG -> new Object[]{policyProperties.getMaxLength()};
            default -> null;
        };
        return messageSource.getMessage(violation.messageKey(), args, violation.messageKey(), locale);
    }
}
