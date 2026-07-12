package com.pwb.backend.modules.liveroom.exception;

import com.pwb.backend.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public enum LiveRoomErrorCode implements ErrorCode {

    FORBIDDEN_ACCESS("FORBIDDEN_ACCESS",
            "Producer role required to create a live room",
            HttpStatus.FORBIDDEN),

    ROOM_ALREADY_ACTIVE("ROOM_ALREADY_ACTIVE",
            "You already have an active Live Room",
            HttpStatus.CONFLICT),

    ROOM_CODE_COLLISION_FAILED("ROOM_CODE_COLLISION_FAILED",
            "Failed to allocate a unique room code, please retry",
            HttpStatus.INTERNAL_SERVER_ERROR),

    ROOM_NOT_FOUND("ROOM_NOT_FOUND",
            "Live Room not found",
            HttpStatus.NOT_FOUND),

    HOST_DISCONNECT_GRACE_FAILED("HOST_DISCONNECT_GRACE_FAILED",
            "Failed to mark host disconnect grace window",
            HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}