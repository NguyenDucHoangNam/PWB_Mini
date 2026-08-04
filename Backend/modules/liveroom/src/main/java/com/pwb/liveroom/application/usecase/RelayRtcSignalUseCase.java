package com.pwb.liveroom.application.usecase;

import com.pwb.liveroom.application.command.RelayRtcSignalCommand;

public interface RelayRtcSignalUseCase {

    void execute(RelayRtcSignalCommand command);
}
