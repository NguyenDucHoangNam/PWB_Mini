package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.command.AddTrackCommentCommand;
import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.exception.LiveroomBusinessException;
import com.pwb.liveroom.application.exception.LiveroomErrorCode;
import com.pwb.liveroom.application.support.Playbacks;
import com.pwb.liveroom.application.support.TrackCommentViewFactory;
import com.pwb.liveroom.application.usecase.AddTrackCommentUseCase;
import com.pwb.liveroom.application.view.TrackCommentView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.model.PlaybackState;
import com.pwb.liveroom.domain.model.TrackComment;
import com.pwb.liveroom.domain.service.TrackCommentStore;
import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;


@Slf4j
@Service
@RequiredArgsConstructor
public class AddTrackCommentUseCaseImpl implements AddTrackCommentUseCase {

    private final Playbacks playbacks;
    private final TrackCommentStore commentStore;
    private final TrackCommentViewFactory viewFactory;
    private final LiveroomEventPublisher eventPublisher;
    private final LiveroomConfig config;

    @Override
    @Transactional(readOnly = true)
    public TrackCommentView execute(AddTrackCommentCommand command) {
        LiveRoom room = playbacks.requireActiveRoom(command.roomId());
        Participant participant = playbacks.requireInRoom(room, command.actorId());

        String content = TrackComment.normalizeContent(command.content());
        if (content.isEmpty()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.TRACK_COMMENT_EMPTY);
        }
        if (TrackComment.lengthOf(content) > config.getComments().getMaxLength()) {
            throw new LiveroomBusinessException(LiveroomErrorCode.TRACK_COMMENT_TOO_LONG);
        }

        PlaybackState state = playbacks.requireWithSong(room.getId());
        if (command.songId() == null || !command.songId().equals(state.getSongId())) {
            throw new LiveroomBusinessException(LiveroomErrorCode.TRACK_COMMENT_SONG_MISMATCH);
        }

        Instant now = Instant.now();
        double position = clamp(command.positionSeconds(), state.getSongDurationSeconds());

        TrackComment saved = commentStore.add(TrackComment.post(
                room.getCurrentCycleId(),
                state.getSongId(),
                participant.getUserId(),
                participant.getUserEmail(),
                content,
                position,
                now
        ));

        TrackCommentView view = viewFactory.toView(saved);
        eventPublisher.broadcastToRoomChannel(
                RoomEvents.trackCommentAdded(room, view), LiveroomEventPublisher.MUSIC_CHANNEL);

        log.debug("Track comment added: roomId={} songId={} userId={} position={}",
                room.getId(), state.getSongId(), command.actorId(), position);
        return view;
    }

    private double clamp(Double requested, Integer durationSeconds) {
        double position = requested == null ? 0d : Math.max(0d, requested);
        return durationSeconds == null ? position : Math.min(position, durationSeconds);
    }
}