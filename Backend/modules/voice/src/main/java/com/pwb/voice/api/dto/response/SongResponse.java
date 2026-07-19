package com.pwb.voice.api.dto.response;

import com.pwb.voice.api.enums.SongStatus;
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
public class SongResponse {

    private UUID id;
    private UUID userId;
    private String title;
    private String artist;
    private String album;
    private String format;
    private SongStatus status;
    private Long fileSizeBytes;
    private Integer durationSeconds;
    private boolean processed;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
