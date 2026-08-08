package com.pwb.iam.domain.service;

public interface PasswordPolicyService {

    PasswordPolicyResult validate(String rawPassword);
}
