package com.pwb.backend.common.exception;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

@Getter
@Builder(builderClassName = "BusinessExceptionBuilder")
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String customMessage;
    private final Map<String, Object> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null, null, null);
    }

    public BusinessException(ErrorCode errorCode, String customMessage) {
        this(errorCode, customMessage, null, null);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, null, cause, null);
    }

    public BusinessException(ErrorCode errorCode, String customMessage, Throwable cause) {
        this(errorCode, customMessage, cause, null);
    }

    public BusinessException(ErrorCode errorCode, String customMessage, Throwable cause, Map<String, Object> details) {
        super(customMessage != null ? customMessage : (errorCode != null ? errorCode.defaultMessage() : null), cause);
        this.errorCode = errorCode;
        this.customMessage = customMessage;
        this.details = details == null || details.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(Map.copyOf(details));
    }

    public static BusinessExceptionBuilder builder() {
        return new BusinessExceptionBuilder();
    }

    public static class BusinessExceptionBuilder {
        private ErrorCode errorCode;
        private String customMessage;
        private Throwable cause;
        private Map<String, Object> details;

        public BusinessExceptionBuilder errorCode(ErrorCode errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public BusinessExceptionBuilder customMessage(String customMessage) {
            this.customMessage = customMessage;
            return this;
        }

        public BusinessExceptionBuilder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public BusinessExceptionBuilder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        public BusinessException build() {
            return new BusinessException(errorCode, customMessage, cause, details);
        }
    }

    public HttpStatus httpStatus() {
        return errorCode != null ? errorCode.httpStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public String errorCodeName() {
        return errorCode instanceof Enum<?> enumCode ? enumCode.name() : errorCode.code();
    }
}
