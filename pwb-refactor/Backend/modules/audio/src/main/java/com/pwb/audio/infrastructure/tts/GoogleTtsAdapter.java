package com.pwb.audio.infrastructure.tts;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.ListVoicesRequest;
import com.google.cloud.texttospeech.v1.ListVoicesResponse;
import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechRequest;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import com.google.cloud.texttospeech.v1.Voice;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.google.protobuf.ByteString;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.domain.model.TtsVoice;
import com.pwb.audio.domain.service.TtsRequest;
import com.pwb.audio.domain.service.TtsResult;
import com.pwb.audio.domain.service.TextToSpeechPort;
import com.pwb.audio.infrastructure.tts.properties.GoogleTtsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnClass(name = "com.google.cloud.texttospeech.v1.TextToSpeechClient")
@ConditionalOnProperty(name = "pwb.audio.tts.google.enabled", havingValue = "true", matchIfMissing = true)
public class GoogleTtsAdapter implements TextToSpeechPort {

    private final GoogleTtsProperties properties;
    private final TextToSpeechClient client;

    public GoogleTtsAdapter(GoogleTtsProperties properties) {
        this.properties = properties;
        this.client = initializeClient(properties);
    }

    @Override
    public TtsResult synthesize(TtsRequest request) {
        if (client == null) {
            throw new AudioBusinessException(AudioErrorCode.TTS_ERROR, "Google TTS client is not configured");
        }
        if (request.text() == null || request.text().isBlank()) {
            throw new AudioBusinessException(AudioErrorCode.TTS_ERROR, "Text must not be blank");
        }

        try {
            SynthesisInput input = SynthesisInput.newBuilder()
                    .setText(request.text())
                    .build();

            VoiceSelectionParams voiceParams = VoiceSelectionParams.newBuilder()
                    .setLanguageCode(Optional.ofNullable(request.languageCode())
                            .orElse(properties.getDefaultLanguageCode()))
                    .setName(Optional.ofNullable(request.voiceName())
                            .orElse(properties.getDefaultVoiceName()))
                    .build();

            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(parseEncoding(properties.getAudioEncoding()))
                    .setSpeakingRate(properties.getSpeakingRate().floatValue())
                    .setPitch(properties.getPitch().floatValue())
                    .build();

            SynthesizeSpeechRequest grpcRequest = SynthesizeSpeechRequest.newBuilder()
                    .setInput(input)
                    .setVoice(voiceParams)
                    .setAudioConfig(audioConfig)
                    .build();

            SynthesizeSpeechResponse response = client.synthesizeSpeech(grpcRequest);
            ByteString audioContent = response.getAudioContent();
            byte[] audioBytes = audioContent.toByteArray();

            log.info("TTS synthesized: bytes={}, language={}, voice={}",
                    audioBytes.length, request.languageCode(), request.voiceName());

            return new TtsResult(audioBytes, null, properties.getAudioEncoding().toLowerCase());
        } catch (AudioBusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Google TTS synthesis failed", ex);
            throw new AudioBusinessException(AudioErrorCode.TTS_ERROR, ex);
        }
    }

    @Override
    public List<TtsVoice> listVoices(String languageCode) {
        if (client == null) {
            throw new AudioBusinessException(AudioErrorCode.TTS_ERROR, "Google TTS client is not configured");
        }

        try {
            ListVoicesRequest.Builder builder = ListVoicesRequest.newBuilder();
            if (languageCode != null && !languageCode.isBlank()) {
                builder.setLanguageCode(languageCode);
            }

            ListVoicesResponse response = client.listVoices(builder.build());
            return response.getVoicesList().stream()
                    .map(this::toTtsVoice)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            log.error("Google TTS listVoices failed: languageCode={}", languageCode, ex);
            throw new AudioBusinessException(AudioErrorCode.TTS_ERROR, ex);
        }
    }

    private TtsVoice toTtsVoice(Voice voice) {
        if (voice == null) {
            return null;
        }
        SsmlVoiceGender gender = voice.getSsmlGender();
        return new TtsVoice(
                voice.getName(),
                voice.getLanguageCodesCount() > 0 ? voice.getLanguageCodes(0) : null,
                gender == null ? null : gender.name()
        );
    }

    private AudioEncoding parseEncoding(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return AudioEncoding.MP3;
        }
        try {
            return AudioEncoding.valueOf(encoding.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown audio encoding '{}', falling back to MP3", encoding);
            return AudioEncoding.MP3;
        }
    }

    private TextToSpeechClient initializeClient(GoogleTtsProperties props) {
        try {
            TextToSpeechSettings.Builder settingsBuilder = TextToSpeechSettings.newBuilder();
            if (props.getCredentialsPath() != null && !props.getCredentialsPath().isBlank()) {
                String credentials = props.getCredentialsPath();
                settingsBuilder.setCredentialsProvider(() -> {
                    try (var stream = new java.io.FileInputStream(credentials)) {
                        return com.google.auth.oauth2.GoogleCredentials.fromStream(stream)
                                .createScoped("https://www.googleapis.com/auth/cloud-platform");
                    } catch (IOException ex) {
                        AudioBusinessException ex2 = new AudioBusinessException(
                                AudioErrorCode.TTS_ERROR,
                                "Failed to load Google credentials: " + credentials
                        );
                        ex2.initCause(ex);
                        throw ex2;
                    }
                });
            }
            return TextToSpeechClient.create(settingsBuilder.build());
        } catch (AudioBusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to initialize Google TTS client", ex);
            throw new AudioBusinessException(AudioErrorCode.TTS_ERROR, ex);
        }
    }
}