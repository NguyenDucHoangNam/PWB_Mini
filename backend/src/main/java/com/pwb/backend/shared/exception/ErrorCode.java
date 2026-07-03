package com.pwb.backend.shared.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
  VALIDATION_FAILED("VALIDATION_FAILED", "Validation failed for request parameters", HttpStatus.BAD_REQUEST),
  UNAUTHORIZED("UNAUTHORIZED", "Full authentication is required to access this resource", HttpStatus.UNAUTHORIZED),
  FORBIDDEN("FORBIDDEN", "You do not have permission to access this resource", HttpStatus.FORBIDDEN),
  RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "Requested resource was not found", HttpStatus.NOT_FOUND),

  USER_NOT_EXISTED("USER_NOT_EXISTED", "User does not exist", HttpStatus.NOT_FOUND),
  USERNAME_EXISTED("USERNAME_EXISTED", "Username is already taken", HttpStatus.BAD_REQUEST),
  EMAIL_EXISTED("EMAIL_EXISTED", "Email is already in use", HttpStatus.BAD_REQUEST),
  OTP_EXPIRED("OTP_EXPIRED", "OTP has expired, please request a new one", HttpStatus.BAD_REQUEST),
  INVALID_OTP("INVALID_OTP", "Invalid OTP code", HttpStatus.BAD_REQUEST),
  OTP_COOLDOWN("OTP_COOLDOWN", "Please wait before requesting a new OTP", HttpStatus.TOO_MANY_REQUESTS),
  OTP_ATTEMPTS_EXCEEDED("OTP_ATTEMPTS_EXCEEDED", "Too many failed OTP attempts", HttpStatus.LOCKED),
  DISPOSABLE_EMAIL_NOT_ALLOWED("DISPOSABLE_EMAIL_NOT_ALLOWED", "Disposable email addresses are not allowed", HttpStatus.BAD_REQUEST),
  REGISTRATION_IN_PROGRESS("REGISTRATION_IN_PROGRESS", "Registration is already in progress for this account", HttpStatus.BAD_REQUEST),
  ACCOUNT_ALREADY_ACTIVE("ACCOUNT_ALREADY_ACTIVE", "Account has already been activated", HttpStatus.BAD_REQUEST),
  RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", "Too many requests, please try again later", HttpStatus.TOO_MANY_REQUESTS),
  INVALID_REFRESH_TOKEN("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired", HttpStatus.UNAUTHORIZED),
  TOKEN_THEFT_DETECTED("TOKEN_THEFT_DETECTED", "Token reuse detected, all sessions revoked", HttpStatus.UNAUTHORIZED);

  private final String code;
  private final String defaultMessage;
  private final HttpStatus httpStatus;
}
