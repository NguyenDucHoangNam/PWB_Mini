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

    public static Password empty() {
        return new Password("");
    }

    public boolean isHashed() {
        return hash != null && !hash.isBlank();
    }
}
