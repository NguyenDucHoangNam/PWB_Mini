package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.command.LoadChatHistoryCommand;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.ChatViewFactory;
import com.pwb.liveroom.application.support.RoomLoader;
import com.pwb.liveroom.application.usecase.LoadChatHistoryUseCase;
import com.pwb.liveroom.application.view.ChatHistoryView;
import com.pwb.liveroom.domain.model.ChatMessage;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.repository.ChatMessageRepository;
import com.pwb.liveroom.domain.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class LoadChatHistoryUseCaseImpl implements LoadChatHistoryUseCase {

    private final RoomLoader roomLoader;
    private final ParticipantRepository participantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatViewFactory viewFactory;

    @Override
    @Transactional(readOnly = true)
    public ChatHistoryView execute(LoadChatHistoryCommand command) {
        LiveRoom room = roomLoader.require(command.roomId());
        if (!room.isActive() || room.getCurrentCycleId() == null) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_ENDED);
        }

        UUID cycleId = room.getCurrentCycleId();
        participantRepository.findByCycleIdAndUserId(cycleId, command.actorId())
                .filter(Participant::isInRoom)
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.NOT_IN_SESSION));

        int size = clampSize(command.size());


        List<ChatMessage> fetched = loadPage(cycleId, command.cursor(), size + 1);

        boolean hasMore = fetched.size() > size;
        List<ChatMessage> page = hasMore ? fetched.subList(0, size) : fetched;
        UUID nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;

        return new ChatHistoryView(page.stream().map(viewFactory::toView).toList(), hasMore, nextCursor);
    }


    private List<ChatMessage> loadPage(UUID cycleId, UUID cursor, int limit) {
        if (cursor == null) {
            return chatMessageRepository.findLatestByCycleId(cycleId, limit);
        }
        return chatMessageRepository.findByIdAndCycleId(cursor, cycleId)
                .map(anchor -> chatMessageRepository
                        .findOlderByCycleId(cycleId, anchor.getSentAt(), anchor.getId(), limit))
                .orElseGet(() -> {
                    log.debug("Chat history cursor not in cycle {}: {}", cycleId, cursor);
                    return chatMessageRepository.findLatestByCycleId(cycleId, limit);
                });
    }

    private int clampSize(int requested) {
        if (requested <= 0) {
            return ChatMessage.DEFAULT_HISTORY_SIZE;
        }
        return Math.min(requested, ChatMessage.DEFAULT_HISTORY_SIZE);
    }
}