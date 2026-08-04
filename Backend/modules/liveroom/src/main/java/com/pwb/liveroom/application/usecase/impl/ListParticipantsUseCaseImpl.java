package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.ParticipationViewFactory;
import com.pwb.liveroom.application.support.RoomLoader;
import com.pwb.liveroom.application.usecase.ListParticipantsUseCase;
import com.pwb.liveroom.application.view.ParticipantView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListParticipantsUseCaseImpl implements ListParticipantsUseCase {

    private final RoomLoader roomLoader;
    private final ParticipantRepository participantRepository;
    private final ParticipationViewFactory viewFactory;

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantView> execute(UUID actorId, UUID roomId) {
        LiveRoom room = roomLoader.require(roomId);
        if (room.getCurrentCycleId() == null) {
            return List.of();
        }

        List<Participant> roster = participantRepository.findInRoomByCycleId(room.getCurrentCycleId());
        boolean allowed = room.isOwnedBy(actorId)
                || roster.stream().anyMatch(p -> p.getUserId().equals(actorId));
        if (!allowed) {
            throw new LiveroomBusinessException(LiveroomErrorCode.NOT_IN_SESSION);
        }
        return viewFactory.toRankedViews(roster);
    }
}