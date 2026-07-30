package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.usecase.ValidatePasswordPolicyUseCase;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.service.PasswordPolicyResult;
import com.pwb.iam.domain.service.PasswordPolicyService;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValidatePasswordPolicyUseCaseImpl implements ValidatePasswordPolicyUseCase {

    private final PasswordPolicyService passwordPolicyService;

    @Override
    public void validate(String rawPassword) {
        PasswordPolicyResult result = passwordPolicyService.validate(rawPassword);
        if (!result.valid()) {
            throw new BusinessException(IamErrorCode.WEAK_PASSWORD,
                    java.util.Map.of("violations", result.messages()));
        }
    }
}
