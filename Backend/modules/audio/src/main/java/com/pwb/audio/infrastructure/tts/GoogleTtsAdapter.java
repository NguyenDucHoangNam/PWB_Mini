package com.pwb.audio.infrastructure.tts;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechRequest;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.domain.service.TextToSpeechPort;
import com.pwb.audio.domain.service.TtsRequest;
import com.pwb.audio.domain.service.TtsResult;
import com.pwb.audio.infrastructure.audio.AudioProbeService;
import com.pwb.audio.infrastructure.tts.properties.GoogleTtsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Slf4j
@Service
@ConditionalOnClass(name = "com.google.cloud.texttospeech.v1.TextToSpeechClient")
@ConditionalOnProperty(name = "pwb.audio.tts.google.enabled", havingValue = "true", matchIfMissing = true)
public class GoogleTtsAdapter implements TextToSpeechPort {

    private static final String CLASSPATH_PREFIX = "classpath:";

    private final GoogleTtsProperties properties;
    private final ObjectProvider<AudioProbeService> audioProbe;
    private final TextToSpeechClient client;

    public GoogleTtsAdapter(GoogleTtsProperties properties, ObjectProvider<AudioProbeService> audioProbe) {
        this.properties = properties;
        this.audioProbe = audioProbe;
        this.client = initializeClient(properties);
    }

    @Override
    public TtsResult synthesize(TtsRequest request) {
        if (client == null) {
            throw new AudioBusinessException(AudioErrorCode.TTS_CLIENT_NOT_CONFIGURED);
        }
        if (request.text() == null || request.text().isBlank()) {
            throw new AudioBusinessException(AudioErrorCode.TTS_TEXT_BLANK);
        }

        String encoding = properties.getAudioEncoding().toLowerCase();
        try {
            SynthesizeSpeechResponse response = client.synthesizeSpeech(SynthesizeSpeechRequest.newBuilder()
                    .setInput(SynthesisInput.newBuilder().setText(request.text()).build())
                    .setVoice(resolveVoice(request))
                    .setAudioConfig(resolveAudioConfig())
                    .build());

            byte[] audioBytes = response.getAudioContent().toByteArray();
            log.info("TTS synthesized: bytes={}, language={}, voice={}",
                    audioBytes.length, request.languageCode(), request.voiceName());

            return new TtsResult(audioBytes, probeDuration(audioBytes, encoding), encoding);
        } catch (AudioBusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Google TTS synthesis failed", ex);
            throw new AudioBusinessException(AudioErrorCode.TTS_ERROR, ex);
        }
    }

    private VoiceSelectionParams resolveVoice(TtsRequest request) {
        String targetLang = Optional.ofNullable(request.languageCode())
                .filter(lang -> !lang.isBlank())
                .orElse(properties.getDefaultLanguageCode());

        VoiceSelectionParams.Builder builder = VoiceSelectionParams.newBuilder()
                .setLanguageCode(targetLang);

        if (request.voiceName() != null && !request.voiceName().isBlank()) {
            builder.setName(request.voiceName());
        } else if (targetLang.equalsIgnoreCase(properties.getDefaultLanguageCode())
                && properties.getDefaultVoiceName() != null
                && !properties.getDefaultVoiceName().isBlank()) {
            builder.setName(properties.getDefaultVoiceName());
        }

        return builder.build();
    }

    private AudioConfig resolveAudioConfig() {
        return AudioConfig.newBuilder()
                .setAudioEncoding(parseEncoding(properties.getAudioEncoding()))
                .setSpeakingRate(properties.getSpeakingRate())
                .setPitch(properties.getPitch())
                .build();
    }

    private Integer probeDuration(byte[] audioBytes, String encoding) {
        AudioProbeService probe = audioProbe.getIfAvailable();
        if (probe == null) {
            return null;
        }
        try {
            return probe.probeDurationFromBytes(audioBytes, "." + encoding);
        } catch (Exception ex) {
            log.warn("Could not determine TTS audio duration: {}", ex.getMessage());
            return null;
        }
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
        if (props.getCredentialsPath() == null || props.getCredentialsPath().isBlank()) {
            log.warn("Google TTS credentials-path is not configured; TTS features are disabled.");
            return null;
        }

        try {
            String credentialsPath = props.getCredentialsPath();
            TextToSpeechSettings settings = TextToSpeechSettings.newBuilder()
                    .setCredentialsProvider(() -> loadCredentials(credentialsPath))
                    .build();
            return TextToSpeechClient.create(settings);
        } catch (Exception ex) {
            log.error("Failed to initialize Google TTS client; TTS features are disabled.", ex);
            return null;
        }
    }

    private GoogleCredentials loadCredentials(String path) {
        try (InputStream stream = openCredentials(path)) {
            return GoogleCredentials.fromStream(stream)
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
        } catch (IOException ex) {
            throw new AudioBusinessException(AudioErrorCode.TTS_ERROR, ex);
        }
    }

    private InputStream openCredentials(String path) throws IOException {
        if (path.startsWith(CLASSPATH_PREFIX)) {
            return new ClassPathResource(path.substring(CLASSPATH_PREFIX.length())).getInputStream();
        }
        File file = new File(path);
        return file.exists() ? new FileInputStream(file) : new ClassPathResource(path).getInputStream();
    }
}
