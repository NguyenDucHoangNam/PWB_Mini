package com.pwb.backend.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
public enum VoiceLanguage {

    VI_VN("vi-VN", Map.of(
            VoiceGender.MALE, List.of(
                    "vi-VN-Wavenet-A",
                    "vi-VN-Wavenet-C",
                    "vi-VN-Standard-A",
                    "vi-VN-Standard-C"),
            VoiceGender.FEMALE, List.of(
                    "vi-VN-Wavenet-B",
                    "vi-VN-Wavenet-D",
                    "vi-VN-Standard-B",
                    "vi-VN-Standard-D"))),

    EN_US("en-US", Map.of(
            VoiceGender.MALE, List.of(
                    "en-US-Neural2-D",
                    "en-US-Neural2-J",
                    "en-US-Wavenet-A",
                    "en-US-Wavenet-C",
                    "en-US-Standard-A",
                    "en-US-Standard-C"),
            VoiceGender.FEMALE, List.of(
                    "en-US-Neural2-A",
                    "en-US-Neural2-C",
                    "en-US-Neural2-F",
                    "en-US-Wavenet-B",
                    "en-US-Wavenet-F",
                    "en-US-Wavenet-G",
                    "en-US-Standard-B")));

    private final String code;
    private final Map<VoiceGender, List<String>> voicesByGender;

    VoiceLanguage(String code, Map<VoiceGender, List<String>> voicesByGender) {
        this.code = code;
        this.voicesByGender = voicesByGender;
    }

    public static Optional<VoiceLanguage> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(lang -> lang.code.equalsIgnoreCase(code))
                .findFirst();
    }

    public boolean supportsVoice(String voiceName) {
        if (voiceName == null) {
            return false;
        }
        return voicesByGender.values().stream()
                .flatMap(List::stream)
                .anyMatch(v -> v.equalsIgnoreCase(voiceName));
    }

    public List<VoiceOption> listVoices() {
        return voicesByGender.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(name -> new VoiceOption(name, entry.getKey().name())))
                .collect(Collectors.toList());
    }

    public enum VoiceGender {
        MALE,
        FEMALE
    }

    public record VoiceOption(String voiceName, String gender) {}
}
