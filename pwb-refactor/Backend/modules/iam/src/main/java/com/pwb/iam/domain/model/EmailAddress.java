package com.pwb.iam.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public final class EmailAddress {

    private static final Pattern RFC_5322_SIMPLIFIED = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final String value;

    private EmailAddress(String value) {
        this.value = value;
    }

    public static EmailAddress of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        String normalized = raw.trim().toLowerCase();
        if (!RFC_5322_SIMPLIFIED.matcher(normalized).matches()) {
            throw new IllegalArgumentException("email is not valid: " + raw);
        }
        return new EmailAddress(normalized);
    }

    public String value() {
        return value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmailAddress other)) {
            return false;
        }
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
