package com.pwb.iam.domain.model;

import java.util.regex.Pattern;

public record EmailAddress(String value) {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    public EmailAddress {
        if (value == null || value.isBlank() || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid email address: " + value);
        }
    }

    public static EmailAddress of(String raw) {
        return new EmailAddress(raw == null ? null : raw.trim());
    }
}
