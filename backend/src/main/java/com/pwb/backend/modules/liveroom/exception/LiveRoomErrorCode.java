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
            HttpStatus.INTERNAL_SERVER_ERROR),

    ROOM_FULL("ROOM_FULL",
            "Room has reached the maximum number of participants",
            HttpStatus.BAD_REQUEST),

    SESSION_NOT_FOUND("SESSION_NOT_FOUND",
            "Waiting request not found for the given listener",
            HttpStatus.NOT_FOUND),

    LISTENER_NOT_IN_WAITING("LISTENER_NOT_IN_WAITING",
            "Listener is not in the waiting list",
            HttpStatus.NOT_FOUND),

    LISTENER_NOT_A_MEMBER("LISTENER_NOT_A_MEMBER",
            "Listener is not a member of the room",
            HttpStatus.NOT_FOUND),

    FORBIDDEN_NOT_HOST("FORBIDDEN_NOT_HOST",
            "Only the room host can perform this action",
            HttpStatus.FORBIDDEN),

    FORBIDDEN_LISTENER_SANDBOX("FORBIDDEN_LISTENER_SANDBOX",
            "Listener token is not allowed to access this endpoint",
            HttpStatus.FORBIDDEN),

    PLAYBACK_ROOM_NOT_LIVE("PLAYBACK_ROOM_NOT_LIVE",
            "Room is not in LIVE state",
            HttpStatus.NOT_FOUND),

    PLAYBACK_NO_ACTIVE_SOURCE("PLAYBACK_NO_ACTIVE_SOURCE",
            "Room has no active audio source",
            HttpStatus.CONFLICT),

    PLAYBACK_SEEK_OUT_OF_BOUNDS("PLAYBACK_SEEK_OUT_OF_BOUNDS",
            "currentTime is outside [0, duration]",
            HttpStatus.BAD_REQUEST);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}