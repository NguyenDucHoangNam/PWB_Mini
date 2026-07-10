package com.pwb.backend.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Marker interface for any error-code enum, shared or module-specific. Lets
 * {@link BusinessException} accept either the platform {@link ErrorCode} or a
 * module-local enum like {@code IamErrorCode} / {@code AudioErrorCode}
 * without forcing every module to depend on the shared enum.
 *
 * <p>H11: this is the bridge that keeps {@link GlobalExceptionHandler}
 * generic while letting each module own its own business codes.
 */
public interface ErrorCodeLike {
    String getCode();
    String getDefaultMessage();
    HttpStatus getHttpStatus();
}