package com.pwb.backend.web.error;

import com.pwb.backend.exception.BaseBusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.backend.exception.ErrorDetail;
import com.pwb.backend.exception.ErrorResponse;
import com.pwb.backend.exception.SysErrorCode;
import com.pwb.backend.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Locale;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final ErrorCode VALIDATION_CODE = ValidationException.CODE;
    private static final ErrorCode FILE_TOO_LARGE_CODE = SysErrorCode.FILE_TOO_LARGE;
    private static final ErrorCode RESOURCE_NOT_FOUND_CODE = SysErrorCode.RESOURCE_NOT_FOUND;
    private static final ErrorCode INTERNAL_SERVER_ERROR_CODE = SysErrorCode.INTERNAL_SERVER_ERROR;

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(BaseBusinessException.class)
    public ResponseEntity<ErrorResponse> handleBaseBusiness(BaseBusinessException ex) {
        ErrorCode ec = ex.getErrorCode();
        return ResponseEntity.status(HttpStatus.valueOf(ec.httpStatus()))
                .body(ErrorResponse.of(ec, resolveMessage(ec, ex.getArgs())));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.valueOf(FILE_TOO_LARGE_CODE.httpStatus()))
                .body(ErrorResponse.of(FILE_TOO_LARGE_CODE, resolveMessage(FILE_TOO_LARGE_CODE, null)));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        log.warn("Resource not found: {} {}", ex.getHttpMethod(), ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.valueOf(RESOURCE_NOT_FOUND_CODE.httpStatus()))
                .body(ErrorResponse.of(RESOURCE_NOT_FOUND_CODE, resolveMessage(RESOURCE_NOT_FOUND_CODE, null)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> ErrorDetail.builder()
                        .field(err.getField())
                        .code(err.getCode())
                        .issue(err.getDefaultMessage())
                        .rejectedValue(err.getRejectedValue())
                        .build())
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        VALIDATION_CODE,
                        resolveMessage(VALIDATION_CODE, new Object[]{details.size()}),
                        details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        INTERNAL_SERVER_ERROR_CODE,
                        resolveMessage(INTERNAL_SERVER_ERROR_CODE, null)));
    }

    private String resolveMessage(ErrorCode ec, Object[] args) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(ec.code(), args, ec.defaultMessage(), locale);
        } catch (Exception ex) {
            return ec.defaultMessage();
        }
    }
}
