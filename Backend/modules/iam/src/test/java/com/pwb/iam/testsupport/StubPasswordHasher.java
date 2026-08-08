package com.pwb.iam.testsupport;

import com.pwb.iam.domain.service.PasswordHasher;

public final class StubPasswordHasher implements PasswordHasher {

    private static final String PREFIX = "hashed:";

    @Override
    public String hash(String rawPassword) {
        return PREFIX + rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        return encodedPassword.equals(PREFIX + rawPassword);
    }
}
