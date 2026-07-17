package com.pwb.iam.core.service;

import com.pwb.iam.core.model.User;

public interface UserRegistrationService {

    User register(RegisterCommand command);
}
