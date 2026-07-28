package com.pwb.iam.domain.service;

import java.util.List;

public record PasswordPolicyResult(boolean valid, List<PasswordPolicyViolation> violations) {

    public boolean isInvalid() {
        return !valid;
    }

    public static PasswordPolicyResult of(List<PasswordPolicyViolation> violations) {
        return new PasswordPolicyResult(violations == null || violations.isEmpty(), violations == null ? List.of() : violations);
    }
}
