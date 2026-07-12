package com.pwb.backend.modules.voice_tag.exception;

import com.pwb.backend.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public enum VoiceTagErrorCode implements ErrorCode {

    TTS_TEXT_TOO_LONG("TTS_TEXT_TOO_LONG", "Raw text exceeds 100 characters", HttpStatus.BAD_REQUEST),
    INVALID_LANGUAGE_CODE("INVALID_LANGUAGE_CODE", "languageCode must follow BCP-47 format", HttpStatus.BAD_REQUEST),
    VOICE_TAG_LIMIT_EXCEEDED("VOICE_TAG_LIMIT_EXCEEDED", "Maximum number of active voice tags reached", HttpStatus.BAD_REQUEST),
    VOICE_TAG_STORAGE_EXCEEDED("VOICE_TAG_STORAGE_EXCEEDED", "Total voice tag storage quota exceeded", HttpStatus.BAD_REQUEST),
    INVALID_SSML_TAG("INVALID_SSML_TAG", "SSML contains a tag or attribute outside the whitelist", HttpStatus.BAD_REQUEST),
    INVALID_VOICE_NAME("INVALID_VOICE_NAME", "voiceName is not in the allowed GCP voice whitelist", HttpStatus.BAD_REQUEST),

    FORBIDDEN_ACCESS("FORBIDDEN_ACCESS", "Voice Tag does not belong to current user", HttpStatus.FORBIDDEN),

    VOICE_TAG_NOT_FOUND("VOICE_TAG_NOT_FOUND", "Voice Tag not found", HttpStatus.NOT_FOUND),

    VOICE_TAG_ALREADY_DEFAULT("VOICE_TAG_ALREADY_DEFAULT", "Another voice tag is already the default for this user", HttpStatus.CONFLICT),
    VOICE_TAG_IN_USE("VOICE_TAG_IN_USE", "Voice Tag is currently referenced by one or more active demos", HttpStatus.CONFLICT),

    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", "Voice Tag API rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),

    TTS_SERVICE_FAILED("TTS_SERVICE_FAILED", "Google Cloud Text-to-Speech call failed", HttpStatus.BAD_GATEWAY),
    TTS_SERVICE_FAILED_INVALID("TTS_SERVICE_FAILED_INVALID", "Google Cloud TTS returned a non MP3 audio payload", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}
