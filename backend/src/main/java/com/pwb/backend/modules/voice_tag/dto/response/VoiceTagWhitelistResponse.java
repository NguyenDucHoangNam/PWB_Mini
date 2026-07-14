package com.pwb.backend.modules.voice_tag.dto.response;

import java.util.List;

public record VoiceTagWhitelistResponse(String languageCode, List<VoiceOption> voices) {

    public record VoiceOption(String voiceName, String gender) {
    }
}