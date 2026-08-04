package com.pwb.liveroom.api.realtime;

import com.pwb.liveroom.api.dto.request.RtcDescriptionRequest;
import com.pwb.liveroom.api.dto.request.RtcIceCandidateRequest;
import com.pwb.liveroom.api.support.Actors;
import com.pwb.liveroom.application.command.RelayRtcSignalCommand;
import com.pwb.liveroom.application.command.RtcSignalType;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.usecase.RelayRtcSignalUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class RtcStompController {

    private final RelayRtcSignalUseCase relayRtcSignal;

    @MessageMapping("/liveroom/{roomId}/rtc/offer")
    public void offer(
            @DestinationVariable UUID roomId,
            @Payload RtcDescriptionRequest request,
            Principal principal
    ) {
        relayDescription(roomId, request, principal, RtcSignalType.OFFER);
    }

    @MessageMapping("/liveroom/{roomId}/rtc/answer")
    public void answer(
            @DestinationVariable UUID roomId,
            @Payload RtcDescriptionRequest request,
            Principal principal
    ) {
        relayDescription(roomId, request, principal, RtcSignalType.ANSWER);
    }

    @MessageMapping("/liveroom/{roomId}/rtc/ice")
    public void iceCandidate(
            @DestinationVariable UUID roomId,
            @Payload RtcIceCandidateRequest request,
            Principal principal
    ) {
        requirePayload(request);
        relayRtcSignal.execute(RelayRtcSignalCommand.iceCandidate(
                Actors.userIdOf(principal), roomId, request.targetUserId(), request.toCandidate()));
    }

    private void relayDescription(
            UUID roomId, RtcDescriptionRequest request, Principal principal, RtcSignalType type) {
        requirePayload(request);
        relayRtcSignal.execute(RelayRtcSignalCommand.description(
                Actors.userIdOf(principal), roomId, request.targetUserId(), type, request.sdp()));
    }

    private void requirePayload(Object request) {
        if (request == null) {
            throw new LiveroomBusinessException(LiveroomErrorCode.RTC_PAYLOAD_INVALID);
        }
    }
}