package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.command.FindRoomByCodeCommand;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.RoomViewFactory;
import com.pwb.liveroom.application.usecase.FindRoomByCodeUseCase;
import com.pwb.liveroom.application.view.RoomLookupView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.vo.RoomCode;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import com.pwb.liveroom.domain.service.RoomCodeLookupThrottle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FindRoomByCodeUseCaseImpl implements FindRoomByCodeUseCase {

    private final LiveRoomRepository liveRoomRepository;
    private final RoomCodeLookupThrottle lookupThrottle;
    private final RoomViewFactory roomViewFactory;

    @Override
    @Transactional(readOnly = true)
    public RoomLookupView execute(FindRoomByCodeCommand command) {
        if (!lookupThrottle.tryConsume(command.clientIp())) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_CODE_LOOKUP_THROTTLED);
        }

        RoomCode code = parseCode(command.roomCode());
        LiveRoom room = liveRoomRepository.findByRoomCode(code.value())
                .orElseThrow(() -> new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND));



        log.debug("Room code resolved: clientIp={} codeHash={} roomId={}",
                command.clientIp(), Integer.toHexString(code.value().hashCode()), room.getId());
        return roomViewFactory.toLookupView(room);
    }


    private RoomCode parseCode(String raw) {
        try {
            return RoomCode.of(raw);
        } catch (IllegalArgumentException ex) {
            throw new LiveroomBusinessException(LiveroomErrorCode.ROOM_NOT_FOUND);
        }
    }
}