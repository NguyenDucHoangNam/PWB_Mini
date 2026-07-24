package com.pwb.liveroom.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlaybackSnapshotResponse {

    private String roomCode;
    private String status;
    private Long positionSeconds;
    private Instant effectiveAt;
    private Long version;
    private UUID changedByUserId;
    private Instant changedAt;
    private boolean empty;
    private SongPlaybackSummaryResponse song;
    private BigDecimal playbackRate;
    private String loopMode;
    private boolean shuffleEnabled;
    private String audioSource;
}