package com.pwb.backend.enums;

import lombok.Getter;

import java.util.List;

@Getter
public enum MediaType {

    VOICE_TAG("voicetags", "vt-", List.of("mp3"), MediaCategory.AUDIO),
    AVATAR("avatars", "av-", List.of("jpg", "png", "webp"), MediaCategory.IMAGE),
    AUDIO_DEMO("demos", "demo-", List.of("mp3", "wav"), MediaCategory.AUDIO),
    TRACK_MASTER("tracks", "master-", List.of("flac", "wav"), MediaCategory.AUDIO);

    private final String prefix;
    private final String filePrefix;
    private final List<String> allowedExtensions;
    private final MediaCategory category;

    MediaType(String prefix, String filePrefix, List<String> allowedExtensions, MediaCategory category) {
        this.prefix = prefix;
        this.filePrefix = filePrefix;
        this.allowedExtensions = allowedExtensions;
        this.category = category;
    }

    public boolean supportsExtension(String extension) {
        if (extension == null) {
            return false;
        }
        String normalized = extension.toLowerCase().replace(".", "");
        return allowedExtensions.contains(normalized);
    }

    public enum MediaCategory {
        AUDIO,
        IMAGE,
        VIDEO,
        DOCUMENT
    }
}
