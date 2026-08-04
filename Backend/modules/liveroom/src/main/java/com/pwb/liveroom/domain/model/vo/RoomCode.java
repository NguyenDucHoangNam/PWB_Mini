package com.pwb.liveroom.domain.model.vo;

import java.util.regex.Pattern;


public final class RoomCode {

    public static final int LENGTH = 6;
    public static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9]{" + LENGTH + "}$");

    private final String value;

    private RoomCode(String value) {
        this.value = value;
    }

    public static RoomCode of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("roomCode must not be null");
        }
        String normalized = raw.trim().toUpperCase();
        if (!VALID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("roomCode must be " + LENGTH + " characters of A-Z0-9");
        }
        return new RoomCode(normalized);
    }

    public String value() {
        return value;
    }


    public String display() {
        return value.substring(0, 3) + "-" + value.substring(3);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RoomCode roomCode && value.equals(roomCode.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}