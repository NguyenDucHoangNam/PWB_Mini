package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.usecase.EnforcePasswordPolicyUseCase;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.service.PasswordPolicyResult;
import com.pwb.iam.domain.service.PasswordPolicyService;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnforcePasswordPolicyUseCaseImpl implements EnforcePasswordPolicyUseCase {

    private final PasswordPolicyService passwordPolicyService;

    @Override
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
