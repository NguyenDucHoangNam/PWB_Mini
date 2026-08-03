package com.pwb.iam.domain.model;

import java.util.regex.Pattern;

public record EmailAddress(String value) {

    private static final Pattern RFC_5322_SIMPLIFIED = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    public EmailAddress(String value) {
        if (value == null) {
            throw new IllegalArgumentException("email is not valid: null");
        }
        String normalized = normalize(value);
        if (normalized.contains("..")
                || normalized.startsWith(".")
                || normalized.endsWith(".")
                || !RFC_5322_SIMPLIFIED.matcher(normalized).matches()) {
            throw new IllegalArgumentException("email is not valid: " + value);
        }
        this.value = normalized;
    }

    public static EmailAddress of(String raw) {
        return new EmailAddress(raw);
    }

    /**
     * Canonical form used for lookups and rate-limit keys: trimmed and lower-cased.
     * Callers that only need a comparison key should use this instead of re-implementing
     * {@code trim().toLowerCase()}, so normalization stays defined in one place.
     */
    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase();
    }

    @Override
    public String toString() {
        return value;
    }
}
