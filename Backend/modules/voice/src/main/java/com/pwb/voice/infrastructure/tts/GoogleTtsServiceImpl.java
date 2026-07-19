package com.pwb.voice.infrastructure.tts;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.voice.core.service.TextToSpeechService;
import com.pwb.voice.infrastructure.config.TtsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleTtsServiceImpl implements TextToSpeechService {

    private static final String CACHE_NAME = "ttsCache";
    private static final String SHA_256 = "SHA-256";
    private static final String KEY_DELIMITER = "|";

    private final TextToSpeechClient textToSpeechClient;
    private final TtsProperties ttsProperties;

    @Override
    @Cacheable(value = CACHE_NAME, key = "#root.target.cacheKey(#text, #languageCode)")
    public byte[] synthesize(String text, String languageCode) {
        long startMs = System.currentTimeMillis();
        int textLength = text == null ? 0 : text.length();

        log.info("TTS synthesize started: textLength={}, languageCode={}", textLength, languageCode);

        try {
            SynthesisInput input = SynthesisInput.newBuilder()
                    .setText(text)
                    .build();

            VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode(languageCode)
                    .setName(ttsProperties.getDefaultVoiceName())
                    .setSsmlGender(SsmlVoiceGender.NEUTRAL)
                    .build();

            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.MP3)
                    .setSpeakingRate((float) ttsProperties.getSpeakingRate())
                    .setPitch((float) ttsProperties.getPitch())
                    .build();

            SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);
            byte[] audioContent = response.getAudioContent().toByteArray();

            long durationMs = System.currentTimeMillis() - startMs;
            log.info("TTS synthesize succeeded: textLength={}, languageCode={}, audioBytes={}, durationMs={}",
                    textLength, languageCode, audioContent.length, durationMs);

            return audioContent;
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startMs;
            log.error("TTS synthesize failed: textLength={}, languageCode={}, durationMs={}",
                    textLength, languageCode, durationMs, ex);
            throw new BusinessException(ErrorCode.TTS_GENERATION_FAILED, ex);
        }
    }

    public String cacheKey(String text, String languageCode) {
        String raw = String.join(KEY_DELIMITER,
                text == null ? "" : text,
                languageCode == null ? "" : languageCode,
                ttsProperties.getDefaultVoiceName(),
                Double.toString(ttsProperties.getSpeakingRate()),
                Double.toString(ttsProperties.getPitch()));

        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
