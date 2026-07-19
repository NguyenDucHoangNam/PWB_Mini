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
public class ProcessingStatusResponse {

    private UUID songId;
    private SongStatus status;
    private String processedS3Key;
    private Integer durationSeconds;
    private String lastError;
    private String message;
    private Instant updatedAt;
}
