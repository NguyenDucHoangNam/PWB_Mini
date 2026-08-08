package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.event.LiveroomEventPublisher;
import com.pwb.liveroom.application.event.RoomEvents;
import com.pwb.liveroom.application.support.Playbacks;
import com.pwb.liveroom.application.support.TrackCommentViewFactory;
import com.pwb.liveroom.application.usecase.GetTrackCommentsUseCase;
import com.pwb.liveroom.application.view.TrackCommentView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.PlaybackState;
import com.pwb.liveroom.domain.service.TrackCommentStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class GetTrackCommentsUseCaseImpl implements GetTrackCommentsUseCase {

    private final Playbacks playbacks;
    private final TrackCommentStore commentStore;
    private final TrackCommentViewFactory viewFactory;
    private final LiveroomEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public void execute(UUID actorId, UUID roomId) {
        LiveRoom room = playbacks.requireActiveRoom(roomId);
        playbacks.requireInRoom(room, actorId);

        PlaybackState state = playbacks.loadOrSilent(roomId, Instant.now());
        UUID songId = state.getSongId();
        List<TrackCommentView> comments = songId == null
                ? List.of()
                : viewFactory.toViews(commentStore.findBySong(room.getCurrentCycleId(), songId));

        eventPublisher.sendToUser(actorId, RoomEvents.trackCommentSnapshot(room, songId, comments));
    }
}