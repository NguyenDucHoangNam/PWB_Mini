package com.pwb.backend.modules.voice_tag.service;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.voice_tag.exception.VoiceTagErrorCode;
import com.pwb.backend.modules.voice_tag.tts.TtsResponseValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VoiceTagGcpTtsClient {

    private final ObjectProvider<TextToSpeechClient> textToSpeechClientProvider;
    private final TtsResponseValidator ttsResponseValidator;

    public VoiceTagGcpTtsClient(
            ObjectProvider<TextToSpeechClient> textToSpeechClientProvider,
            TtsResponseValidator ttsResponseValidator) {
        this.textToSpeechClientProvider = textToSpeechClientProvider;
        this.ttsResponseValidator = ttsResponseValidator;
    }

    public byte[] synthesize(String ssml, String languageCode, String voiceName) {
        TextToSpeechClient client = textToSpeechClientProvider.getIfAvailable();
        if (client == null) {
            log.warn("GCP_TTS_CLIENT_UNAVAILABLE languageCode={} voice={}", languageCode, voiceName);
            throw new BusinessException(VoiceTagErrorCode.TTS_SERVICE_FAILED,
                    "Google Cloud Text-to-Speech client is not available");
        }

        byte[] payload;
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
                    .build();
            SynthesizeSpeechResponse response = client.synthesizeSpeech(input, voice, audioConfig);
            payload = response.getAudioContent().toByteArray();
        } catch (Exception ex) {
            log.warn("GCP_TTS_CONNECTION_FAILED languageCode={} voice={} reason={}",
                    languageCode, voiceName, ex.getMessage());
            throw new BusinessException(VoiceTagErrorCode.TTS_SERVICE_FAILED, ex);
        }

        ttsResponseValidator.validate(payload);
        log.info("GCP_TTS_CALL_SUCCESS bytesReceived={}", payload.length);
        return payload;
    }
}
