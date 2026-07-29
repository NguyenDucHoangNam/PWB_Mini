package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.PasswordPolicyResult;
import com.pwb.iam.domain.service.PasswordPolicyService;
import com.pwb.iam.domain.service.PasswordPolicyViolation;
import com.pwb.iam.infrastructure.config.PasswordPolicyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordPolicyAdapter implements PasswordPolicyService {

    private final PasswordPolicyProperties policyProperties;

    @Override
    public PasswordPolicyResult validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return PasswordPolicyResult.of(List.of());
        }

        List<PasswordPolicyViolation> violations = new ArrayList<>();

        if (rawPassword.length() < policyProperties.getMinLength()) {
            violations.add(PasswordPolicyViolation.TOO_SHORT);
        }
        if (rawPassword.length() > policyProperties.getMaxLength()) {
            violations.add(PasswordPolicyViolation.TOO_LONG);
        }

        if (violations.isEmpty()) {
            return PasswordPolicyResult.of(List.of());
        }
        return PasswordPolicyResult.of(List.copyOf(violations));
    }
}
