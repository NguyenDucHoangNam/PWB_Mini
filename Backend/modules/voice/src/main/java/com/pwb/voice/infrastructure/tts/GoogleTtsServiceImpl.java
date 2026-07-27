package com.pwb.voice.infrastructure.tts;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.pwb.kernel.exception.BusinessException;
import com.pwb.voice.core.exception.VoiceErrorCode;

import com.pwb.voice.core.service.TextToSpeechService;
import com.pwb.voice.infrastructure.config.TtsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleTtsServiceImpl implements TextToSpeechService {

    private static final String CACHE_NAME = "ttsCache";

    private final TextToSpeechClient textToSpeechClient;
    private final TtsProperties ttsProperties;

    @Override
    @Cacheable(value = CACHE_NAME, key = "#text + '|' + #languageCode")
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
            throw new BusinessException(VoiceErrorCode.TTS_GENERATION_FAILED, ex);
        }
    }
}

