package com.pwb.voice.core.exception;

import com.pwb.kernel.exception.ErrorCode;

public enum VoiceErrorCode implements ErrorCode {

    VOICE_TAG_NOT_FOUND      ("VOICE_001", "Voice tag not found.",                  404),
    SONG_NOT_FOUND           ("VOICE_002", "Song not found.",                       404),
    INVALID_AUDIO_FORMAT     ("VOICE_003", "Invalid audio format.",                 400),
    FILE_TOO_LARGE           ("VOICE_004", "File size exceeds maximum allowed.",    413),
    TTS_GENERATION_FAILED    ("VOICE_005", "Text-to-speech generation failed.",     500),
    AUDIO_PROCESSING_FAILED  ("VOICE_006", "Audio processing failed.",              500),
    INVALID_INTERVAL         ("VOICE_007", "Invalid interval value.",               400),
    DUPLICATE_VOICE_TAG_NAME ("VOICE_008", "Voice tag name already exists.",        409),
    SONG_NOT_READY           ("VOICE_009", "Song is not ready for streaming.",      409),
    ACCESS_DENIED_PRO_ONLY   ("VOICE_010", "This feature is available for PRO only.", 403),
    VOICE_TAG_IN_USE         ("VOICE_011", "Voice tag is currently in use.",        409),
    SONG_ALREADY_PROCESSED   ("VOICE_012", "Song has already been processed.",      409),
    INVALID_AUDIO_DURATION   ("VOICE_013", "Audio duration exceeds maximum.",       400);

    VoiceErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    private final String code;
    private final String message;
    private final int httpStatus;

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return message;
    }
}
