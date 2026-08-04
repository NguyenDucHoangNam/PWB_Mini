package com.pwb.liveroom.api.dto.response;

import com.pwb.liveroom.application.view.ChatHistoryView;

import java.util.List;
import java.util.UUID;

public record ChatHistoryResponse(
        List<ChatMessageResponse> messages,
        boolean hasMore,
        UUID nextCursor
) {

    public static ChatHistoryResponse from(ChatHistoryView view) {
        return new ChatHistoryResponse(
                view.messages().stream().map(ChatMessageResponse::from).toList(),
                view.hasMore(),
                view.nextCursor()
        );
    }
}