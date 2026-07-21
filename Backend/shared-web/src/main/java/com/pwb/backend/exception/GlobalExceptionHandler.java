package com.pwb.backend.exception;

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

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(BaseBusinessException.class)
    public ResponseEntity<ErrorResponse> handleBaseBusiness(BaseBusinessException ex) {
        ErrorCode ec = ex.getErrorCode();
        return ResponseEntity.status(HttpStatus.valueOf(ec.getHttpStatus()))
                .body(ErrorResponse.of(ec, resolveMessage(ec, extractArgs(ex))));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUpload(MaxUploadSizeExceededException ex) {
        ErrorCode ec = ErrorCode.FILE_TOO_LARGE;
        return ResponseEntity.status(HttpStatus.valueOf(ec.getHttpStatus()))
                .body(ErrorResponse.of(ec, resolveMessage(ec, null)));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        log.warn("Resource not found: {} {}", ex.getHttpMethod(), ex.getResourcePath());
        ErrorCode ec = ErrorCode.RESOURCE_NOT_FOUND;
        return ResponseEntity.status(HttpStatus.valueOf(ec.getHttpStatus()))
                .body(ErrorResponse.of(ec, resolveMessage(ec, null)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> ErrorDetail.builder()
                        .field(err.getField())
                        .issue(err.getDefaultMessage())
                        .build())
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        ErrorCode.INVALID_INPUT,
                        resolveMessage(ErrorCode.INVALID_INPUT, null),
                        details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        resolveMessage(ErrorCode.INTERNAL_SERVER_ERROR, null)));
    }

    private String resolveMessage(ErrorCode ec, Object[] args) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(ec.getCode(), args, ec.getMessage(), locale);
        } catch (Exception ex) {
            return ec.getMessage();
        }
    }

    private Object[] extractArgs(BaseBusinessException ex) {
        if (ex instanceof BusinessException be) {
            Object[] args = be.getArgs();
            return args == null || args.length == 0 ? null : args;
        }
        return null;
    }
}
