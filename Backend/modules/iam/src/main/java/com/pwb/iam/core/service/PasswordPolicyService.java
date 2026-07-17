package com.pwb.iam.core.service;

public interface PasswordPolicyService {

    PasswordPolicyResult validate(String rawPassword);

    String hash(String rawPassword);
}
