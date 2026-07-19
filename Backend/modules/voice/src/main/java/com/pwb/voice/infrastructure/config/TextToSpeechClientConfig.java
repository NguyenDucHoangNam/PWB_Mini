package com.pwb.voice.infrastructure.config;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import com.pwb.voice.infrastructure.config.TtsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Slf4j
@Configuration
public class TextToSpeechClientConfig {

    @Bean(destroyMethod = "close")
    public TextToSpeechClient textToSpeechClient(TtsProperties ttsProperties) throws IOException {
        String credPath = ttsProperties.getCredentialsPath();
        if (credPath == null || credPath.isBlank()) {
            throw new IllegalStateException(
                    "GCP_TTS_CREDENTIALS_PATH is required to bootstrap TextToSpeechClient");
        }

        log.info("Initializing TextToSpeechClient with credentials file");

        try (var stream = new FileInputStream(credPath)) {
            var credentials = ServiceAccountCredentials.fromStream(stream);
            var settings = TextToSpeechSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();
            return TextToSpeechClient.create(settings);
        }
    }
}
