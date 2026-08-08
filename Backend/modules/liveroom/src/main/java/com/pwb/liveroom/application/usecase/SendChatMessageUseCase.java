package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.command.SendChatMessageCommand;
import com.pwb.liveroom.application.view.ChatMessageView;

public interface SendChatMessageUseCase {

    ChatMessageView execute(SendChatMessageCommand command);
}