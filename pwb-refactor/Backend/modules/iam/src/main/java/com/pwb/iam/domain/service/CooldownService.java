package com.pwb.iam.domain.service;

public interface CooldownService {

    long enforceRegisterCooldown(String email);

    long enforceResendOtpCooldown(String email);
}
