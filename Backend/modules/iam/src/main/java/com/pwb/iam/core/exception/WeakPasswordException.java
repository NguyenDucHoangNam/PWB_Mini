package com.pwb.iam.core.exception;

import com.pwb.kernel.exception.BaseBusinessException;
import com.pwb.iam.core.exception.IamErrorCode;
import com.pwb.iam.core.service.PasswordPolicyViolation;

import java.util.List;

public class WeakPasswordException extends BaseBusinessException {

    private final List<PasswordPolicyViolation> violations;

    public WeakPasswordException(List<PasswordPolicyViolation> violations) {
        super(IamErrorCode.WEAK_PASSWORD);
        this.violations = List.copyOf(violations);
    }

    public List<PasswordPolicyViolation> getViolations() {
        return violations;
    }
}
