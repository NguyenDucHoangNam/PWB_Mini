package com.pwb.iam.application.policy;

import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.service.PasswordPolicyResult;
import com.pwb.iam.domain.service.PasswordPolicyService;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PasswordPolicyEnforcer {

    private final PasswordPolicyService passwordPolicyService;

    public void enforce(String rawPassword) {
        PasswordPolicyResult result = passwordPolicyService.validate(rawPassword);
        if (result.isInvalid()) {
            String reasons = result.violations().stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new BusinessException(IamErrorCode.WEAK_PASSWORD, reasons);
        }
    }
}
