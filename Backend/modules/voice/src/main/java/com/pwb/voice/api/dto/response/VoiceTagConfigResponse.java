package com.pwb.voice.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceTagConfigResponse {

    private UUID id;
    private UUID songId;
    private UUID voiceTagId;
    private String voiceTagName;
    private Integer intervalSeconds;
    private Integer volumePercentage;
    private Integer fadeInDurationMs;
    private Integer fadeOutDurationMs;
    private Integer startOffsetSeconds;
    private boolean enabled;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
