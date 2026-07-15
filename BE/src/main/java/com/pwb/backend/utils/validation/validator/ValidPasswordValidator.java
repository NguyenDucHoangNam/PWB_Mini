package com.pwb.backend.utils.validation.validator;

import com.pwb.backend.utils.validation.PasswordPolicy;
import com.pwb.backend.utils.validation.annotation.ValidPassword;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return PasswordPolicy.matches(value);
    }
}
