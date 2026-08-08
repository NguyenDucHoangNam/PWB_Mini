package com.pwb.iam.domain.model;

public record Password(String hash) {

    public Password {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("password hash must not be blank");
        }
    }

    public static Password fromHash(String hash) {
        return new Password(hash);
    }

    /**
     * Always true for a constructed instance — the compact constructor rejects blank hashes.
     * Kept so callers can express "is this a usable password" as {@code p != null && p.isHashed()}.
     */
    public boolean isHashed() {
        return true;
    }
}
