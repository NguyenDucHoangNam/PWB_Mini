package com.pwb.iam.core.service;

public interface PasswordPolicyService {

    void validate(String rawPassword);

    String hash(String rawPassword);
}
