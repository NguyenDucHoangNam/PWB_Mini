package com.pwb.infra.storage.util;

import com.pwb.infra.storage.exception.StorageErrorCode;
import com.pwb.infra.storage.exception.StorageException;
import lombok.experimental.UtilityClass;

import java.util.Set;
import java.util.regex.Pattern;

@UtilityClass
public class MediaTypeUtils {

    private static final Pattern VALID_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9/_\\-\\.]+$");
    private static final int MAX_KEY_LENGTH = 1024;

    /** Enough for every signature {@link #detectFromBytes} knows about, with room to spare. */
    public static final int MAGIC_BYTE_COUNT = 16;

    private static final Set<String> AUDIO_TYPES =
            Set.of("audio/mpeg", "audio/wav", "audio/flac", "audio/ogg");

    public String detectFromBytes(byte[] head) {
        if (head == null || head.length < 4) {
            return "application/octet-stream";
        }

        if (head.length >= 3 && (head[0] & 0xFF) == 0x49 && (head[1] & 0xFF) == 0x44 && (head[2] & 0xFF) == 0x33) {
            return "audio/mpeg";
        }

        if (head.length >= 4 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0) {
            return "audio/mpeg";
        }

        if (head.length >= 4
                && (head[0] & 0xFF) == 0x52 && (head[1] & 0xFF) == 0x49
                && (head[2] & 0xFF) == 0x46 && (head[3] & 0xFF) == 0x46) {
            return "audio/wav";
        }

        if (head.length >= 4
                && (head[0] & 0xFF) == 0x66 && (head[1] & 0xFF) == 0x4C
                && (head[2] & 0xFF) == 0x61 && (head[3] & 0xFF) == 0x43) {
            return "audio/flac";
        }

        if (head.length >= 4 && (head[0] & 0xFF) == 0x4F && (head[1] & 0xFF) == 0x67
                && (head[2] & 0xFF) == 0x67 && (head[3] & 0xFF) == 0x53) {
            return "audio/ogg";
        }

        if (head.length >= 4
                && (head[0] & 0xFF) == 0x1A && (head[1] & 0xFF) == 0x45
                && (head[2] & 0xFF) == 0xDF && (head[3] & 0xFF) == 0xA3) {
            return "video/x-matroska";
        }

        return "application/octet-stream";
    }

    /**
     * Whether the leading bytes are one of the audio containers this system accepts.
     *
     * <p>Deliberately reads the bytes rather than a declared type: a content type is chosen by whoever
     * uploaded the file, and an extension is chosen by whoever named it, so neither says anything about
     * what the file actually is.
     */
    public boolean isAudio(byte[] head) {
        return AUDIO_TYPES.contains(detectFromBytes(head));
    }

    public void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new StorageException(StorageErrorCode.STORAGE_INVALID_KEY);
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new StorageException(StorageErrorCode.STORAGE_INVALID_KEY);
        }
        if (key.contains("..")) {
            throw new StorageException(StorageErrorCode.STORAGE_INVALID_KEY);
        }
        if (!VALID_KEY_PATTERN.matcher(key).matches()) {
            throw new StorageException(StorageErrorCode.STORAGE_INVALID_KEY);
        }
    }
}
