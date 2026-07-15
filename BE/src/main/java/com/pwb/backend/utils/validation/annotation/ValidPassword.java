package com.pwb.backend.utils.validation.annotation;

import com.pwb.backend.utils.validation.validator.ValidPasswordValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPasswordValidator.class)
public @interface ValidPassword {

    String message() default "{validation.password.strength}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
