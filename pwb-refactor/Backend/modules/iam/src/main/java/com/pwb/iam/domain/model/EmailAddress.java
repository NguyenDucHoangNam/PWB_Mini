package com.pwb.iam.domain.model;

import java.util.regex.Pattern;

public record EmailAddress(String value) {

    private static final Pattern RFC_5322_SIMPLIFIED = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    public EmailAddress {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        String normalized = value.trim().toLowerCase();
        if (!RFC_5322_SIMPLIFIED.matcher(normalized).matches()) {
            throw new IllegalArgumentException("email is not valid: " + value);
        }
        value = normalized;
    }

    public static EmailAddress of(String raw) {
        return new EmailAddress(raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
