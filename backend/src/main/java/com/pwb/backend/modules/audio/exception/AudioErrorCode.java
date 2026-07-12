package com.pwb.backend.modules.audio.exception;

import com.pwb.backend.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public enum AudioErrorCode implements ErrorCode {

    UNSUPPORTED_AUDIO_FORMAT("UNSUPPORTED_AUDIO_FORMAT", "Audio format is not supported", HttpStatus.BAD_REQUEST),
    FILE_SIZE_MISMATCH("FILE_SIZE_MISMATCH", "Uploaded file size does not match the expected value", HttpStatus.BAD_REQUEST),
    FILE_NOT_FOUND_ON_S3("FILE_NOT_FOUND_ON_S3", "Uploaded file was not found on object storage", HttpStatus.BAD_REQUEST),

    INVALID_S3_KEY_OWNER("INVALID_S3_KEY_OWNER", "s3Key does not belong to the current user", HttpStatus.FORBIDDEN),
    DEMO_QUOTA_EXCEEDED("DEMO_QUOTA_EXCEEDED", "Maximum number of active demos reached", HttpStatus.FORBIDDEN),
    AUDIO_QUOTA_EXCEEDED("AUDIO_QUOTA_EXCEEDED", "Total audio storage quota exceeded", HttpStatus.FORBIDDEN),

    INVALID_AUDIO_CONTENT("INVALID_AUDIO_CONTENT", "Uploaded file is not a valid audio stream", HttpStatus.INTERNAL_SERVER_ERROR),

    DEMO_NOT_FOUND("DEMO_NOT_FOUND", "Demo not found", HttpStatus.NOT_FOUND),
    DEMO_AUDIO_PROCESSING_FAILED("DEMO_AUDIO_PROCESSING_FAILED", "Audio processing failed", HttpStatus.INTERNAL_SERVER_ERROR),
    DEMO_NOT_ACTIVE("DEMO_NOT_ACTIVE", "Demo is not in ACTIVE state and cannot be streamed", HttpStatus.CONFLICT),

    PLAYLIST_SIGNATURE_INVALID("PLAYLIST_SIGNATURE_INVALID",
            "Playlist signature is invalid or missing", HttpStatus.FORBIDDEN),
    PLAYLIST_SIGNATURE_EXPIRED("PLAYLIST_SIGNATURE_EXPIRED",
            "Playlist signature has expired", HttpStatus.FORBIDDEN);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}