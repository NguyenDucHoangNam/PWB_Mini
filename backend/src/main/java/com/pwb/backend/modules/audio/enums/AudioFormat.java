package com.pwb.backend.modules.audio.enums;

import java.util.Set;

public enum AudioFormat {
    WAV("audio/wav", "wav", Set.of("wav")),
    FLAC("audio/flac", "flac", Set.of("flac")),
    MP3("audio/mpeg", "mp3", Set.of("mp3"));

    private final String contentType;
    private final String extension;
    private final Set<String> acceptedExtensions;

    AudioFormat(String contentType, String extension, Set<String> acceptedExtensions) {
        this.contentType = contentType;
        this.extension = extension;
        this.acceptedExtensions = acceptedExtensions;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public Set<String> acceptedExtensions() {
        return acceptedExtensions;
    }

    public static AudioFormat fromContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String normalized = contentType.trim().toLowerCase();
        for (AudioFormat format : values()) {
            if (format.contentType.equalsIgnoreCase(normalized)
                    || ("audio/" + format.extension).equalsIgnoreCase(normalized)) {
                return format;
            }
        }
        return null;
    }

    public static AudioFormat fromFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        for (AudioFormat format : values()) {
            if (format.acceptedExtensions.contains(ext)) {
                return format;
            }
        }
        return null;
    }
}