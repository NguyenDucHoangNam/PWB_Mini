package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.command.LoadChatHistoryCommand;
import com.pwb.liveroom.application.view.ChatHistoryView;

public interface LoadChatHistoryUseCase {

    ChatHistoryView execute(LoadChatHistoryCommand command);
}