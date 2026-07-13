package com.pwb.backend.modules.liveroom.exception;

import com.pwb.backend.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public enum ChatErrorCode implements ErrorCode {

    INVALID_CHAT_FRAME("INVALID_CHAT_FRAME",
            "Chat frame is malformed or missing required fields",
            HttpStatus.BAD_REQUEST),

    INVALID_EMOJI("INVALID_EMOJI",
            "Reaction emoji is not in the allowed whitelist",
            HttpStatus.BAD_REQUEST),

    TEXT_TOO_LONG("TEXT_TOO_LONG",
            "Text message exceeds the maximum allowed length",
            HttpStatus.BAD_REQUEST),

    CHAT_SENDER_NOT_IN_ROOM("CHAT_SENDER_NOT_IN_ROOM",
            "Sender is not a member of the room",
            HttpStatus.FORBIDDEN),

    CHAT_RATE_LIMITED("CHAT_RATE_LIMITED",
            "Too many chat frames; please slow down",
            HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}
