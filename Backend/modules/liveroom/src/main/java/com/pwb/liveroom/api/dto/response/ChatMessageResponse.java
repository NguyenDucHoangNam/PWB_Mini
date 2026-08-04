package com.pwb.liveroom.api.dto.response;

import com.pwb.liveroom.application.view.ChatMessageView;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID roomId,
        UUID cycleId,
        UUID userId,
        String userEmail,
        String content,
        Instant sentAt
) {

    public static ChatMessageResponse from(ChatMessageView view) {
        return new ChatMessageResponse(
                view.id(),
                view.roomId(),
                view.cycleId(),
                view.userId(),
                view.userEmail(),
                view.content(),
                view.sentAt()
        );
    }
}