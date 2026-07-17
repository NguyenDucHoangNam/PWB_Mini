package com.pwb.iam.core.service.impl;

import com.pwb.iam.core.model.EmailAddress;
import com.pwb.iam.core.model.OAuthProvider;
import com.pwb.iam.core.model.Password;
import com.pwb.iam.core.model.User;
import com.pwb.iam.core.service.PasswordPolicyService;
import com.pwb.iam.core.service.RegisterCommand;
import com.pwb.iam.core.service.UserRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegistrationServiceImpl implements UserRegistrationService {

    private final PasswordPolicyService passwordPolicyService;

    @Override
    public User register(RegisterCommand command) {
        log.info("Registering user: email={}, provider={}", command.email(), command.provider());

        Password password = Password.fromRaw(command.rawPassword(), passwordPolicyService);
        EmailAddress email = EmailAddress.of(command.email());
        String username = deriveUsername(command.email());
        String fullName = command.fullName() == null || command.fullName().isBlank()
                ? username
                : command.fullName();

        User user = command.provider() == OAuthProvider.GOOGLE
                ? User.createGoogle(username, email, null, fullName, null)
                : User.createLocal(username, email, password, fullName);

        log.info("User registered: userId={}, email={}", user.getUserId(), user.getEmail().value());
        return user;
    }

    private String deriveUsername(String email) {
        int atIndex = email.indexOf('@');
        return atIndex > 0 ? email.substring(0, atIndex) : email;
    }
}
