package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.command.ControlPlaybackCommand;
import com.pwb.liveroom.application.view.PlaybackStateView;

public interface ControlPlaybackUseCase {

    PlaybackStateView execute(ControlPlaybackCommand command);
}