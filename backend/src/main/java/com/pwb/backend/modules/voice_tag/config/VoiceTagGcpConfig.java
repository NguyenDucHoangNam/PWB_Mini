package com.pwb.backend.modules.voice_tag.config;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.FileInputStream;
import java.io.IOException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class VoiceTagGcpConfig {

    private final VoiceTagProperties properties;

    @Bean(destroyMethod = "close")
    @Lazy
    public TextToSpeechClient textToSpeechClient() throws IOException {
        String credentialsPath = properties.getGcp().getCredentialsPath();

        if (credentialsPath != null && !credentialsPath.isBlank()) {
            log.info("Initializing GCP TTS client with service-account key: {}", credentialsPath);
            GoogleCredentials credentials = GoogleCredentials
                .fromStream(new FileInputStream(credentialsPath))
                .createScoped("https://www.googleapis.com/auth/cloud-platform");

            TextToSpeechSettings settings = TextToSpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build();
            return TextToSpeechClient.create(settings);
        }

        log.info("Initializing GCP TTS client with Application Default Credentials");
        return TextToSpeechClient.create();
    }
}
