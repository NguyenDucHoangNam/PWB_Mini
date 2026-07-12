package com.pwb.backend.modules.voice_tag.tts;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.voice_tag.config.VoiceTagProperties;
import com.pwb.backend.modules.voice_tag.exception.VoiceTagErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HexFormat;

@Slf4j
@Component
public class TtsResponseValidator {

    private static final int ID3_HEADER_LENGTH = 3;
    private static final int FRAME_SYNC_MASK = 0xE0;

    private final VoiceTagProperties properties;

    public TtsResponseValidator(VoiceTagProperties properties) {
        this.properties = properties;
    }

    public void validate(byte[] payload) {
        if (payload == null || payload.length < properties.getMinMp3Bytes()
                || payload.length > properties.getMaxMp3Bytes()) {
            logRejectReason("size", payload);
            throw new BusinessException(VoiceTagErrorCode.TTS_SERVICE_FAILED_INVALID);
        }
        if (!looksLikeMp3(payload)) {
            logRejectReason("magic", payload);
            throw new BusinessException(VoiceTagErrorCode.TTS_SERVICE_FAILED_INVALID);
        }
    }

    private boolean looksLikeMp3(byte[] payload) {
        if (payload.length >= ID3_HEADER_LENGTH
                && (payload[0] & 0xFF) == 0x49
                && (payload[1] & 0xFF) == 0x44
                && (payload[2] & 0xFF) == 0x33) {
            return true;
        }
        if (payload.length >= 2
                && (payload[0] & 0xFF) == 0xFF
                && ((payload[1] & 0xFF) & FRAME_SYNC_MASK) == 0xE0) {
            return true;
        }
        return false;
    }

    private void logRejectReason(String reason, byte[] payload) {
        int length = payload == null ? 0 : payload.length;
        String hex = payload == null
                ? "<null>"
                : HexFormat.of().formatHex(payload, 0, Math.min(payload.length, 16));
        log.error("GCP_TTS_INVALID_BYTES reason={} bytesReceived={} headerHex={}", reason, length, hex);
    }
}
