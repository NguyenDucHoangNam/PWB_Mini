package com.pwb.audio.application.exception;

import com.pwb.shared.exception.ErrorCategory;
import com.pwb.shared.exception.ErrorCode;

public enum AudioErrorCode implements ErrorCode {

    SONG_NOT_FOUND             (ErrorCategory.NOT_FOUND,         "AUDIO_001", "Song not found."),
    VOICE_TAG_NOT_FOUND        (ErrorCategory.NOT_FOUND,         "AUDIO_002", "Voice tag not found."),
    SONG_TAG_CONFIG_NOT_FOUND  (ErrorCategory.NOT_FOUND,         "AUDIO_003", "Song tag configuration not found."),
    INVALID_AUDIO_FORMAT       (ErrorCategory.VALIDATION,        "AUDIO_004", "Invalid audio format. Supported formats: mp3, wav, flac."),
    FILE_TOO_LARGE             (ErrorCategory.VALIDATION,        "AUDIO_005", "File size exceeds maximum allowed limit."),
    DUPLICATE_VOICE_TAG_NAME  (ErrorCategory.CONFLICT,          "AUDIO_006", "Voice tag name already exists."),
    VOICE_TAG_IN_USE           (ErrorCategory.CONFLICT,          "AUDIO_007", "Voice tag is currently in use by a song configuration."),
    SONG_NOT_UPLOADED         (ErrorCategory.BUSINESS,          "AUDIO_008", "Song must be uploaded before processing."),
    PROCESSING_ALREADY_STARTED(ErrorCategory.BUSINESS,          "AUDIO_009", "Song processing has already been started."),
    STORAGE_ERROR             (ErrorCategory.INTERNAL,          "AUDIO_010", "Failed to interact with storage service."),
    TTS_ERROR                 (ErrorCategory.INTERNAL,          "AUDIO_011", "Text-to-speech synthesis failed."),
    PROCESSING_FAILED         (ErrorCategory.INTERNAL,          "AUDIO_013", "Audio processing failed."),
    UNAUTHORIZED_ACCESS       (ErrorCategory.FORBIDDEN,         "AUDIO_012", "You do not have permission to access this resource.");

    AudioErrorCode(ErrorCategory category, String code, String defaultMessage) {
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
