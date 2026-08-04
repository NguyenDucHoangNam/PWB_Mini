package com.pwb.liveroom.api.realtime;

import com.pwb.liveroom.api.dto.request.SendChatMessageRequest;
import com.pwb.liveroom.api.support.Actors;
import com.pwb.liveroom.application.command.SendChatMessageCommand;
import com.pwb.liveroom.application.usecase.SendChatMessageUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;


@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final SendChatMessageUseCase sendChatMessage;

    @MessageMapping("/liveroom/{roomId}/chat/send")
    public void send(
            @DestinationVariable UUID roomId,
            @Payload SendChatMessageRequest request,
            Principal principal
    ) {
        sendChatMessage.execute(new SendChatMessageCommand(
                Actors.userIdOf(principal), roomId, request == null ? null : request.content()));
    }
}