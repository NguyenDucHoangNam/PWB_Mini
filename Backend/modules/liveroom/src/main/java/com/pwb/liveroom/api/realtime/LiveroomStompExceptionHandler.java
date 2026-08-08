package com.pwb.liveroom.api.realtime;

import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.shared.exception.BusinessException;
import com.pwb.shared.exception.ErrorCategory;
import com.pwb.shared.exception.ErrorCode;
import com.pwb.shared.exception.SysErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.Locale;
import java.util.Map;


@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class LiveroomStompExceptionHandler {


    public static final String ERROR_QUEUE = "/queue/liveroom/errors";

    private final MessageSource messageSource;


    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(destinations = ERROR_QUEUE, broadcast = false)
    public ApiResponse<Void> handleBusiness(BusinessException ex) {
        if (ex.getCategory() == ErrorCategory.INTERNAL) {
            log.error("Realtime business failure: code={}", ex.getCode(), ex);
        } else {
            log.debug("Realtime business rejection: code={}", ex.getCode());
        }
        return error(ex.getErrorCode(), args(ex.getDetails()));
    }


    @MessageExceptionHandler(OptimisticLockingFailureException.class)
    @SendToUser(destinations = ERROR_QUEUE, broadcast = false)
    public ApiResponse<Void> handleOptimisticLocking(OptimisticLockingFailureException ex) {
        log.debug("Concurrent playback update rejected: {}", ex.getMessage());
        return error(LiveroomErrorCode.MUSIC_STATE_CONFLICT, null);
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser(destinations = ERROR_QUEUE, broadcast = false)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("Rejected realtime frame: {}", ex.getMessage());
        return error(SysErrorCode.INVALID_REQUEST, null);
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = ERROR_QUEUE, broadcast = false)
    public ApiResponse<Void> handleGeneric(Exception ex) {
        log.error("Unhandled realtime exception: ", ex);
        return error(SysErrorCode.INTERNAL_SERVER_ERROR, null);
    }

    private ApiResponse<Void> error(ErrorCode errorCode, Object[] args) {
        return ApiResponse.error(errorCode, resolve(errorCode, args));
    }


    private Object[] args(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        return details.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toArray();
    }

    private String resolve(ErrorCode errorCode, Object[] args) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(errorCode.code(), args, errorCode.defaultMessage(), locale);
        } catch (Exception ex) {
            log.debug("Message resolution failed for key={}", errorCode.code(), ex);
            return errorCode.defaultMessage();
        }
    }
}