package com.pwb.backend.config;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import com.pwb.backend.config.GcpTtsProperties;
import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class GcpTtsConfig {

    private final GcpTtsProperties gcpTtsProperties;

    @Bean(destroyMethod = "close")
    public TextToSpeechClient textToSpeechClient() {
        try {
            TextToSpeechSettings settings;
            String credentialsPath = gcpTtsProperties.getCredentialsPath();

            if (credentialsPath == null || credentialsPath.isBlank()) {
                log.warn("GCP_TTS_CREDENTIALS_PATH not configured. TTS client will be unavailable until credentials are provided.");
                settings = TextToSpeechSettings.newBuilder()
                        .setCredentialsProvider(NoCredentialsProvider.create())
                        .build();
            } else {
                GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath));
                settings = TextToSpeechSettings.newBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                        .build();
            }

            return TextToSpeechClient.create(settings);
        } catch (IOException ex) {
            log.error("Failed to initialize GCP TTS client", ex);
            throw new BusinessException(
                    ErrorCode.TTS_SERVICE_FAILED,
                    "Failed to initialize TTS client: " + ex.getMessage(),
                    ex);
        }
    }
}
