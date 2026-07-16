package com.pwb.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceTagResponse {

    private String id;
    private String textContent;
    private String languageCode;
    private String voiceName;
    private boolean isDefault;
    private String createdAt;
}
