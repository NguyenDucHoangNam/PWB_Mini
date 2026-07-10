package com.pwb.backend.shared.exception;

import lombok.Getter;

/**
 * Application-level exception carrying an {@link ErrorCodeLike} (either the
 * platform {@link ErrorCode} or a module-specific enum).
 *
 * <p>The optional {@code data} payload is intentionally hidden from the API
 * response (H6): we keep it on the exception for logging and for callers
 * that explicitly opt-in via a typed DTO, but
 * {@link GlobalExceptionHandler} no longer serialises it.
 */
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCodeLike errorCode;
    private final Object[] args;
    private final Object data;

    public BusinessException(ErrorCodeLike errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.args = null;
        this.data = null;
    }

    public BusinessException(ErrorCodeLike errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
        this.data = null;
    }

    public BusinessException(ErrorCodeLike errorCode, Object[] args) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.args = args;
        this.data = null;
    }

    public BusinessException(ErrorCodeLike errorCode, String message, Object[] args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
        this.data = null;
    }

    /**
     * @deprecated H6: never serialise {@code data} to clients. The constructor
     *     stays so existing module code keeps compiling, but the handler will
     *     drop the field. Prefer a typed DTO returned via the controller.
     */
    @Deprecated
    public BusinessException(ErrorCodeLike errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
        this.data = data;
    }
}