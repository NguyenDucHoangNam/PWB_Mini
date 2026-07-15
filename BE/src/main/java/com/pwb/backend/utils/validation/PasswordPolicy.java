package com.pwb.backend.utils.validation;

import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

@UtilityClass
public class PasswordPolicy {

    private static final Pattern PATTERN = Pattern.compile(
            "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*]).{8,128}$"
    );

    public boolean matches(String password) {
        return password != null && PATTERN.matcher(password).matches();
    }
}
