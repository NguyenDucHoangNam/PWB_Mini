package com.pwb.liveroom.application.support;

import com.pwb.liveroom.application.view.ChatMessageView;
import com.pwb.liveroom.domain.model.ChatMessage;
import org.springframework.stereotype.Component;

@Component
public class ChatViewFactory {

    public ChatMessageView toView(ChatMessage message) {
        return new ChatMessageView(
                message.getId(),
                message.getRoomId(),
                message.getCycleId(),
                message.getUserId(),
                message.getUserEmail(),
                message.getContent(),
                message.getSentAt()
        );
    }
}