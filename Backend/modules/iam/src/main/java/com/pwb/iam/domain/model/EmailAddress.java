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
                || localPartEndsWithDot(normalized)
                || !RFC_5322_SIMPLIFIED.matcher(normalized).matches()) {
            throw new IllegalArgumentException("email is not valid: " + value);
        }
        this.value = normalized;
    }

    public static EmailAddress of(String raw) {
        return new EmailAddress(raw);
    }

    /**
     * Rejects {@code foo.@example.com}. The guards above are deliberately symmetric about the dot —
     * {@code startsWith(".")} covers a leading dot in the local part and {@code endsWith(".")} a
     * trailing dot in the domain — but neither sees the character just before the {@code @}, and
     * the pattern's {@code [A-Za-z0-9._%+-]+} happily allows one there.
     *
     * <p>Worth closing rather than tolerating, because such an address fails silently in the worst
     * place: registration succeeds, the account is written as PENDING_VERIFICATION, and the OTP mail
     * is then refused by the recipient's server. The user is left staring at a code entry screen
     * for a mail that is never going to arrive, and nothing server-side reports an error.
     */
    private static boolean localPartEndsWithDot(String normalized) {
        int at = normalized.indexOf('@');
        return at > 0 && normalized.charAt(at - 1) == '.';
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
