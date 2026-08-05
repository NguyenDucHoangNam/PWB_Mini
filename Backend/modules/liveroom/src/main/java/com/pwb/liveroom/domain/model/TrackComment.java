package com.pwb.liveroom.domain.model;

import java.time.Instant;
import java.util.UUID;


public final class TrackComment {

    private final UUID id;
    private final UUID cycleId;
    private final UUID songId;
    private final UUID userId;
    private final String userEmail;
    private final String content;
    private final double positionSeconds;
    private final Instant createdAt;

    private TrackComment(
            UUID id,
            UUID cycleId,
            UUID songId,
            UUID userId,
            String userEmail,
            String content,
            double positionSeconds,
            Instant createdAt
    ) {
        this.id = id;
        this.cycleId = cycleId;
        this.songId = songId;
        this.userId = userId;
        this.userEmail = userEmail;
        this.content = content;
        this.positionSeconds = positionSeconds;
        this.createdAt = createdAt;
    }

    public static TrackComment post(
            UUID cycleId,
            UUID songId,
            UUID userId,
            String userEmail,
            String content,
            double positionSeconds,
            Instant createdAt
    ) {
        if (cycleId == null || songId == null || userId == null) {
            throw new IllegalArgumentException("cycleId, songId and userId must not be null");
        }
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("userEmail must not be blank");
        }
        String normalized = normalizeContent(content);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        return new TrackComment(
                UUID.randomUUID(),
                cycleId,
                songId,
                userId,
                userEmail,
                normalized,
                Math.max(0d, positionSeconds),
                createdAt);
    }

    public static String normalizeContent(String content) {
        return content == null ? "" : content.strip();
    }

    public static int lengthOf(String content) {
        return content.codePointCount(0, content.length());
    }

    public UUID getId() {
        return id;
    }

    public UUID getCycleId() {
        return cycleId;
    }

    public UUID getSongId() {
        return songId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getContent() {
        return content;
    }

    public double getPositionSeconds() {
        return positionSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}