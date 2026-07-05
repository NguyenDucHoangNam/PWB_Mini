package com.pwb.backend.iam.internal.validation;

import com.pwb.backend.iam.api.dto.request.RegisterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchValidator implements ConstraintValidator<PasswordMatch, RegisterRequest> {

  @Override
  public boolean isValid(RegisterRequest request, ConstraintValidatorContext context) {
    if (request.password() == null || request.confirmPassword() == null) {
      return true;
    }

    boolean matches = request.password().equals(request.confirmPassword());
    if (!matches) {
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate("Passwords do not match")
          .addPropertyNode("confirmPassword")
          .addConstraintViolation();
    }
    return matches;
  }
}