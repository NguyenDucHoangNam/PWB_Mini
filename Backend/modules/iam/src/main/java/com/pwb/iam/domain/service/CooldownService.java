package com.pwb.iam.domain.service;

public interface CooldownService {

    long enforceResetCooldown(String email);
}
