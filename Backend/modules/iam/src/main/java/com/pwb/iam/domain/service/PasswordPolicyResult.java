package com.pwb.iam.domain.service;

import java.util.List;

public record PasswordPolicyResult(boolean valid, List<String> messages) {

    public static PasswordPolicyResult ok() {
        return new PasswordPolicyResult(true, List.of());
    }

    public static PasswordPolicyResult failure(List<String> messages) {
        return new PasswordPolicyResult(false, messages == null ? List.of() : messages);
    }
}
