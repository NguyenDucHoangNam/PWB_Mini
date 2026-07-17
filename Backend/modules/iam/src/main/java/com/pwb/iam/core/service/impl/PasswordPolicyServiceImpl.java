package com.pwb.iam.core.service.impl;

import com.pwb.iam.core.exception.WeakPasswordException;
import com.pwb.iam.core.service.PasswordPolicyResult;
import com.pwb.iam.core.service.PasswordPolicyService;
import com.pwb.iam.core.service.PasswordPolicyViolation;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class PasswordPolicyServiceImpl implements PasswordPolicyService {

    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 128;
    private static final int ITERATIONS = 3;
    private static final int MEMORY_KB = 65536;
    private static final int PARALLELISM = 1;

    private static final String UPPER = ".*[A-Z].*";
    private static final String LOWER = ".*[a-z].*";
    private static final String DIGIT = ".*[0-9].*";
    private static final String SPECIAL = ".*[^A-Za-z0-9].*";
    private static final String WHITESPACE = ".*\\s.*";

    private final Argon2 argon2;

    public PasswordPolicyServiceImpl() {
        this(Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id));
    }

    public PasswordPolicyServiceImpl(Argon2 argon2) {
        this.argon2 = argon2;
    }

    @Override
    public PasswordPolicyResult validate(String rawPassword) {
        List<PasswordPolicyViolation> violations = new ArrayList<>();

        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            violations.add(PasswordPolicyViolation.TOO_SHORT);
        } else if (rawPassword.length() > MAX_LENGTH) {
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

    @Override
    public String hash(String rawPassword) {
        PasswordPolicyResult result = validate(rawPassword);
        if (result.isInvalid()) {
            log.warn("Password policy rejected: violations={}", result.violations());
            throw new WeakPasswordException(result.violations());
        }
        char[] chars = rawPassword.toCharArray();
        try {
            return argon2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, chars);
        } finally {
            java.util.Arrays.fill(chars, '\0');
        }
    }
}
