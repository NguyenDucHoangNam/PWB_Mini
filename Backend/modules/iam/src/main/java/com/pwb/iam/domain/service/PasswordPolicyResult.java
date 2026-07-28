package com.pwb.iam.domain.service;

public record PasswordPolicyResult(boolean valid, java.util.List<PasswordPolicyViolation> violations) {

    public boolean isInvalid() {
        return !valid;
    }

    public static PasswordPolicyResult of(java.util.List<PasswordPolicyViolation> violations) {
        return new PasswordPolicyResult(violations.isEmpty(), violations);
    }
}
