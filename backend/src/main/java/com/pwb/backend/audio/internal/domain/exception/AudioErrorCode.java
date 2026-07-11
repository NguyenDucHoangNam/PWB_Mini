package com.pwb.backend.audio.internal.domain.exception;

import com.pwb.backend.shared.exception.ErrorCodeLike;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum AudioErrorCode implements ErrorCodeLike {

    UNSUPPORTED_AUDIO_FORMAT("UNSUPPORTED_AUDIO_FORMAT", "Audio format is not supported. Accepted formats: wav, flac, mp3", HttpStatus.BAD_REQUEST),
    FILE_NOT_FOUND_ON_S3("FILE_NOT_FOUND_ON_S3", "Audio file was not found on object storage", HttpStatus.BAD_REQUEST),
    FILE_SIZE_MISMATCH("FILE_SIZE_MISMATCH", "Uploaded file size does not match the expected size", HttpStatus.BAD_REQUEST),
    INVALID_S3_KEY_OWNER("INVALID_S3_KEY_OWNER", "You do not own this S3 key", HttpStatus.FORBIDDEN),
    DEMO_QUOTA_EXCEEDED("DEMO_QUOTA_EXCEEDED", "You have reached the maximum number of active demos (20)", HttpStatus.FORBIDDEN),
    AUDIO_QUOTA_EXCEEDED("AUDIO_QUOTA_EXCEEDED", "You have reached the maximum storage quota for active audio (5 GB)", HttpStatus.FORBIDDEN),
    INVALID_AUDIO_CONTENT("INVALID_AUDIO_CONTENT", "Audio file failed content validation", HttpStatus.INTERNAL_SERVER_ERROR),
    UPLOAD_CLAIM_EXPIRED("UPLOAD_CLAIM_EXPIRED", "Upload claim has expired or is missing. Please re-request a presigned upload URL", HttpStatus.BAD_REQUEST),
    FFMPEG_PROCESS_FAILED("FFMPEG_PROCESS_FAILED", "FFmpeg processing failed", HttpStatus.INTERNAL_SERVER_ERROR),
    FFPROBE_VALIDATION_FAILED("FFPROBE_VALIDATION_FAILED", "Audio file failed ffprobe validation (codec, sample-rate, duration, or cover-art check)", HttpStatus.BAD_REQUEST),
    HLS_SEGMENTATION_FAILED("HLS_SEGMENTATION_FAILED", "Failed to segment audio into HLS", HttpStatus.INTERNAL_SERVER_ERROR),
    AES_KEY_GENERATION_FAILED("AES_KEY_GENERATION_FAILED", "Failed to generate or encrypt AES key for demo", HttpStatus.INTERNAL_SERVER_ERROR),

    TTS_TEXT_TOO_LONG("TTS_TEXT_TOO_LONG", "Voice tag text exceeds 100 raw characters (excluding SSML tags)", HttpStatus.BAD_REQUEST),
    INVALID_LANGUAGE_CODE("INVALID_LANGUAGE_CODE", "Language code must follow BCP-47 format (e.g. vi-VN, en-US)", HttpStatus.BAD_REQUEST),
    INVALID_VOICE_NAME("INVALID_VOICE_NAME", "Voice name is not in the GCP allowlist", HttpStatus.BAD_REQUEST),
    INVALID_SSML_TAG("INVALID_SSML_TAG", "SSML input contains tags or attributes outside the whitelist", HttpStatus.BAD_REQUEST),
    VOICE_TAG_LIMIT_EXCEEDED("VOICE_TAG_LIMIT_EXCEEDED", "Active voice tag cap of 50 reached for this account", HttpStatus.BAD_REQUEST),
    VOICE_TAG_STORAGE_EXCEEDED("VOICE_TAG_STORAGE_EXCEEDED", "Total voice tag storage exceeds 200 MB cap", HttpStatus.BAD_REQUEST),
    TTS_SERVICE_FAILED("TTS_SERVICE_FAILED", "Google Cloud TTS request failed", HttpStatus.BAD_GATEWAY),
    TTS_SERVICE_FAILED_INVALID("TTS_SERVICE_FAILED_INVALID", "Google Cloud TTS response bytes failed integrity check", HttpStatus.BAD_GATEWAY),
    VOICE_TAG_NOT_FOUND("VOICE_TAG_NOT_FOUND", "Voice tag does not exist", HttpStatus.NOT_FOUND),
    VOICE_TAG_IN_USE("VOICE_TAG_IN_USE", "Voice tag is referenced by at least one ACTIVE demo and cannot be deleted", HttpStatus.CONFLICT),
    VOICE_TAG_ALREADY_DEFAULT("VOICE_TAG_ALREADY_DEFAULT", "Another voice tag for this account is already marked as default", HttpStatus.CONFLICT),

    DEMO_NOT_FOUND("DEMO_NOT_FOUND", "Demo does not exist or has been deleted", HttpStatus.NOT_FOUND),
    DEMO_NOT_ACTIVE("DEMO_NOT_ACTIVE", "Demo is not in ACTIVE status and cannot be distributed", HttpStatus.CONFLICT),
    INVALID_RECIPIENT_EMAIL("INVALID_RECIPIENT_EMAIL", "Recipient email is in a blacklisted disposable-mail domain", HttpStatus.BAD_REQUEST),
    SHARE_QUOTA_EXCEEDED("SHARE_QUOTA_EXCEEDED", "Per-user share quota exceeded (recipients or distributions limit reached)", HttpStatus.TOO_MANY_REQUESTS),
    DISTRIBUTION_NOT_FOUND("DISTRIBUTION_NOT_FOUND", "Distribution does not exist", HttpStatus.NOT_FOUND),

    LINK_REVOKED("LINK_REVOKED", "This shared link has been revoked by the producer", HttpStatus.FORBIDDEN),
    IP_MISMATCH("IP_MISMATCH", "Client IP does not match the CIDR recorded for this stream session, or session jti has been revoked", HttpStatus.FORBIDDEN),
    LINK_NOT_FOUND("LINK_NOT_FOUND", "Shared link for this token does not exist", HttpStatus.NOT_FOUND),
    LINK_EXPIRED("LINK_EXPIRED", "Shared link has permanently expired", HttpStatus.GONE),
    STREAM_SESSION_INVALID("STREAM_SESSION_INVALID", "Stream session cookie is missing, invalid or expired", HttpStatus.FORBIDDEN),
    DOWNLOAD_PROHIBITED("DOWNLOAD_PROHIBITED", "Producer has disabled original file download for this link, or the link is revoked", HttpStatus.FORBIDDEN),
    ORIGINAL_FILE_MISSING("ORIGINAL_FILE_MISSING", "Original audio file is missing from object storage", HttpStatus.NOT_FOUND),
    DOWNLOAD_QUOTA_EXCEEDED("DOWNLOAD_QUOTA_EXCEEDED", "Download quota exceeded for this session or share token", HttpStatus.TOO_MANY_REQUESTS),
    S3_PRESIGN_FAILED("S3_PRESIGN_FAILED", "Failed to generate S3 pre-signed URL", HttpStatus.SERVICE_UNAVAILABLE),
    FORBIDDEN_ACCESS("FORBIDDEN_ACCESS", "Caller is not the owner of the requested resource", HttpStatus.FORBIDDEN),
    WS_TOKEN_INVALID("WS_TOKEN_INVALID", "Temporary WebSocket access token is missing, invalid or expired", HttpStatus.FORBIDDEN),
    WS_SUBSCRIBE_DENIED("WS_SUBSCRIBE_DENIED", "Caller is not a participant of the requested shared thread", HttpStatus.FORBIDDEN);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    AudioErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }
}