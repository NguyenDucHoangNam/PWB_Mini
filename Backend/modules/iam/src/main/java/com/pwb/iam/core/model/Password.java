package com.pwb.iam.core.model;

import com.pwb.iam.core.service.PasswordPolicyService;
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

    public static Password fromRaw(String raw, PasswordPolicyService policy) {
        policy.validate(raw);
        String hashed = policy.hash(raw);
        return new Password(hashed);
    }
}
