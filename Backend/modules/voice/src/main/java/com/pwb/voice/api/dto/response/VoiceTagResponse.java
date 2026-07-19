package com.pwb.voice.api.dto.response;

import com.pwb.voice.api.enums.VoiceTagType;
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
public class VoiceTagResponse {

    private UUID id;
    private UUID userId;
    private String name;
    private String description;
    private VoiceTagType tagType;
    private String sourceText;
    private String languageCode;
    private Integer durationSeconds;
    private Long fileSizeBytes;
    private boolean isDefault;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
