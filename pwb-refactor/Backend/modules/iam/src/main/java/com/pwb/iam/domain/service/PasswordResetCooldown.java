package com.pwb.iam.domain.service;

public interface PasswordResetCooldown {

    long enforce(String email);
}
