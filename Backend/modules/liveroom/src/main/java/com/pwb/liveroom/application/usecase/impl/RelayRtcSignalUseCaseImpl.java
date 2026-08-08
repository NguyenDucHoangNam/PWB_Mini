package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.command.RelayRtcSignalCommand;
import com.pwb.liveroom.application.command.RtcIceCandidate;
import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvent;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.RtcRelayGuard;
import com.pwb.liveroom.application.usecase.RelayRtcSignalUseCase;
import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelayRtcSignalUseCaseImpl implements RelayRtcSignalUseCase {

    private final RtcRelayGuard relayGuard;
    private final LiveroomEventPublisher eventPublisher;
    private final LiveroomConfig config;

    @Override
    public void execute(RelayRtcSignalCommand command) {
        if (command.targetUserId() == null) {
            throw new LiveroomBusinessException(LiveroomErrorCode.RTC_PAYLOAD_INVALID);
        }
        if (command.targetUserId().equals(command.actorId())) {
            throw new LiveroomBusinessException(LiveroomErrorCode.RTC_SELF_SIGNALING);
        }

        relayGuard.verify(command.roomId(), command.actorId(), command.targetUserId());

        RoomEvent event = command.type().isDescription()
                ? describe(command)
                : iceCandidate(command);

        eventPublisher.sendToUserChannel(
                command.targetUserId(), event, LiveroomEventPublisher.RTC_CHANNEL);

        log.debug("Relayed RTC signal: roomId={} from={} to={} type={}",
                command.roomId(), command.actorId(), command.targetUserId(), command.type());
    }

    private RoomEvent describe(RelayRtcSignalCommand command) {
        String sdp = command.sdp();
        if (sdp == null || sdp.isBlank()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.RTC_PAYLOAD_INVALID);
        }
        if (sdp.length() > config.getRtc().getMaxSdpLength()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.RTC_PAYLOAD_TOO_LARGE);
        }
        return RoomEvents.rtcDescription(command.roomId(), command.actorId(), command.type(), sdp);
    }

    private RoomEvent iceCandidate(RelayRtcSignalCommand command) {
        RtcIceCandidate candidate = command.candidate();
        if (candidate == null || candidate.candidate() == null || candidate.candidate().isBlank()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.RTC_PAYLOAD_INVALID);
        }
        if (candidate.candidate().length() > config.getRtc().getMaxCandidateLength()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.RTC_PAYLOAD_TOO_LARGE);
        }
        return RoomEvents.rtcIceCandidate(command.roomId(), command.actorId(), candidate);
    }
}