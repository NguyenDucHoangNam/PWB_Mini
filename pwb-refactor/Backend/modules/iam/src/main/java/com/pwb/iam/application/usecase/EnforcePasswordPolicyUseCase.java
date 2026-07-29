package com.pwb.iam.application.usecase;

public interface EnforcePasswordPolicyUseCase {

    void enforce(String rawPassword);
}
