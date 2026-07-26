package com.pwb.liveroom.core.model;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;

import java.util.Map;

import static java.util.Map.entry;

public final class LiveroomErrorCodeMapper {

    private static final Map<String, ErrorCode> DOMAIN_ERROR_CODE_MAP = Map.ofEntries(
            entry("LIVEROOM_FULL", ErrorCode.LIVEROOM_FULL),
            entry("LIVEROOM_NOT_ACTIVE", ErrorCode.LIVEROOM_ALREADY_ENDED),
            entry("LIVEROOM_PAUSED", ErrorCode.LIVEROOM_ALREADY_ENDED),
            entry("LIVEROOM_ALREADY_ENDED", ErrorCode.LIVEROOM_ALREADY_ENDED),
            entry("LIVEROOM_CAPACITY_INVALID", ErrorCode.LIVEROOM_INVALID_CAPACITY),
            entry("LIVEROOM_CODE_INVALID_LENGTH", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_CODE_INVALID_CHARS", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_DISPLAY_NAME_REQUIRED", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_DISPLAY_NAME_TOO_LONG", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_ROLE_REQUIRED", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_ROLE_INVALID", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_LEFT_BEFORE_JOIN", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_ALREADY_LEFT_MEDIA", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_USER_ID_REQUIRED", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_CAPACITY_LOWER_THAN_CURRENT", ErrorCode.LIVEROOM_INVALID_CAPACITY),
            entry("LIVEROOM_DECIDED_BY_REQUIRED", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_JOIN_REQUEST_NOT_OWNER", ErrorCode.LIVEROOM_JOIN_REQUEST_NOT_OWNER),
            entry("LIVEROOM_JOIN_REQUEST_NOT_PENDING", ErrorCode.LIVEROOM_JOIN_REQUEST_NOT_PENDING),
            entry("LIVEROOM_JOIN_REQUEST_MESSAGE_TOO_LONG", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_JOIN_REQUEST_REASON_TOO_LONG", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_TITLE_REQUIRED", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_TITLE_TOO_LONG", ErrorCode.INVALID_INPUT),
            entry("LIVEROOM_HOST_ALREADY_ACTIVE", ErrorCode.LIVEROOM_HOST_ALREADY_ACTIVE),
            entry("LIVEROOM_MODE_NOT_JOINABLE", ErrorCode.LIVEROOM_MODE_NOT_JOINABLE),
            entry("LIVEROOM_NOT_REQUIRE_APPROVAL", ErrorCode.LIVEROOM_NOT_REQUIRE_APPROVAL),
            entry("LIVEROOM_JOIN_REQUEST_INVALID_DECISION", ErrorCode.LIVEROOM_JOIN_REQUEST_INVALID_DECISION)
    );

    private LiveroomErrorCodeMapper() {
    }

    public static BusinessException mapDomainException(LiveroomDomainException ex) {
        ErrorCode ec = DOMAIN_ERROR_CODE_MAP.getOrDefault(ex.getErrorKey(), ErrorCode.INVALID_INPUT);
        return new BusinessException(ec);
    }
}
