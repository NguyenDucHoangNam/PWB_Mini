package com.pwb.backend.shared.model;

import org.hibernate.annotations.SQLRestriction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SQLRestriction("deleted = false")
public @interface SoftDelete {
}