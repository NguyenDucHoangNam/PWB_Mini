package com.pwb.liveroom.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SongPlaybackSummaryResponse {

    private UUID songId;
    private UUID ownerUserId;
    private String title;
    private String artist;
    private String album;
    private Integer durationSeconds;
    private String format;
    private boolean processable;
    private String audioSource;
}
