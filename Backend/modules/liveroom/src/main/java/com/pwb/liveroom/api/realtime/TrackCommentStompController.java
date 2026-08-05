package com.pwb.liveroom.api.realtime;

import com.pwb.liveroom.api.dto.request.AddTrackCommentRequest;
import com.pwb.liveroom.api.support.Actors;
import com.pwb.liveroom.application.command.AddTrackCommentCommand;
import com.pwb.liveroom.application.usecase.AddTrackCommentUseCase;
import com.pwb.liveroom.application.usecase.GetTrackCommentsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;


@Controller
@RequiredArgsConstructor
public class TrackCommentStompController {

    private final AddTrackCommentUseCase addTrackComment;
    private final GetTrackCommentsUseCase getTrackComments;

    @MessageMapping("/liveroom/{roomId}/comments/add")
    public void add(
            @DestinationVariable UUID roomId,
            @Payload AddTrackCommentRequest request,
            Principal principal
    ) {
        addTrackComment.execute(new AddTrackCommentCommand(
                Actors.userIdOf(principal),
                roomId,
                request == null ? null : request.songId(),
                request == null ? null : request.positionSeconds(),
                request == null ? null : request.content()));
    }

    @MessageMapping("/liveroom/{roomId}/comments/get")
    public void get(@DestinationVariable UUID roomId, Principal principal) {
        getTrackComments.execute(Actors.userIdOf(principal), roomId);
    }
}