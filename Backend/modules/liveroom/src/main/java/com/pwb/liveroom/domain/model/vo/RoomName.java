package com.pwb.liveroom.domain.model.vo;

import java.util.regex.Pattern;


public final class RoomName {

    public static final int MAX_LENGTH = 100;


    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{N} .,\\-_'\"!?]+$");

    private final String value;
    private final String normalized;

    private RoomName(String value, String normalized) {
        this.value = value;
        this.normalized = normalized;
    }

    public static RoomName of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("roomName must not be null");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("roomName must not be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("roomName must be at most " + MAX_LENGTH + " characters");
        }
        if (!ALLOWED.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("roomName contains unsupported characters");
        }
        return new RoomName(trimmed, normalize(trimmed));
    }


    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase();
    }

    public String value() {
        return value;
    }

    public String normalized() {
        return normalized;
    }

    @Override
    public String toString() {
        return value;
    }
}