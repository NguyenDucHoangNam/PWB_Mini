package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.command.SelectSongCommand;
import com.pwb.liveroom.application.view.PlaybackStateView;

public interface SelectSongUseCase {

    PlaybackStateView execute(SelectSongCommand command);
}