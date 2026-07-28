package com.pwb.iam.domain.model;

import java.util.Objects;

public final class Password {

    private final String hash;

    private Password(String hash) {
        this.hash = hash;
    }

    public static Password fromHash(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("password hash must not be blank");
        }
        return new Password(hash);
    }

    public static Password empty() {
        return new Password("");
    }

    public String getHash() {
        return hash;
    }

    public boolean isHashed() {
        return hash != null && !hash.isBlank();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Password other)) {
            return false;
        }
        return Objects.equals(hash, other.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(hash);
    }
}
