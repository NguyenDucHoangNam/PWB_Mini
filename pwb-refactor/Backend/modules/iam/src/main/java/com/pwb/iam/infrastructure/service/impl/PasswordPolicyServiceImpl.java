package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.PasswordPolicyResult;
import com.pwb.iam.domain.service.PasswordPolicyService;
import com.pwb.iam.domain.service.PasswordPolicyViolation;
import com.pwb.iam.infrastructure.config.PasswordPolicyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PasswordPolicyServiceImpl implements PasswordPolicyService {

    private static final String UPPER = ".*[A-Z].*";
    private static final String LOWER = ".*[a-z].*";
    private static final String DIGIT = ".*[0-9].*";
    private static final String SPECIAL = ".*[^A-Za-z0-9].*";
    private static final String WHITESPACE = ".*\\s.*";

    private final PasswordPolicyProperties properties;

    public PasswordPolicyServiceImpl(PasswordPolicyProperties properties) {
        this.properties = properties;
    }

    @Override
    public PasswordPolicyResult validate(String rawPassword) {
        List<PasswordPolicyViolation> violations = new ArrayList<>();
        int minLength = properties.getMinLength();
        int maxLength = properties.getMaxLength();

        if (rawPassword == null || rawPassword.length() < minLength) {
            violations.add(PasswordPolicyViolation.TOO_SHORT);
        } else if (rawPassword.length() > maxLength) {
            violations.add(PasswordPolicyViolation.TOO_LONG);
        }

        if (rawPassword != null && !rawPassword.isEmpty()) {
            if (!rawPassword.matches(UPPER)) {
                violations.add(PasswordPolicyViolation.MISSING_UPPER);
            }
            if (!rawPassword.matches(LOWER)) {
                violations.add(PasswordPolicyViolation.MISSING_LOWER);
            }
            if (!rawPassword.matches(DIGIT)) {
                violations.add(PasswordPolicyViolation.MISSING_DIGIT);
            }
            if (!rawPassword.matches(SPECIAL)) {
                violations.add(PasswordPolicyViolation.MISSING_SPECIAL);
            }
            if (rawPassword.matches(WHITESPACE)) {
                violations.add(PasswordPolicyViolation.CONTAINS_WHITESPACE);
            }
        }

        return PasswordPolicyResult.of(violations);
    }
}
