package com.pwb.liveroom.application.exception;

import com.pwb.shared.exception.ErrorCategory;
import com.pwb.shared.exception.ErrorCode;


public enum LiveroomErrorCode implements ErrorCode {

    ROOM_NOT_FOUND               (ErrorCategory.NOT_FOUND,         "LR_001", "Room not found."),
    ROOM_ENDED                   (ErrorCategory.CONFLICT,          "LR_002", "Room has ended."),
    ROOM_NAME_DUPLICATE          (ErrorCategory.CONFLICT,          "LR_003", "You already own a room with this name."),
    ROOM_NAME_INVALID            (ErrorCategory.VALIDATION,        "LR_004", "Room name is invalid."),
    ROOM_CODE_GENERATION_FAILED  (ErrorCategory.INTERNAL,          "LR_005", "Could not allocate a unique room code."),
    ROOM_CODE_LOOKUP_THROTTLED   (ErrorCategory.TOO_MANY_REQUESTS, "LR_006", "Too many room code attempts. Please try again later."),
    CANNOT_REOPEN                (ErrorCategory.CONFLICT,          "LR_007", "Only ended rooms can be reopened."),
    UNDO_WINDOW_EXPIRED          (ErrorCategory.CONFLICT,          "LR_008", "The undo window has expired."),
    GRACE_INVALID                (ErrorCategory.VALIDATION,        "LR_009", "Grace period must be between 30 and 1800 seconds."),
    ROOM_FORCE_ENDED_ROLE_CHANGE (ErrorCategory.CONFLICT,          "LR_010", "Room ended due to owner role change."),

    NOT_OWNER                    (ErrorCategory.FORBIDDEN,         "LR_020", "Only the owner can perform this action."),
    SELF_JOIN_NOT_ALLOWED        (ErrorCategory.VALIDATION,        "LR_022", "You already own this room and do not need to request access."),

    REQUEST_NOT_FOUND            (ErrorCategory.NOT_FOUND,         "LR_030", "Join request not found."),
    REQUEST_EXPIRED              (ErrorCategory.CONFLICT,          "LR_031", "Join request has expired."),
    REQUEST_LOCKED               (ErrorCategory.FORBIDDEN,         "LR_032", "You have been rejected 3 times and cannot send more requests to this room."),
    DUPLICATE_REQUEST            (ErrorCategory.CONFLICT,          "LR_033", "You already have a pending request."),
    ALREADY_APPROVED             (ErrorCategory.CONFLICT,          "LR_034", "You are already approved for this room. Join it directly."),

    ROOM_FULL                    (ErrorCategory.CONFLICT,          "LR_040", "Room is full. Please try later."),
    ALREADY_IN_ROOM              (ErrorCategory.CONFLICT,          "LR_041", "You are already in this room."),
    NOT_IN_SESSION               (ErrorCategory.FORBIDDEN,         "LR_042", "You are not in this room."),
    PARTICIPANT_NOT_FOUND        (ErrorCategory.NOT_FOUND,         "LR_043", "Participant not found in this room."),
    APPROVAL_REQUIRED            (ErrorCategory.FORBIDDEN,         "LR_044", "You need the owner's approval before joining this room."),

    SELF_KICK_NOT_ALLOWED        (ErrorCategory.VALIDATION,        "LR_050", "You cannot kick yourself."),
    KICKED_COOLDOWN              (ErrorCategory.TOO_MANY_REQUESTS, "LR_051", "You were kicked from this room. Please try again later."),
    MIC_MUTE_COOLDOWN            (ErrorCategory.TOO_MANY_REQUESTS, "LR_052", "You cannot unmute your microphone yet."),

    CHAT_EMPTY                   (ErrorCategory.VALIDATION,        "LR_060", "Chat message content is empty."),
    CHAT_TOO_LONG                (ErrorCategory.VALIDATION,        "LR_061", "Chat message exceeds maximum length."),
    TRACK_COMMENT_EMPTY          (ErrorCategory.VALIDATION,        "LR_062", "Track comment content is empty."),
    TRACK_COMMENT_TOO_LONG       (ErrorCategory.VALIDATION,        "LR_063", "Track comment exceeds maximum length."),
    TRACK_COMMENT_SONG_MISMATCH  (ErrorCategory.CONFLICT,          "LR_064", "That song is no longer playing in this room."),

    MUSIC_NOT_OWN_SONG           (ErrorCategory.FORBIDDEN,         "LR_070", "You can only select your own songs."),
    MUSIC_NOT_READY              (ErrorCategory.CONFLICT,          "LR_071", "The song is not ready to play yet."),
    MUSIC_NOT_PLAYING            (ErrorCategory.CONFLICT,          "LR_072", "No song is currently playing in this room."),
    MUSIC_INVALID_POSITION       (ErrorCategory.VALIDATION,        "LR_073", "Playback position is out of range."),
    MUSIC_INVALID_VOLUME         (ErrorCategory.VALIDATION,        "LR_074", "Volume must be between 0 and 100."),
    MUSIC_STATE_CONFLICT         (ErrorCategory.CONFLICT,          "LR_075", "Music was controlled by another user. Please try again."),
    MUSIC_OWNER_ABSENT           (ErrorCategory.FORBIDDEN,         "LR_076", "Owner has left. Music stays paused until the owner returns."),
    MUSIC_SONG_LOAD_FAILED       (ErrorCategory.INTERNAL,          "LR_077", "Failed to load the audio file."),

    WS_UNAUTHORIZED              (ErrorCategory.FORBIDDEN,         "LR_080", "You are not allowed to subscribe to this room."),
    WS_RATE_LIMITED              (ErrorCategory.TOO_MANY_REQUESTS, "LR_081", "You are sending too fast. Please slow down."),

    RTC_SELF_SIGNALING           (ErrorCategory.VALIDATION,        "LR_090", "You cannot send a signal to yourself."),
    RTC_PAYLOAD_TOO_LARGE        (ErrorCategory.VALIDATION,        "LR_091", "Signaling payload is too large."),
    RTC_PAYLOAD_INVALID          (ErrorCategory.VALIDATION,        "LR_092", "Signaling payload is missing or malformed.");

    LiveroomErrorCode(ErrorCategory category, String code, String defaultMessage) {
        this.category = category;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    private final ErrorCategory category;
    private final String code;
    private final String defaultMessage;

    @Override
    public ErrorCategory category() {
        return category;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}