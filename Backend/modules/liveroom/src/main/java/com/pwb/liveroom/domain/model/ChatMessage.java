package com.pwb.liveroom.domain.model;

import com.pwb.shared.domain.DomainBaseEntity;

import java.time.Instant;
import java.util.UUID;


public final class ChatMessage extends DomainBaseEntity {


    public static final int MAX_CONTENT_LENGTH = 500;


    public static final int DEFAULT_HISTORY_SIZE = 200;

    private final UUID id;
    private final UUID roomId;
    private final UUID cycleId;
    private final UUID userId;
    private final String userEmail;
    private final String content;
    private final Instant sentAt;

    private ChatMessage(
            UUID id,
            UUID roomId,
            UUID cycleId,
            UUID userId,
            String userEmail,
            String content,
            Instant sentAt
    ) {
        this.id = id;
        this.roomId = roomId;
        this.cycleId = cycleId;
        this.userId = userId;
        this.userEmail = userEmail;
        this.content = content;
        this.sentAt = sentAt;
    }


    public static ChatMessage send(
            UUID roomId,
            UUID cycleId,
            UUID userId,
            String userEmail,
            String content,
            Instant sentAt
    ) {
        if (roomId == null || cycleId == null || userId == null) {
            throw new IllegalArgumentException("roomId, cycleId and userId must not be null");
        }
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("userEmail must not be blank");
        }
        String normalized = normalizeContent(content);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (lengthOf(normalized) > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("content must be at most " + MAX_CONTENT_LENGTH + " characters");
        }
        return new ChatMessage(null, roomId, cycleId, userId, userEmail, normalized, sentAt);
    }

    public static ChatMessage rehydrate(
            UUID id,
            UUID roomId,
            UUID cycleId,
            UUID userId,
            String userEmail,
            String content,
            Instant sentAt
    ) {
        return new ChatMessage(id, roomId, cycleId, userId, userEmail, content, sentAt);
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

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getCycleId() {
        return cycleId;
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

    public Instant getSentAt() {
        return sentAt;
    }

    public boolean isNew() {
        return id == null;
    }
}