package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.command.SendChatMessageCommand;
import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.ChatViewFactory;
import com.pwb.liveroom.application.support.RoomLoader;
import com.pwb.liveroom.application.usecase.SendChatMessageUseCase;
import com.pwb.liveroom.application.view.ChatMessageView;
import com.pwb.liveroom.domain.model.ChatMessage;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.repository.ChatMessageRepository;
import com.pwb.liveroom.domain.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;


@Slf4j
@Service
@RequiredArgsConstructor
public class SendChatMessageUseCaseImpl implements SendChatMessageUseCase {

    private final RoomLoader roomLoader;
    private final ParticipantRepository participantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final LiveroomEventPublisher eventPublisher;
    private final ChatViewFactory viewFactory;

    @Override
    @Transactional
    public ChatMessageView execute(SendChatMessageCommand command) {
        LiveRoom room = roomLoader.require(command.roomId());
        if (!room.isActive() || room.getCurrentCycleId() == null) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_ENDED);
        }

        Participant participant = participantRepository
                .findByCycleIdAndUserId(room.getCurrentCycleId(), command.actorId())
                .filter(Participant::isInRoom)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.NOT_IN_SESSION));

        String content = ChatMessage.normalizeContent(command.content());
        if (content.isEmpty()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.CHAT_EMPTY);
        }
        if (ChatMessage.lengthOf(content) > ChatMessage.MAX_CONTENT_LENGTH) {
            throw new LiveroomBusinessException(LiveroomErrorCode.CHAT_TOO_LONG);
        }

        Instant now = Instant.now();


        ChatMessage saved = chatMessageRepository.save(ChatMessage.send(
                room.getId(),
                room.getCurrentCycleId(),
                participant.getUserId(),
                participant.getUserEmail(),
                content,
                now
        ));


        participant.markInteraction(now);
        participantRepository.save(participant);

        eventPublisher.broadcastToRoomChannel(
                RoomEvents.chatMessageReceived(room, saved), LiveroomEventPublisher.CHAT_CHANNEL);

        log.debug("Chat message sent: roomId={} cycleId={} userId={} length={}",
                room.getId(), room.getCurrentCycleId(), command.actorId(), ChatMessage.lengthOf(content));
        return viewFactory.toView(saved);
    }
}