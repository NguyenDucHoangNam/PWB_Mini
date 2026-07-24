package com.pwb.liveroom.core.service;

import com.pwb.liveroom.api.dto.response.PlaybackSnapshotResponse;
import com.pwb.liveroom.api.dto.response.SongPlaybackSummaryResponse;

import java.math.BigDecimal;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;

public interface LiveRoomPlaybackService {

    PlaybackSnapshotResponse getSnapshot(UUID userId, String roomCode);

    PlaybackSnapshotResponse selectSong(UUID userId, String roomCode, UUID songId);

    PlaybackSnapshotResponse play(UUID userId, String roomCode);

    PlaybackSnapshotResponse pause(UUID userId, String roomCode);

    PlaybackSnapshotResponse seek(UUID userId, String roomCode, long positionSeconds);

    PlaybackSnapshotResponse setRate(UUID userId, String roomCode, BigDecimal rate);

    PlaybackSnapshotResponse setLoop(UUID userId, String roomCode, String loopMode);

    PlaybackSnapshotResponse setShuffle(UUID userId, String roomCode, boolean enabled);

    PlaybackSnapshotResponse requestPlaybackState(UUID userId, String roomCode);

    SongPlaybackSummaryResponse ownerSummary(UUID userId, UUID songId, UUID expectedOwnerUserId);

    URL getSharedSongStreamUrl(UUID userId, String roomCode, UUID songId, Duration expiration);
}
