package com.pwb.voice.api.dto.response;

import com.pwb.voice.api.enums.SongStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingStatusResponse {

    private UUID songId;
    private SongStatus status;
    private boolean processedS3KeyExists;
    private String lastError;
    private String message;
}
