package com.pwb.iam.domain.model;

import lombok.Getter;

@Getter
public final class Password {

    private final String hash;

    private Password(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("Password hash must not be blank");
        }
        this.hash = hash;
    }

    public static Password fromHash(String hashed) {
        return new Password(hashed);
    }
}
