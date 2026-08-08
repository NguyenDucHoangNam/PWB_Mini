package com.pwb.liveroom.application.view;

import java.util.List;
import java.util.UUID;


public record ChatHistoryView(
        List<ChatMessageView> messages,
        boolean hasMore,
        UUID nextCursor
) {
}