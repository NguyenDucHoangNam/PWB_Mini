package com.pwb.iam.domain.service;

public enum PasswordPolicyViolation {
    TOO_SHORT("password.violation.too_short"),
    TOO_LONG("password.violation.too_long"),
    MISSING_UPPER("password.violation.missing_upper"),
    MISSING_LOWER("password.violation.missing_lower"),
    MISSING_DIGIT("password.violation.missing_digit"),
    MISSING_SPECIAL("password.violation.missing_special"),
    CONTAINS_WHITESPACE("password.violation.contains_whitespace"),
    NULL_PASSWORD("password.violation.null");

    private final String messageKey;

    PasswordPolicyViolation(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
