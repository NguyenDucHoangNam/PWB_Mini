package com.pwb.iam.core.service;

public enum PasswordPolicyViolation {
    TOO_SHORT,
    TOO_LONG,
    MISSING_UPPER,
    MISSING_LOWER,
    MISSING_DIGIT,
    MISSING_SPECIAL,
    CONTAINS_WHITESPACE
}
