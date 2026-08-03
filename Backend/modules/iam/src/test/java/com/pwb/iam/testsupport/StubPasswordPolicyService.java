package com.pwb.iam.testsupport;

import com.pwb.iam.domain.service.PasswordPolicyResult;
import com.pwb.iam.domain.service.PasswordPolicyService;

import java.util.List;

public class StubPasswordPolicyService implements PasswordPolicyService {

    private boolean valid = true;
    private List<String> messages = List.of();

    public StubPasswordPolicyService alwaysValid() {
        this.valid = true;
        this.messages = List.of();
        return this;
    }

    public StubPasswordPolicyService presetViolations(List<String> messages) {
        this.valid = false;
        this.messages = messages;
        return this;
    }

    @Override
    public PasswordPolicyResult validate(String rawPassword) {
        if (valid) {
            return PasswordPolicyResult.ok();
        }
        return PasswordPolicyResult.failure(messages);
    }
}
