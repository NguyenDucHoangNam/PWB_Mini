package com.pwb.liveroom.application.support;

import com.pwb.liveroom.application.view.PlaybackStateView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.model.PlaybackState;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PlaybackViewFactory {

    public PlaybackStateView toView(LiveRoom room, PlaybackState state, Instant now) {
        return new PlaybackStateView(
                state.getRoomId(),
                state.getSongId(),
                state.getSongOwnerId(),
                state.getSongTitle(),
                state.getSongArtist(),
                state.getSongDurationSeconds(),
                state.getStatus(),
                state.positionAt(now),
                state.getVolumePercent(),
                state.getStartedAt(),
                state.getLastUpdatedAt(),
                state.getLastUpdatedBy(),
                state.getSequenceNumber(),
                room.isOwnerAbsent()
        );
    }
}