package com.pwb.backend.service;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.google.protobuf.ByteString;
import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GcpTtsClient {

    private static final int MIN_BYTES = 1024;
    private static final int MAX_BYTES = 5 * 1024 * 1024;

    private static final byte[] ID3_MAGIC = {0x49, 0x44, 0x33};
    private static final byte[][] MPEG_MAGIC = {
            {(byte) 0xFF, (byte) 0xFB},
            {(byte) 0xFF, (byte) 0xE3},
            {(byte) 0xFF, (byte) 0xF3}
    };

    private final TextToSpeechClient textToSpeechClient;

    public byte[] synthesizeSsml(String ssml, String languageCode, String voiceName) {
        try {
            SynthesisInput input = SynthesisInput.newBuilder()
                    .setSsml(ssml)
                    .build();

            VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode(languageCode)
                    .setName(voiceName)
                    .build();

            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.MP3)
                    .setSampleRateHertz(24000)
                    .build();

            SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);
            ByteString audioContent = response.getAudioContent();
            byte[] bytes = audioContent.toByteArray();

            validateAudioBytes(bytes);
            return bytes;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("GCP TTS synthesis failed for ssml length={} voice={}", ssml.length(), voiceName, ex);
            throw new BusinessException(
                    ErrorCode.TTS_SERVICE_FAILED,
                    "TTS synthesis failed: " + ex.getMessage(),
                    ex);
        }
    }

    private void validateAudioBytes(byte[] bytes) {
        if (bytes == null || bytes.length < MIN_BYTES || bytes.length > MAX_BYTES) {
            throw new BusinessException(
                    ErrorCode.TTS_SERVICE_FAILED_INVALID,
                    "Invalid audio size: " + (bytes == null ? 0 : bytes.length));
        }

        if (!isValidMp3Magic(bytes)) {
            throw new BusinessException(
                    ErrorCode.TTS_SERVICE_FAILED_INVALID,
                    "Audio content is not a valid MP3 stream");
        }
    }

    private boolean isValidMp3Magic(byte[] bytes) {
        if (startsWith(bytes, ID3_MAGIC)) {
            return true;
        }
        for (byte[] mpegMagic : MPEG_MAGIC) {
            if (startsWith(bytes, mpegMagic)) {
                return true;
            }
        }
        return false;
    }

    private boolean startsWith(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (source[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
